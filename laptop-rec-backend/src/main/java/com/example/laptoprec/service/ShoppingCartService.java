package com.example.laptoprec.service;

import com.example.laptoprec.vo.LaptopListItemVO;

import java.util.List;

public interface ShoppingCartService {
    List<LaptopListItemVO> getItems();

    void addItem(Long laptopId);

    void deleteItems(List<Long> laptopIds);

    void clear();
}
