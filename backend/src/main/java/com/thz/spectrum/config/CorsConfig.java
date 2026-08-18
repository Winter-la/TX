package com.thz.spectrum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 跨域配置：前后端分离部署时，允许 Cloudflare Pages 前端域名跨域访问
 * app.cors.allowed-origins 逗号分隔；默认 * （本地/单 jar 模式无影响）
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] list = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (list.length == 0) list = new String[]{"*"};
        registry.addMapping("/**")
                .allowedOriginPatterns(list)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }
}
