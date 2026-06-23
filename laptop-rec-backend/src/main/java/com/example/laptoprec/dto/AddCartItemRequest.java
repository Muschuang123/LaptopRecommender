package com.example.laptoprec.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AddCartItemRequest {
    @NotNull(message = "笔记本 id 不能为空")
    @Positive(message = "笔记本 id 必须是正整数")
    private Long laptopId;

    public Long getLaptopId() {
        return laptopId;
    }

    public void setLaptopId(Long laptopId) {
        this.laptopId = laptopId;
    }
}
