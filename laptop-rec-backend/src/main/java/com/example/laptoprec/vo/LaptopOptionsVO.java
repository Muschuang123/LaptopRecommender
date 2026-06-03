package com.example.laptoprec.vo;

import java.math.BigDecimal;
import java.util.List;

public class LaptopOptionsVO {
    private List<String> brands;
    private List<String> productTypes;
    private List<String> usagePositionings;
    private List<String> gpuTypes;
    private List<Integer> memoryCapacitiesGb;
    private List<Integer> storageCapacitiesGb;
    private List<BigDecimal> screenSizesInch;
    private RangeVO priceRange;
    private RangeVO weightRange;

    public List<String> getBrands() {
        return brands;
    }

    public void setBrands(List<String> brands) {
        this.brands = brands;
    }

    public List<String> getProductTypes() {
        return productTypes;
    }

    public void setProductTypes(List<String> productTypes) {
        this.productTypes = productTypes;
    }

    public List<String> getUsagePositionings() {
        return usagePositionings;
    }

    public void setUsagePositionings(List<String> usagePositionings) {
        this.usagePositionings = usagePositionings;
    }

    public List<String> getGpuTypes() {
        return gpuTypes;
    }

    public void setGpuTypes(List<String> gpuTypes) {
        this.gpuTypes = gpuTypes;
    }

    public List<Integer> getMemoryCapacitiesGb() {
        return memoryCapacitiesGb;
    }

    public void setMemoryCapacitiesGb(List<Integer> memoryCapacitiesGb) {
        this.memoryCapacitiesGb = memoryCapacitiesGb;
    }

    public List<Integer> getStorageCapacitiesGb() {
        return storageCapacitiesGb;
    }

    public void setStorageCapacitiesGb(List<Integer> storageCapacitiesGb) {
        this.storageCapacitiesGb = storageCapacitiesGb;
    }

    public List<BigDecimal> getScreenSizesInch() {
        return screenSizesInch;
    }

    public void setScreenSizesInch(List<BigDecimal> screenSizesInch) {
        this.screenSizesInch = screenSizesInch;
    }

    public RangeVO getPriceRange() {
        return priceRange;
    }

    public void setPriceRange(RangeVO priceRange) {
        this.priceRange = priceRange;
    }

    public RangeVO getWeightRange() {
        return weightRange;
    }

    public void setWeightRange(RangeVO weightRange) {
        this.weightRange = weightRange;
    }
}
