package com.example.laptoprec.service.impl;

import com.example.laptoprec.mapper.LaptopMapper;
import com.example.laptoprec.mapper.ShoppingCartMapper;
import com.example.laptoprec.service.ShoppingCartService;
import com.example.laptoprec.vo.LaptopListItemVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    private final ShoppingCartMapper shoppingCartMapper;
    private final LaptopMapper laptopMapper;

    public ShoppingCartServiceImpl(ShoppingCartMapper shoppingCartMapper, LaptopMapper laptopMapper) {
        this.shoppingCartMapper = shoppingCartMapper;
        this.laptopMapper = laptopMapper;
    }

    @Override
    public List<LaptopListItemVO> getItems() {
        return shoppingCartMapper.selectItems();
    }

    @Override
    public void addItem(Long laptopId) {
        if (laptopId == null || laptopId <= 0) {
            throw new IllegalArgumentException("笔记本 id 必须是正整数");
        }
        if (laptopMapper.selectDetailById(laptopId) == null) {
            throw new IllegalArgumentException("笔记本不存在，id=" + laptopId);
        }
        shoppingCartMapper.insertIgnore(laptopId);
    }

    @Override
    public void deleteItems(List<Long> laptopIds) {
        List<Long> normalizedIds = laptopIds == null ? List.of() : laptopIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一台笔记本");
        }
        shoppingCartMapper.deleteByLaptopIds(normalizedIds);
    }

    @Override
    public void clear() {
        shoppingCartMapper.deleteAll();
    }
}
