package com.example.laptoprec.dto;

import java.util.List;

public class RecommendChatRequest {
    private List<RecommendMessageDTO> messages;

    public List<RecommendMessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<RecommendMessageDTO> messages) {
        this.messages = messages;
    }
}
