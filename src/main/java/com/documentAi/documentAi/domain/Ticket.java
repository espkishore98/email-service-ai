package com.documentAi.documentAi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String ticketId;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }

    public Ticket(Long id, String ticketId, String status) {
        this.id = id;
        this.ticketId = ticketId;
        this.status = status;
    }

    public Ticket(String ticketId, String status) {
        this.ticketId = ticketId;
        this.status = status;
    }
    public Ticket() {
        super();
    }

}
