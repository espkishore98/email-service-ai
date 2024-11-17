package com.documentAi.documentAi.model;

import lombok.Data;

@Data
public class ComparisonResponse {
    private String differences;
    private String similarities;
    private double similarityScore;
}
