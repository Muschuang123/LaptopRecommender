package com.example.laptoprec.vo;

public class RecommendationVO {
    private Long laptopId;
    private String reason;
    private LaptopDetailVO detail;

    public Long getLaptopId() {
        return laptopId;
    }

    public void setLaptopId(Long laptopId) {
        this.laptopId = laptopId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LaptopDetailVO getDetail() {
        return detail;
    }

    public void setDetail(LaptopDetailVO detail) {
        this.detail = detail;
    }
}
