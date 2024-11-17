//package com.documentAi.documentAi.service;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.ollama.api.OllamaApi;
//import org.springframework.context.annotation.Role;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//
//import java.net.http.HttpHeaders;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//public class DocumentProcessingService {
//
//    private final ChatClient chatClient;
//
//    public DocumentProcessingService(ChatClient.Builder chatClientBuilder) {
//        this.chatClient =  chatClientBuilder.build();
//
//    }
//
//    // Compare two documents
//    public void compareDocuments(String doc1, String doc2) {
//        String comparisonPrompt = "Compare the following two documents: \n\n" +
//                "Document 1:\n" + doc1 + "\n\n" +
//                "Document 2:\n" + doc2 + "\n\n" +
//                "Provide a comparison in terms of content similarity and key differences.";
//
//        List<OllamaApi.Message> messages = Collections.singletonList(new OllamaApi.Message(OllamaApi.Message.Role.USER, comparisonPrompt, null, null));
//
//        OllamaApi.ChatRequest request = new OllamaApi.ChatRequest(
//                "llama",
//                messages,
//                false,                     // Do not stream the response
//                "text",                    // Expected response format
//                null,                      // No keep-alive setting
//                null,                      // No specific tools needed
//                Map.of("temperature", 0.5)
//        );
//        var response = chatClient.prompt(request.toString());
//
//    }
//
//}
