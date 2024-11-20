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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            Map<String, String> parsedResponse = parseResponse(response);

            // Use parsed subject or fallback to the original subject
            String subject = "Re: " + (parsedResponse.get("subject").isEmpty() ? emailMessage.getSubject() : parsedResponse.get("subject"));
            String body = parsedResponse.get("body");

            sendResponse(emailMessage.getFrom(), subject, body);
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
                Categorize the email into one of these categories: CLAIM, BILLING, POLICY UPDATE, COMPLAINT, INQUIRY.
                Return only the category name without any explanation.
                """);

        // User message with email content
        Message userMessage = new UserMessage(content);

        try {
            ChatResponse response = chatClient.call(new Prompt(List.of(systemMessage, userMessage)));
            String category = response.getResult().getOutput().getContent().trim().toUpperCase();
            return EmailCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category returned by AI, defaulting to GENERAL");
            return EmailCategory.GENERAL;
        }
    }

    private String generateResponse(EmailMessage email) {

        //can have database connection to create a ticket for each mail with unique id and then pass to prompt

        // System message for response generation
        Message systemMessage = new SystemMessage("""
                You are a professional email assistant specializing in insurance.
                The sender's name is: %s.
                The email concerns: %s.
                The "Subject" should be concise and descriptive of the response.
                The "Body" should include a short and polite and professional response with a ticket generated for request with  greeting and signature".            
                Format your response as:
                    Subject: <Subject Line>
                    Body:
                    <Body Content>
                """.formatted(email.getFrom(), email.getCategory()));

        Message userMessage = new UserMessage(email.getContent());

        ChatResponse response = chatClient.call(new Prompt(List.of(systemMessage, userMessage)));
        return response.getResult().getOutput().getContent();
    }
    private Map<String, String> parseResponse(String response) {
        Map<String, String> responseParts = new HashMap<>();
        String[] parts = response.split("Body:\\s*", 2); // Split at "Body:"

        if (parts.length == 2) {
            String subject = parts[0].replace("Subject:", "").trim(); // Extract and clean subject
            String body = parts[1].trim(); // Extract body
            responseParts.put("subject", subject);
            responseParts.put("body", body);
        } else {
            log.warn("Invalid response format. Defaulting to empty subject and body.");
            responseParts.put("subject", "");
            responseParts.put("body", response); // Treat entire response as body if format invalid
        }
        return responseParts;
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