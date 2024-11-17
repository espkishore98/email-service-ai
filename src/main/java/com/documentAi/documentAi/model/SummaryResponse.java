package com.documentAi.documentAi.model;

import lombok.Data;

@Data
public class SummaryResponse {
    private String summary;
    private int originalLength;
    private int summaryLength;

}
