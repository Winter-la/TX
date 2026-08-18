package com.thz.spectrum.repository;

import com.thz.spectrum.entity.SpectrumRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpectrumRecordRepository extends JpaRepository<SpectrumRecord, Long> {
    List<SpectrumRecord> findByProductIdOrderByTestDateDesc(Long productId);
    Optional<SpectrumRecord> findFirstByProductIdOrderByTestDateDesc(Long productId);
}
