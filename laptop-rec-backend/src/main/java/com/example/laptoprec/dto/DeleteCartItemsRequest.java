package com.example.laptoprec.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class DeleteCartItemsRequest {
    @NotEmpty(message = "至少选择一台笔记本")
    private List<@NotNull(message = "笔记本 id 不能为空") @Positive(message = "笔记本 id 必须是正整数") Long> laptopIds;

    public List<Long> getLaptopIds() {
        return laptopIds;
    }

    public void setLaptopIds(List<Long> laptopIds) {
        this.laptopIds = laptopIds;
    }
}
