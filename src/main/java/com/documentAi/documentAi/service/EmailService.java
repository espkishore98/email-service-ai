package com.documentAi.documentAi.service;

import com.documentAi.documentAi.model.EmailCategory;
import com.documentAi.documentAi.model.EmailMessage;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
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
    private final RuntimeService runtimeService; // Camunda RuntimeService
    private final HistoryService historyService;
    @Value("${spring.mail.username}")
    private String emailUsername;
    @Value("${spring.mail.password}")
    private String password;

    private VectorStore vectorStore;
    public EmailService(JavaMailSender emailSender, ChatClient.Builder chatClient, RuntimeService runtimeService,
                        HistoryService historyService,
                        VectorStore vectorStore
                       ) {
        this.emailSender = emailSender;
        this.chatClient = chatClient.build();
        this.mailProperties = new Properties();
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.vectorStore = vectorStore;
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
                Categorize the email into one of these categories: CLAIM, BILLING, POLICY_UPDATE, COMPLAINT, ENQUIRY.
                Return only the category name without any explanation.
                """);

        // User message with email content
        Message userMessage = new UserMessage(content);

        try {
            ChatResponse response = chatClient.prompt(new Prompt(List.of(systemMessage, userMessage)))
                    .advisors()
                    .call().chatResponse();
            String category = response.getResult().getOutput().getContent().trim().toUpperCase();
            return EmailCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category returned by AI, defaulting to GENERAL");
            return EmailCategory.GENERAL;
        }
    }

    private String generateResponse(EmailMessage email) {


        Map<String, Object> variables = new HashMap<>();
        variables.put("emailContent", email.getContent());
        variables.put("emailFrom", email.getFrom());
        variables.put("emailCategory", email.getCategory().name());

        // Start the Camunda process with the variables passed in
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("ticketProcess", variables);
        System.out.println("processInstance " + processInstance.getId());
        // Fetch ticketId and other variables after the process completes
        String ticketId;
        if (runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult() != null) {
            ticketId = (String) runtimeService.getVariable(processInstance.getId(), "ticketId");
        } else {
            // Fetch from history if the process has completed
            ticketId = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .variableName("ticketId")
                    .singleResult()
                    .getValue()
                    .toString();
        }
        // Handle the generated response, e.g., return the ticketId and response
        log.info("Generated ticket ID: {}", ticketId);
        // System message for response generation
        Message systemMessage = new SystemMessage("""
                You are a professional email assistant working on behalf of an insurance company, responding to customer emails.
                                
                The sender's name is: %s. \s
                The email concerns: %s. \s
                                
                The "Subject" should be concise and descriptive of the response, starting with a placeholder for the ticketId. The structure should be `{ticketId} - [Subject of the response]` ticketId is not policyNumber. 
                                
                The "Body" should include: \s
                1. A formal greeting addressing the sender. \s
                2. A concise response in **two paragraphs**. Each paragraph should be clearly separated by line breaks. Include a list if necessary, using proper list formatting (e.g., `-` for bullet points).
                3. A formal closing signature, followed by an automated message disclaimer.
                                
                Please generate the response in **HTML format** for better readability in emails, using `<p>` for paragraphs and `<br>` for line breaks. Format your response as follows:
                Subject: {ticketId} - [Subject Line]
                Body:
                <p>Dear [Sender's Name],</p> 
                <p>[Paragraph 1 content with line breaks and lists, if any].</p>
                 <p>[Paragraph 2 content mentioning {ticketId}].</p> 
                 <p>Sincerely,<br>DataNinjas Insurance corp</p>
                <p><i>This is an automated message; please do not reply directly to this email.</i></p>            
                """.formatted(email.getFrom(), email.getCategory()));

        Message userMessage = new UserMessage(email.getContent());

        ChatResponse response = chatClient.prompt(new Prompt(List.of(systemMessage, userMessage)))
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults().withTopK(5))).call().chatResponse();
        String updatedResponse = response.getResult().getOutput().getContent().replace("{ticketId}", ticketId);

        return updatedResponse;
    }

    private Map<String, String> parseResponse(String response) {
        Map<String, String> responseParts = new HashMap<>();

        // Split the response at "Body:" to separate the subject and body content
        String[] parts = response.split("Body:\\s*", 2);

        if (parts.length == 2) {
            // Extract and clean subject
            String subject = parts[0].replace("Subject:", "").trim();

            // Extract body content
            String body = parts[1].trim();

            // Normalize line breaks (important for consistent rendering)
            body = body.replaceAll("\\r\\n|\\r|\\n", System.lineSeparator()); // Normalize all to single line separator

            // Optionally, convert double line breaks into paragraphs
            body = body.replaceAll(System.lineSeparator() + "{2,}", "<p>") // Convert double line breaks into paragraphs
                    .replaceAll(System.lineSeparator(), "<br>");        // Convert single line breaks to HTML <br>

            responseParts.put("subject", subject);
            responseParts.put("body", body); // Store formatted body
        } else {
            log.warn("Invalid response format. Defaulting to empty subject and body.");
            responseParts.put("subject", "");
            responseParts.put("body", response); // Treat entire response as body if format invalid
        }

        return responseParts;
    }



    private void sendResponse(String to, String subject, String responseText) throws jakarta.mail.MessagingException {
        try {
            MimeMessage mimeMessage = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(emailUsername);
            helper.setTo(to);
            helper.setSubject(subject);
            String htmlContent = responseText
                    .replace("\n", "<br>") // Preserve line breaks
                    .replace("\n\n", "<p>") // Convert double line breaks into paragraphs
                    .replace("Sincerely,", "<p>Sincerely,</p>"); // Ensure proper paragraph separation

            helper.setText(htmlContent, true);
            helper.setText(responseText, true);
            log.info("responseText is", responseText);
            emailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Error sending response", e);
        }
    }
}