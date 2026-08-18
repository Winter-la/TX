package com.thz.spectrum.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储抽象：本地磁盘 或 S3 兼容对象存储（Cloudflare R2）
 * 由 app.storage.type=local|s3 决定注入哪个实现
 */
public interface StorageService {

    /** 是否本地磁盘存储（本地模式需要 /uploads/** 静态映射） */
    boolean isLocal();

    /**
     * 保存文件。
     * @param category 分类目录（如 spectrum / spectrum_files / announcements / feedback_images）
     * @param filename 原始文件名（会被清洗，仅取 basename）
     * @return 可公开访问的引用：本地为相对路径 /uploads/...，S3 为绝对 URL
     */
    String store(String category, String filename, InputStream in, long size) throws IOException;

    /** 读取已保存文件的内容（预览/下载/打包用） */
    InputStream open(String storedRef) throws IOException;
}
