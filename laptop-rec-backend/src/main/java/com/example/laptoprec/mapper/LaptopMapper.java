package com.example.laptoprec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.laptoprec.dto.LaptopQueryDTO;
import com.example.laptoprec.entity.Laptop;
import com.example.laptoprec.vo.LaptopDetailVO;
import com.example.laptoprec.vo.LaptopListItemVO;
import com.example.laptoprec.vo.RangeVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LaptopMapper extends BaseMapper<Laptop> {
    List<LaptopListItemVO> selectListItems(@Param("query") LaptopQueryDTO query);

    long countListItems(@Param("query") LaptopQueryDTO query);

    LaptopDetailVO selectDetailById(@Param("id") Long id);

    List<String> selectBrands();

    List<String> selectProductTypes();

    List<String> selectUsagePositionings();

    List<String> selectGpuTypes();

    List<Integer> selectMemoryCapacitiesGb();

    List<Integer> selectStorageCapacitiesGb();

    List<BigDecimal> selectScreenSizesInch();

    RangeVO selectPriceRange();

    RangeVO selectWeightRange();
}
