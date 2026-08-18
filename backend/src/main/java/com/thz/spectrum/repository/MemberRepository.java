package com.thz.spectrum.repository;

import com.thz.spectrum.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);
    long countByRole(String role);
}
