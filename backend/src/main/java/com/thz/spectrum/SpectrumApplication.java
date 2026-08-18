package com.thz.spectrum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpectrumApplication {

    private static final Logger log = LoggerFactory.getLogger(SpectrumApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SpectrumApplication.class, args);
        log.info("智能指纹图谱平台 后端服务已启动");
        log.info("API: http://localhost:8080/api/query?code=001");
        log.info("管理: http://localhost:8080/admin");
    }
}
