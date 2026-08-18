package com.thz.spectrum.storage;

import com.thz.spectrum.config.UploadPaths;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * 本地磁盘存储（默认）：文件写入 uploads/ 目录，由 /uploads/** 静态映射对外提供
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final UploadPaths uploadPaths;

    public LocalStorageService(UploadPaths uploadPaths) {
        this.uploadPaths = uploadPaths;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public String store(String category, String filename, InputStream in, long size) throws IOException {
        Path dir = uploadPaths.resolve(category);
        Files.createDirectories(dir);
        String safeName = System.currentTimeMillis() + "_" + sanitize(filename);
        Files.copy(in, dir.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + category + "/" + safeName;
    }

    @Override
    public InputStream open(String storedRef) throws IOException {
        String rel = (storedRef != null && storedRef.startsWith("/uploads/"))
                ? storedRef.substring("/uploads/".length()) : (storedRef == null ? "" : storedRef);
        Path base = uploadPaths.getBase();
        Path p = base.resolve(rel).normalize();
        if (!p.startsWith(base)) throw new IOException("非法文件路径");
        return Files.newInputStream(p);
    }

    @Override
    public void delete(String storedRef) throws IOException {
        String rel = (storedRef != null && storedRef.startsWith("/uploads/"))
                ? storedRef.substring("/uploads/".length()) : (storedRef == null ? "" : storedRef);
        Path base = uploadPaths.getBase();
        Path p = base.resolve(rel).normalize();
        if (!p.startsWith(base)) throw new IOException("非法文件路径");
        Files.deleteIfExists(p);
    }

    /** 仅保留文件名（去掉路径），并替换非法字符 */
    private String sanitize(String name) {
        if (name == null) return "file";
        String b = name.replace('\\', '/');
        int idx = b.lastIndexOf('/');
        if (idx >= 0) b = b.substring(idx + 1);
        b = b.replaceAll("[^A-Za-z0-9._-]", "_");
        if (b.isBlank()) b = "file";
        return b.length() > 100 ? b.substring(b.length() - 100) : b;
    }
}
