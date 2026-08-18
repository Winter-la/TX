package com.thz.spectrum.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 回收站条目 — 删除产品时，把产品及其关联数据（检测记录/光谱文件/流向）以 JSON 快照存入，
 * 文件本身保留在存储中、仅由回收站条目引用，便于日后恢复。
 */
@Entity
@Table(name = "recycle_bin")
public class RecycleBin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType; // 当前仅 "product"

    @Column(name = "original_id")
    private Long originalId;

    @Column(length = 20)
    private String code;

    @Column(length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String snapshot; // JSON 快照

    @Column(name = "deleted_by", length = 50)
    private String deletedBy;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getOriginalId() { return originalId; }
    public void setOriginalId(Long originalId) { this.originalId = originalId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
