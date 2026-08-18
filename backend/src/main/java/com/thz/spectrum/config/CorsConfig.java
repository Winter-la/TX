package com.thz.spectrum.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置：
 * - 无 Origin 的请求（普通同源 GET 等）不触发 CORS，直接放行
 * - 同源请求（Origin 的 host 等于请求自身 host，例如管理后台的同源 POST）自动放行，
 *   避免因白名单未包含自身域名而被误杀
 * - 白名单（app.cors.allowed-origins，支持逗号分隔、* 通配、含 * 的域名通配）内的来源放行
 * - 其它来源拒绝（403 Invalid CORS request）
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String origins;

    @Bean
    public CorsFilter corsFilter() {
        List<String> whitelist = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

        CorsConfigurationSource source = request -> {
            String origin = request.getHeader("Origin");
            if (origin == null) {
                return null; // 无 Origin，不触发 CORS
            }
            if (!isSameHost(origin, request) && !matchesWhitelist(origin, whitelist)) {
                return null; // 拒绝：Spring 返回 403 Invalid CORS request
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of(origin));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setExposedHeaders(List.of("Content-Disposition"));
            config.setMaxAge(3600L);
            return config;
        };
        return new CorsFilter(source);
    }

    /** 判断 origin 是否与请求自身同 host（忽略 scheme/端口，规避反代导致 scheme 不一致） */
    private boolean isSameHost(String origin, HttpServletRequest request) {
        try {
            String originHost = java.net.URI.create(origin).getHost();
            return originHost != null && originHost.equalsIgnoreCase(request.getServerName());
        } catch (Exception e) {
            return false;
        }
    }

    /** 白名单匹配：支持精确值、* 全放行、含 * 的域名通配（如 https://*.pages.dev） */
    private boolean matchesWhitelist(String origin, List<String> whitelist) {
        for (String w : whitelist) {
            if (w.equals("*")) return true;
            if (origin.equalsIgnoreCase(w)) return true;
            if (w.contains("*")) {
                String regex = w.replace(".", "\\.").replace("*", ".*");
                if (origin.matches("(?i)" + regex)) return true;
            }
        }
        return false;
    }
}
