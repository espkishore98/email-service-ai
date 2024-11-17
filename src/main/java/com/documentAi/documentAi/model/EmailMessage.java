package com.documentAi.documentAi.model;

import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class EmailMessage {
    private String from;
    private String subject;
    private String content;
    private EmailCategory category;
}