package com.thz.spectrum.repository;

import com.thz.spectrum.entity.RecycleBin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RecycleBinRepository extends JpaRepository<RecycleBin, Long> {
    List<RecycleBin> findAllByOrderByDeletedAtDesc();
    List<RecycleBin> findByDeletedAtBefore(LocalDateTime time);
}
