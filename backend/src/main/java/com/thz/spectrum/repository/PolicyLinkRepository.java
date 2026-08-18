package com.thz.spectrum.repository;

import com.thz.spectrum.entity.PolicyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PolicyLinkRepository extends JpaRepository<PolicyLink, Long> {
    List<PolicyLink> findAllByOrderByCreatedAtDesc();
}
