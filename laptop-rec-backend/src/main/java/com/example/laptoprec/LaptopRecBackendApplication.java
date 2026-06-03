package com.example.laptoprec;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.example.laptoprec.mapper")
@SpringBootApplication
public class LaptopRecBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaptopRecBackendApplication.class, args);
    }
}

