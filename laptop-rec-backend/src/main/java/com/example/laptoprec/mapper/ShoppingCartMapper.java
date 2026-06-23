package com.example.laptoprec.mapper;

import com.example.laptoprec.vo.LaptopListItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ShoppingCartMapper {
    List<LaptopListItemVO> selectItems();

    int insertIgnore(@Param("laptopId") Long laptopId);

    int deleteByLaptopIds(@Param("laptopIds") List<Long> laptopIds);

    int deleteAll();
}
