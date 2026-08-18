package com.thz.spectrum.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 意见反馈表 — 公众通过"提出意见"页面提交
 */
@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type; // 功能建议 / 体验问题 / 数据问题 / 其他

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(length = 100)
    private String contact;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    // ====== getters / setters ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
