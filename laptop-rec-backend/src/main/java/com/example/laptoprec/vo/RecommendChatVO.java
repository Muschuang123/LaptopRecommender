package com.example.laptoprec.vo;

import java.util.List;

public class RecommendChatVO {
    private String reply;
    private List<RecommendationVO> recommendations;
    private List<String> followUpQuestions;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<RecommendationVO> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<RecommendationVO> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getFollowUpQuestions() {
        return followUpQuestions;
    }

    public void setFollowUpQuestions(List<String> followUpQuestions) {
        this.followUpQuestions = followUpQuestions;
    }
}
