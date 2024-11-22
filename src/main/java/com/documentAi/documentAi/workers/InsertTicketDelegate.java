package com.documentAi.documentAi.workers;

import com.documentAi.documentAi.model.Ticket;
import com.documentAi.documentAi.repository.TicketRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class InsertTicketDelegate implements JavaDelegate {
    @Autowired
    TicketRepository ticketRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String ticketId = (String) execution.getVariable("ticketId");
        Ticket ticket = new Ticket();
        ticket.setTicketId(ticketId);
        ticket.setStatus("In Progress");
        ticketRepository.save(ticket);
        System.out.println("Inserted Ticket into DB with ID: " + ticketId);
    }
}
