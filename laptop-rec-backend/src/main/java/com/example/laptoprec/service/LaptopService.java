package com.example.laptoprec.service;

import com.example.laptoprec.common.PageResult;
import com.example.laptoprec.dto.LaptopQueryDTO;
import com.example.laptoprec.vo.LaptopDetailVO;
import com.example.laptoprec.vo.LaptopListItemVO;
import com.example.laptoprec.vo.LaptopOptionsVO;

public interface LaptopService {
    PageResult<LaptopListItemVO> queryLaptops(LaptopQueryDTO query);

    LaptopOptionsVO getLaptopOptions();

    LaptopDetailVO getLaptopDetail(Long id);
}
