package com.thz.spectrum.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String alias;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 200)
    private String origin;

    @Column(length = 200)
    private String latin;

    @Column(length = 100)
    private String part;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String conclusion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ====== getters / setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getLatin() { return latin; }
    public void setLatin(String latin) { this.latin = latin; }

    public String getPart() { return part; }
    public void setPart(String part) { this.part = part; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
