package com.health.dto;

public class AIRecommendedQuestionResponse {
    private String question;
    private String category;
    private String reason;

    public AIRecommendedQuestionResponse() {
    }

    public AIRecommendedQuestionResponse(String question, String category, String reason) {
        this.question = question;
        this.category = category;
        this.reason = reason;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
