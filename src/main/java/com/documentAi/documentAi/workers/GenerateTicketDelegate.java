package com.documentAi.documentAi.workers;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.Map;

@Component
public class GenerateTicketDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String ticketId = UUID.randomUUID().toString();
        execution.setVariable("ticketId", ticketId);
        System.out.println("Generated Ticket ID: " + ticketId);
    }
}