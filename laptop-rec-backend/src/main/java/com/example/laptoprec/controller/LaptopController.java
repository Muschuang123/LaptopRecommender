package com.example.laptoprec.controller;

import com.example.laptoprec.common.PageResult;
import com.example.laptoprec.common.Result;
import com.example.laptoprec.dto.LaptopQueryDTO;
import com.example.laptoprec.service.LaptopService;
import com.example.laptoprec.vo.LaptopDetailVO;
import com.example.laptoprec.vo.LaptopListItemVO;
import com.example.laptoprec.vo.LaptopOptionsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/laptops")
public class LaptopController {
    private final LaptopService laptopService;

    public LaptopController(LaptopService laptopService) {
        this.laptopService = laptopService;
    }

    @GetMapping
    public Result<PageResult<LaptopListItemVO>> list(LaptopQueryDTO query) {
        return Result.ok(laptopService.queryLaptops(query));
    }

    @GetMapping("/options")
    public Result<LaptopOptionsVO> options() {
        return Result.ok(laptopService.getLaptopOptions());
    }

    @GetMapping("/{id}")
    public Result<LaptopDetailVO> detail(@PathVariable Long id) {
        return Result.ok(laptopService.getLaptopDetail(id));
    }
}
