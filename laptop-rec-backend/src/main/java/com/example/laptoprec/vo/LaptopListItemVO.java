package com.example.laptoprec.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LaptopListItemVO {
    private Long id;
    private String brandName;
    private String model;
    private String productType;
    private String usagePositioning;
    private BigDecimal weightKg;
    private String imageUrl;
    private String sourceUrl;
    private LocalDate releaseDate;
    private BigDecimal latestPrice;
    private String cpuModel;
    private String gpuModel;
    private String gpuType;
    private Integer memoryCapacityGb;
    private Integer storageCapacityGb;
    private BigDecimal screenSizeInch;
    private String screenResolution;
    private Integer refreshRateHz;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getUsagePositioning() {
        return usagePositioning;
    }

    public void setUsagePositioning(String usagePositioning) {
        this.usagePositioning = usagePositioning;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
        this.latestPrice = latestPrice;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public String getGpuModel() {
        return gpuModel;
    }

    public void setGpuModel(String gpuModel) {
        this.gpuModel = gpuModel;
    }

    public String getGpuType() {
        return gpuType;
    }

    public void setGpuType(String gpuType) {
        this.gpuType = gpuType;
    }

    public Integer getMemoryCapacityGb() {
        return memoryCapacityGb;
    }

    public void setMemoryCapacityGb(Integer memoryCapacityGb) {
        this.memoryCapacityGb = memoryCapacityGb;
    }

    public Integer getStorageCapacityGb() {
        return storageCapacityGb;
    }

    public void setStorageCapacityGb(Integer storageCapacityGb) {
        this.storageCapacityGb = storageCapacityGb;
    }

    public BigDecimal getScreenSizeInch() {
        return screenSizeInch;
    }

    public void setScreenSizeInch(BigDecimal screenSizeInch) {
        this.screenSizeInch = screenSizeInch;
    }

    public String getScreenResolution() {
        return screenResolution;
    }

    public void setScreenResolution(String screenResolution) {
        this.screenResolution = screenResolution;
    }

    public Integer getRefreshRateHz() {
        return refreshRateHz;
    }

    public void setRefreshRateHz(Integer refreshRateHz) {
        this.refreshRateHz = refreshRateHz;
    }
}

