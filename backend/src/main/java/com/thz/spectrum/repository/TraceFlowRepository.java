package com.thz.spectrum.repository;

import com.thz.spectrum.entity.TraceFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TraceFlowRepository extends JpaRepository<TraceFlow, Long> {
    Optional<TraceFlow> findByTraceCode(String traceCode);
    List<TraceFlow> findByProductCodeOrderBySaleTimeDesc(String productCode);
}
