package com.thz.spectrum.config;

import com.thz.spectrum.security.JwtAuthInterceptor;
import com.thz.spectrum.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired private JwtAuthInterceptor jwtAuthInterceptor;
    @Autowired private UploadPaths uploadPaths;
    @Autowired private StorageService storageService;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径跳转到公众端首页
        registry.addRedirectViewController("/", "/frontend/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 仅本地磁盘存储时映射 /uploads/**；对象存储(S3/R2)模式文件走公网 URL，无需本地映射
        if (storageService.isLocal()) {
            String uri = uploadPaths.getBase().toUri().toString();
            if (!uri.endsWith("/")) uri += "/";
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(uri);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 鉴权：拦截需要权限的接口
        // 排除：登录接口 /admin/login、首页重定向 /admin、静态页面 /admin/index.html
        // 注意：/api/trace/query 为公开追溯查询接口（返回字段已脱敏），不在此拦截
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/admin/**", "/api/content/**", "/api/admin/trace/**", "/api/spectrum/**")
                .excludePathPatterns(
                    "/admin", "/admin/login", "/admin/index.html", "/admin/region-data.js",
                    "/api/content/policy-links",
                    "/api/content/announcements",
                    "/api/content/announcement/*",
                    "/api/content/news"
                );
    }
}
