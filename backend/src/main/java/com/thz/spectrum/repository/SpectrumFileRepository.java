package com.thz.spectrum.repository;

import com.thz.spectrum.entity.SpectrumFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SpectrumFileRepository extends JpaRepository<SpectrumFile, Long> {

    Optional<SpectrumFile> findByProductIdAndDataType(Long productId, String dataType);

    List<SpectrumFile> findByProductIdOrderByDataTypeAsc(Long productId);

    List<SpectrumFile> findAllByOrderByProductIdAscDataTypeAsc();

    /** 列表：JOIN products 取名称/追溯码，按产品 ID + 类型排序 */
    @Query("SELECT sf, p.code, p.name, p.type FROM SpectrumFile sf JOIN Product p ON sf.productId = p.id ORDER BY sf.productId, sf.dataType")
    List<Object[]> findAllWithProductInfo();
}
