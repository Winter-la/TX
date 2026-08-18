package com.thz.spectrum.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一上传目录解析。
 * 相对路径以应用所在目录为基准（开发时为 backend/ 模块根，打包后为 jar 所在目录），
 * 避免因启动时工作目录不同导致上传文件散落到多个位置。
 */
@Component
public class UploadPaths {

    private static final Logger log = LoggerFactory.getLogger(UploadPaths.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path base;

    @PostConstruct
    void init() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) {
            Path home = new ApplicationHome(getClass()).getDir().toPath();
            // 开发环境类文件位于 target/classes，上溯两级得到模块根目录
            if (home.endsWith(Paths.get("target", "classes"))) {
                home = home.getParent().getParent();
            }
            p = home.resolve(uploadDir);
        }
        base = p.toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(base);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建上传根目录: " + base, e);
        }
        log.info("上传根目录: {}", base);
    }

    /** 上传根目录的绝对路径 */
    public Path getBase() {
        return base;
    }

    /** 解析上传子目录（如 spectrum / spectrum_files / announcements）的绝对路径 */
    public Path resolve(String sub) {
        return base.resolve(sub).normalize();
    }

    /** 由磁盘上的相对子路径构造对外访问 URL */
    public String url(String subPath) {
        return "/uploads/" + subPath.replace('\\', '/');
    }
}
