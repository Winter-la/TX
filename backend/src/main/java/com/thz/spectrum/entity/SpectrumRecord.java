package com.thz.spectrum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "spectrum_records")
public class SpectrumRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 200)
    private String tech;

    @Column(name = "test_date")
    private LocalDate testDate;

    @Column(name = "match_rate", precision = 5, scale = 2)
    private BigDecimal matchRate;

    @Column(length = 20)
    private String grade;

    @Column(name = "image_path_time", length = 500)
    private String imagePathTime;

    @Column(name = "image_path_freq", length = 500)
    private String imagePathFreq;

    @Column(name = "image_desc_time", length = 500)
    private String imageDescTime;

    @Column(name = "image_desc_freq", length = 500)
    private String imageDescFreq;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ====== getters / setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getTech() { return tech; }
    public void setTech(String tech) { this.tech = tech; }

    public LocalDate getTestDate() { return testDate; }
    public void setTestDate(LocalDate testDate) { this.testDate = testDate; }

    public BigDecimal getMatchRate() { return matchRate; }
    public void setMatchRate(BigDecimal matchRate) { this.matchRate = matchRate; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getImagePathTime() { return imagePathTime; }
    public void setImagePathTime(String imagePathTime) { this.imagePathTime = imagePathTime; }

    public String getImagePathFreq() { return imagePathFreq; }
    public void setImagePathFreq(String imagePathFreq) { this.imagePathFreq = imagePathFreq; }

    public String getImageDescTime() { return imageDescTime; }
    public void setImageDescTime(String imageDescTime) { this.imageDescTime = imageDescTime; }

    public String getImageDescFreq() { return imageDescFreq; }
    public void setImageDescFreq(String imageDescFreq) { this.imageDescFreq = imageDescFreq; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
