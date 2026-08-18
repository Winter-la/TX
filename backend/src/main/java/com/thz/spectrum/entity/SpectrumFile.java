package com.thz.spectrum.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 光谱源文件表 — 按产品+类型（时域/频域）关联 .txt 文件
 * 产品 id 由 products 表提供，名称/产地等信息通过 JOIN 获取
 */
@Entity
@Table(name = "spectrum_files")
public class SpectrumFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "data_type", nullable = false, length = 10)
    private String dataType; // 时域 / 频域

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "original_name", length = 300)
    private String originalName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ====== getters / setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
