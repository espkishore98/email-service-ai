package com.documentAi.documentAi.service;

import com.documentAi.documentAi.model.EmailCategory;
import com.documentAi.documentAi.model.EmailMessage;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;

@Service
@Slf4j
class EmailService {
    private final JavaMailSender emailSender;
    private final ChatClient chatClient;
    private final Properties mailProperties;

    @Value("${spring.mail.username}")
    private String emailUsername;
    @Value("${spring.mail.password}")
    private String password;


    public EmailService(JavaMailSender emailSender, ChatClient chatClient) {
        this.emailSender = emailSender;
        this.chatClient = chatClient;
        this.mailProperties = new Properties();
    }

    @Scheduled(cron = "0 */2 * ? * *")
    public void processEmails() {
        try {
            Store store = connectToEmail();
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            Flags seen = new Flags(Flags.Flag.SEEN);
            FlagTerm unseenFlagTerm = new FlagTerm(seen, false);
            jakarta.mail.Message[] messages = inbox.search(unseenFlagTerm);

            for (jakarta.mail.Message message : messages) {
                processMessage(message);
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            log.error("Error processing emails", e);
        }
    }

    private Store connectToEmail() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", emailUsername, password);
        return store;
    }

    private void processMessage(jakarta.mail.Message message) {
        try {
            EmailMessage emailMessage = extractEmailContent(message);
            EmailCategory category = categorizeEmail(emailMessage.getContent());
            emailMessage.setCategory(category);

            String response = generateResponse(emailMessage);
            sendResponse(emailMessage.getFrom(), emailMessage.getSubject(), response);

            message.setFlag(Flags.Flag.SEEN, true);

            log.info("Processed email: {} - Category: {}", emailMessage.getSubject(), category);
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    private EmailMessage extractEmailContent(jakarta.mail.Message message) throws Exception {
        String content = "";
        if (message.getContent() instanceof String) {
            content = (String) message.getContent();
        } else if (message.getContent() instanceof Multipart) {
            content = extractMultipartContent((Multipart) message.getContent());
        }

        return EmailMessage.builder()
                .from(InternetAddress.toString(message.getFrom()))
                .subject(message.getSubject())
                .content(content)
                .build();
    }

    private String extractMultipartContent(Multipart multipart) throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (bodyPart.getContent() instanceof String) {
                content.append(bodyPart.getContent());
            }
        }
        return content.toString();
    }

    private EmailCategory categorizeEmail(String content) {
        // System message for categorization
        Message systemMessage = new SystemMessage("""
            You are an email categorization assistant.
            Categorize the email into one of these categories: SUPPORT, SALES, COMPLAINT, GENERAL, URGENT.
            Return only the category name without any explanation.
            """);

        // User message with email content
        Message userMessage = new UserMessage(content);

        try {
            ChatResponse response = chatClient.call(new Prompt(List.of(systemMessage, userMessage))) ;
            String category = response.getResult().getOutput().getContent().trim().toUpperCase();
            return EmailCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category returned by AI, defaulting to GENERAL");
            return EmailCategory.GENERAL;
        }
    }

    private String generateResponse(EmailMessage email) {
        // System message for response generation
        Message systemMessage = new SystemMessage("""
            You are a professional email response assistant.
            Generate responses that are:
            - Polite and professional
            - Address specific concerns
            - Concise but thorough
            - Include greeting and signature
            - Signed as "AI Assistant"
            Current email category: %s
            """.formatted(email.getCategory()));

        Message userMessage = new UserMessage(email.getContent());

        ChatResponse response = chatClient.call(new Prompt(List.of(systemMessage, userMessage)));
        return response.getResult().getOutput().getContent();
    }

    private void sendResponse(String to, String subject, String responseText) throws jakarta.mail.MessagingException {
//        SimpleMailMessage message = new SimpleMailMessage();
//        message.setFrom(emailUsername);
//        message.setTo(to);
//        message.setSubject("Re: " + subject);
//        message.setText(responseText);
//        log.info("response text", responseText);
        try {
            MimeMessage mimeMessage = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(emailUsername);
            helper.setTo(to);
            helper.setSubject("Re: " + subject);
            helper.setText(responseText, true); // `true` enables HTML
            log.info("responseText is", responseText);
            emailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error sending response", e);
        }
    }
}