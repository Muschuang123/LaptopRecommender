package com.example.laptoprec.service.impl;

import com.example.laptoprec.common.PageResult;
import com.example.laptoprec.dto.LaptopQueryDTO;
import com.example.laptoprec.mapper.LaptopMapper;
import com.example.laptoprec.service.LaptopService;
import com.example.laptoprec.vo.LaptopDetailVO;
import com.example.laptoprec.vo.LaptopListItemVO;
import com.example.laptoprec.vo.LaptopOptionsVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LaptopServiceImpl implements LaptopService {
    private final LaptopMapper laptopMapper;

    public LaptopServiceImpl(LaptopMapper laptopMapper) {
        this.laptopMapper = laptopMapper;
    }

    @Override
    public PageResult<LaptopListItemVO> queryLaptops(LaptopQueryDTO query) {
        query.normalize();
        long total = laptopMapper.countListItems(query);
        List<LaptopListItemVO> records = total == 0 ? List.of() : laptopMapper.selectListItems(query);
        return new PageResult<>(total, query.getPage(), query.getSize(), records);
    }

    @Override
    public LaptopOptionsVO getLaptopOptions() {
        LaptopOptionsVO options = new LaptopOptionsVO();
        options.setBrands(laptopMapper.selectBrands());
        options.setProductTypes(laptopMapper.selectProductTypes());
        options.setUsagePositionings(laptopMapper.selectUsagePositionings());
        options.setGpuTypes(laptopMapper.selectGpuTypes());
        options.setMemoryCapacitiesGb(laptopMapper.selectMemoryCapacitiesGb());
        options.setStorageCapacitiesGb(laptopMapper.selectStorageCapacitiesGb());
        options.setScreenSizesInch(laptopMapper.selectScreenSizesInch());
        options.setPriceRange(laptopMapper.selectPriceRange());
        options.setWeightRange(laptopMapper.selectWeightRange());
        return options;
    }

    @Override
    public LaptopDetailVO getLaptopDetail(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("笔记本 id 必须是正整数");
        }
        LaptopDetailVO detail = laptopMapper.selectDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("笔记本不存在，id=" + id);
        }
        return detail;
    }
}
