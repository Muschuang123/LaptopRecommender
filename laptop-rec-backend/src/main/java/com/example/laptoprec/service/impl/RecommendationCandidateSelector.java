package com.example.laptoprec.service.impl;

import com.example.laptoprec.vo.LaptopListItemVO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RecommendationCandidateSelector {
    private static final int TARGET_DISTINCT_BRANDS = 3;
    private static final int INITIAL_MAX_PER_BRAND = 2;

    private RecommendationCandidateSelector() {
    }

    static List<LaptopListItemVO> select(
            List<LaptopListItemVO> candidates,
            int limit,
            boolean diversifyByBrand
    ) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<LaptopListItemVO> uniqueCandidates = uniqueById(candidates);
        int targetSize = Math.min(limit, uniqueCandidates.size());
        if (!diversifyByBrand || targetSize == uniqueCandidates.size()) {
            return new ArrayList<>(uniqueCandidates.subList(0, targetSize));
        }

        Map<String, List<LaptopListItemVO>> candidatesByBrand = groupByBrand(uniqueCandidates);
        if (candidatesByBrand.size() < 2) {
            return new ArrayList<>(uniqueCandidates.subList(0, targetSize));
        }

        List<LaptopListItemVO> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        Map<String, Integer> selectedCountByBrand = new LinkedHashMap<>();
        int guaranteedBrandCount = Math.min(Math.min(TARGET_DISTINCT_BRANDS, candidatesByBrand.size()), targetSize);

        int coveredBrands = 0;
        for (Map.Entry<String, List<LaptopListItemVO>> entry : candidatesByBrand.entrySet()) {
            if (coveredBrands >= guaranteedBrandCount) {
                break;
            }
            addCandidate(selected, selectedIds, selectedCountByBrand, entry.getKey(), entry.getValue().get(0));
            coveredBrands++;
        }

        for (LaptopListItemVO candidate : uniqueCandidates) {
            if (selected.size() >= targetSize) {
                break;
            }
            String brand = brandKey(candidate);
            if (selectedCountByBrand.getOrDefault(brand, 0) >= INITIAL_MAX_PER_BRAND) {
                continue;
            }
            addCandidate(selected, selectedIds, selectedCountByBrand, brand, candidate);
        }

        for (LaptopListItemVO candidate : uniqueCandidates) {
            if (selected.size() >= targetSize) {
                break;
            }
            addCandidate(selected, selectedIds, selectedCountByBrand, brandKey(candidate), candidate);
        }
        return selected;
    }

    static int countDistinctBrands(List<LaptopListItemVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        Set<String> brands = new LinkedHashSet<>();
        for (LaptopListItemVO candidate : candidates) {
            if (candidate != null) {
                brands.add(brandKey(candidate));
            }
        }
        return brands.size();
    }

    private static List<LaptopListItemVO> uniqueById(List<LaptopListItemVO> candidates) {
        List<LaptopListItemVO> unique = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (LaptopListItemVO candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Long id = candidate.getId();
            if (id != null && !seenIds.add(id)) {
                continue;
            }
            unique.add(candidate);
        }
        return unique;
    }

    private static Map<String, List<LaptopListItemVO>> groupByBrand(List<LaptopListItemVO> candidates) {
        Map<String, List<LaptopListItemVO>> candidatesByBrand = new LinkedHashMap<>();
        for (LaptopListItemVO candidate : candidates) {
            candidatesByBrand.computeIfAbsent(brandKey(candidate), ignored -> new ArrayList<>()).add(candidate);
        }
        return candidatesByBrand;
    }

    private static void addCandidate(
            List<LaptopListItemVO> selected,
            Set<Long> selectedIds,
            Map<String, Integer> selectedCountByBrand,
            String brand,
            LaptopListItemVO candidate
    ) {
        if (candidate == null) {
            return;
        }
        Long id = candidate.getId();
        if (id != null && !selectedIds.add(id)) {
            return;
        }
        selected.add(candidate);
        selectedCountByBrand.merge(brand, 1, Integer::sum);
    }

    private static String brandKey(LaptopListItemVO candidate) {
        if (candidate == null || candidate.getBrandName() == null || candidate.getBrandName().trim().isEmpty()) {
            return "未标注品牌";
        }
        return candidate.getBrandName().trim();
    }
}
