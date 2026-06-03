package com.example.laptoprec.dto;

import java.math.BigDecimal;

public class LaptopQueryDTO {
    private static final String SORT_LATEST = "latest";
    private static final String SORT_PRICE_ASC = "priceAsc";
    private static final String SORT_PRICE_DESC = "priceDesc";
    private static final String SORT_WEIGHT_ASC = "weightAsc";
    private static final String SORT_SCREEN_DESC = "screenDesc";

    private String keyword;
    private String brand;
    private String cpuKeyword;
    private String gpuKeyword;
    private String productType;
    private String usageKeyword;
    private String gpuType;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minMemoryGb;
    private Integer minStorageGb;
    private BigDecimal minScreenSize;
    private BigDecimal maxWeightKg;
    private String sort = SORT_LATEST;
    private Integer page = 1;
    private Integer size = 20;
    private Integer offset = 0;

    public void normalize() {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        offset = (page - 1) * size;
        keyword = normalizeText(keyword);
        brand = normalizeText(brand);
        cpuKeyword = normalizeText(cpuKeyword);
        gpuKeyword = normalizeText(gpuKeyword);
        productType = normalizeText(productType);
        usageKeyword = normalizeText(usageKeyword);
        gpuType = normalizeText(gpuType);
        sort = normalizeSort(sort);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeSort(String value) {
        String text = normalizeText(value);
        if (text == null) {
            return SORT_LATEST;
        }
        if (SORT_LATEST.equals(text)
                || SORT_PRICE_ASC.equals(text)
                || SORT_PRICE_DESC.equals(text)
                || SORT_WEIGHT_ASC.equals(text)
                || SORT_SCREEN_DESC.equals(text)) {
            return text;
        }
        throw new IllegalArgumentException("不支持的排序方式：" + text);
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCpuKeyword() {
        return cpuKeyword;
    }

    public void setCpuKeyword(String cpuKeyword) {
        this.cpuKeyword = cpuKeyword;
    }

    public String getGpuKeyword() {
        return gpuKeyword;
    }

    public void setGpuKeyword(String gpuKeyword) {
        this.gpuKeyword = gpuKeyword;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getUsageKeyword() {
        return usageKeyword;
    }

    public void setUsageKeyword(String usageKeyword) {
        this.usageKeyword = usageKeyword;
    }

    public String getGpuType() {
        return gpuType;
    }

    public void setGpuType(String gpuType) {
        this.gpuType = gpuType;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Integer getMinMemoryGb() {
        return minMemoryGb;
    }

    public void setMinMemoryGb(Integer minMemoryGb) {
        this.minMemoryGb = minMemoryGb;
    }

    public Integer getMinStorageGb() {
        return minStorageGb;
    }

    public void setMinStorageGb(Integer minStorageGb) {
        this.minStorageGb = minStorageGb;
    }

    public BigDecimal getMinScreenSize() {
        return minScreenSize;
    }

    public void setMinScreenSize(BigDecimal minScreenSize) {
        this.minScreenSize = minScreenSize;
    }

    public BigDecimal getMaxWeightKg() {
        return maxWeightKg;
    }

    public void setMaxWeightKg(BigDecimal maxWeightKg) {
        this.maxWeightKg = maxWeightKg;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }
}
