package com.yzh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.yzh.mapper")
@EnableAsync
public class LlmMarketingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmMarketingPlatformApplication.class, args);
    }
}