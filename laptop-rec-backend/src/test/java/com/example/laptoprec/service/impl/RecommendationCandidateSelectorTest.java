package com.example.laptoprec.service.impl;

import com.example.laptoprec.vo.LaptopListItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationCandidateSelectorTest {

    @Test
    void selectsMultipleBrandsBeforeBackfillingTheLeadingBrand() {
        List<LaptopListItemVO> selected = RecommendationCandidateSelector.select(List.of(
                laptop(1L, "联想"),
                laptop(2L, "联想"),
                laptop(3L, "联想"),
                laptop(4L, "联想"),
                laptop(5L, "惠普"),
                laptop(6L, "惠普"),
                laptop(7L, "华为"),
                laptop(8L, "华为")
        ), 6, true);

        assertEquals(List.of(1L, 5L, 7L, 2L, 6L, 8L), selected.stream().map(LaptopListItemVO::getId).toList());
    }

    @Test
    void keepsDatabaseOrderWhenBrandDiversityIsDisabled() {
        List<LaptopListItemVO> selected = RecommendationCandidateSelector.select(List.of(
                laptop(1L, "联想"),
                laptop(2L, "联想"),
                laptop(3L, "惠普")
        ), 2, false);

        assertEquals(List.of(1L, 2L), selected.stream().map(LaptopListItemVO::getId).toList());
    }

    private LaptopListItemVO laptop(Long id, String brand) {
        LaptopListItemVO laptop = new LaptopListItemVO();
        laptop.setId(id);
        laptop.setBrandName(brand);
        return laptop;
    }
}
