package com.health.dto;

public class AIChatSessionResponse {
    private String chatId;
    private String title;
    private String preview;
    private String updatedAt;
    private Integer messageCount;

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
}
