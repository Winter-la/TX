package com.thz.spectrum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thz.spectrum.entity.*;
import com.thz.spectrum.repository.*;
import com.thz.spectrum.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 产品回收站：
 * - 删除产品时，把产品 + 检测记录 + 光谱文件 + 流向 序列化为 JSON 快照存入 recycle_bin；
 *   关联文件本身保留在存储中（仅由回收站条目引用），恢复时路径依然有效。
 * - 支持恢复、彻底删除（连同文件）、清空、以及按保留天数自动清理。
 */
@Service
public class RecycleService {

    private static final Logger log = LoggerFactory.getLogger(RecycleService.class);

    @Autowired private ProductRepository productRepo;
    @Autowired private SpectrumRecordRepository recordRepo;
    @Autowired private SpectrumFileRepository fileRepo;
    @Autowired private TraceFlowRepository traceRepo;
    @Autowired private RecycleBinRepository recycleRepo;
    @Autowired private StorageService storageService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${app.recycle.retention-days:30}")
    private int retentionDays;

    /** 删除产品 → 移入回收站 */
    public Map<String, Object> deleteProduct(Long productId, String operator) {
        Product p = productRepo.findById(productId).orElse(null);
        if (p == null) return Map.of("error", "产品不存在");

        List<SpectrumRecord> records = recordRepo.findByProductIdOrderByTestDateDesc(productId);
        List<SpectrumFile> sfiles = fileRepo.findByProductIdOrderByDataTypeAsc(productId);
        List<TraceFlow> traces = traceRepo.findByProductCodeOrderBySaleTimeDesc(p.getCode());

        // 快照
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("product", productMap(p));
        snapshot.put("records", records.stream().map(this::recordMap).toList());
        snapshot.put("spectrumFiles", sfiles.stream().map(this::fileMap).toList());
        snapshot.put("traces", traces.stream().map(this::traceMap).toList());
        snapshot.put("fileRefs", collectFileRefs(records, sfiles));

        RecycleBin rb = new RecycleBin();
        rb.setItemType("product");
        rb.setOriginalId(productId);
        rb.setCode(p.getCode());
        rb.setName(p.getName());
        rb.setSnapshot(writeJson(snapshot));
        rb.setDeletedBy(operator);
        recycleRepo.save(rb);

        // 显式删除关联数据（不依赖数据库级联，兼容 Railway 的 ddl-auto=update）
        recordRepo.deleteAll(records);
        fileRepo.deleteAll(sfiles);
        traceRepo.deleteAll(traces);
        productRepo.delete(p);

        return Map.of("success", true, "message", "产品 " + p.getCode() + " 已移入回收站");
    }

    /** 恢复 */
    public Map<String, Object> restore(Long recycleId) {
        RecycleBin rb = recycleRepo.findById(recycleId).orElse(null);
        if (rb == null) return Map.of("error", "回收站记录不存在");

        Map<String, Object> snapshot = readJson(rb.getSnapshot());
        @SuppressWarnings("unchecked")
        Map<String, Object> pm = (Map<String, Object>) snapshot.get("product");
        String code = str(pm.get("code"));
        if (productRepo.findByCode(code).isPresent()) {
            return Map.of("error", "追溯码 " + code + " 已存在，无法恢复");
        }

        // 产品
        Product p = new Product();
        p.setCode(code);
        p.setName(str(pm.get("name")));
        p.setAlias(str(pm.get("alias")));
        p.setType(str(pm.get("type")));
        p.setOrigin(str(pm.get("origin")));
        p.setLatin(str(pm.get("latin")));
        p.setPart(str(pm.get("part")));
        p.setDescription(str(pm.get("description")));
        p.setConclusion(str(pm.get("conclusion")));
        productRepo.save(p);

        // 检测记录
        for (Object o : list(snapshot.get("records"))) {
            Map<?, ?> r = (Map<?, ?>) o;
            SpectrumRecord sr = new SpectrumRecord();
            sr.setProduct(p);
            sr.setTech(str(r.get("tech")));
            sr.setTestDate(dte(r.get("testDate")));
            sr.setMatchRate(bd(r.get("matchRate")));
            sr.setGrade(str(r.get("grade")));
            sr.setImagePathTime(str(r.get("imagePathTime")));
            sr.setImagePathFreq(str(r.get("imagePathFreq")));
            sr.setImageDescTime(str(r.get("imageDescTime")));
            sr.setImageDescFreq(str(r.get("imageDescFreq")));
            recordRepo.save(sr);
        }

        // 光谱源文件
        for (Object o : list(snapshot.get("spectrumFiles"))) {
            Map<?, ?> f = (Map<?, ?>) o;
            SpectrumFile sf = new SpectrumFile();
            sf.setProductId(p.getId());
            sf.setDataType(str(f.get("dataType")));
            sf.setFilePath(str(f.get("filePath")));
            sf.setFileSize(lng(f.get("fileSize")));
            sf.setOriginalName(str(f.get("originalName")));
            fileRepo.save(sf);
        }

        // 流向
        for (Object o : list(snapshot.get("traces"))) {
            Map<?, ?> t = (Map<?, ?>) o;
            String traceCode = str(t.get("traceCode"));
            if (traceCode != null && traceRepo.findByTraceCode(traceCode).isPresent()) continue; // 冲突跳过
            TraceFlow tf = new TraceFlow();
            tf.setProductCode(code);
            tf.setTraceCode(traceCode);
            tf.setBuyerName(str(t.get("buyerName")));
            tf.setBuyerPhone(str(t.get("buyerPhone")));
            tf.setBuyerAddress(str(t.get("buyerAddress")));
            tf.setIdCard(str(t.get("idCard")));
            tf.setSaleTime(dtm(t.get("saleTime")));
            traceRepo.save(tf);
        }

        recycleRepo.deleteById(recycleId);
        return Map.of("success", true, "message", "产品 " + code + " 已恢复");
    }

    /** 彻底删除（连同文件） */
    public Map<String, Object> permanentDelete(Long recycleId) {
        RecycleBin rb = recycleRepo.findById(recycleId).orElse(null);
        if (rb == null) return Map.of("error", "回收站记录不存在");
        purge(rb);
        return Map.of("success", true);
    }

    /** 列表 */
    public List<Map<String, Object>> list() {
        return recycleRepo.findAllByOrderByDeletedAtDesc().stream().map(rb -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rb.getId());
            m.put("itemType", rb.getItemType());
            m.put("code", rb.getCode());
            m.put("name", rb.getName());
            m.put("deletedBy", rb.getDeletedBy());
            m.put("deletedAt", rb.getDeletedAt());
            return m;
        }).toList();
    }

    /** 清空回收站 */
    public Map<String, Object> emptyAll() {
        List<RecycleBin> all = recycleRepo.findAll();
        for (RecycleBin rb : all) purge(rb);
        return Map.of("success", true, "message", "已清空 " + all.size() + " 条回收站记录");
    }

    /** 定时清理：删除超过保留天数的条目 */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpired() {
        List<RecycleBin> expired = recycleRepo.findByDeletedAtBefore(LocalDateTime.now().minusDays(Math.max(retentionDays, 1)));
        for (RecycleBin rb : expired) purge(rb);
        if (!expired.isEmpty()) log.info("回收站自动清理 {} 条过期记录（保留 {} 天）", expired.size(), retentionDays);
    }

    /** 删除条目对应的文件并删除记录 */
    private void purge(RecycleBin rb) {
        try {
            Map<String, Object> snapshot = readJson(rb.getSnapshot());
            for (Object ref : list(snapshot.get("fileRefs"))) {
                try {
                    storageService.delete(str(ref));
                } catch (IOException e) {
                    log.warn("回收站文件删除失败（可忽略）: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("回收站快照解析失败，跳过文件删除: {}", e.getMessage());
        }
        recycleRepo.delete(rb);
    }

    // ===== 快照构造 =====
    private Map<String, Object> productMap(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("alias", p.getAlias());
        m.put("type", p.getType());
        m.put("origin", p.getOrigin());
        m.put("latin", p.getLatin());
        m.put("part", p.getPart());
        m.put("description", p.getDescription());
        m.put("conclusion", p.getConclusion());
        return m;
    }

    private Map<String, Object> recordMap(SpectrumRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tech", r.getTech());
        m.put("testDate", r.getTestDate() == null ? null : r.getTestDate().toString());
        m.put("matchRate", r.getMatchRate() == null ? null : r.getMatchRate().toString());
        m.put("grade", r.getGrade());
        m.put("imagePathTime", r.getImagePathTime());
        m.put("imagePathFreq", r.getImagePathFreq());
        m.put("imageDescTime", r.getImageDescTime());
        m.put("imageDescFreq", r.getImageDescFreq());
        return m;
    }

    private Map<String, Object> fileMap(SpectrumFile f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataType", f.getDataType());
        m.put("filePath", f.getFilePath());
        m.put("fileSize", f.getFileSize());
        m.put("originalName", f.getOriginalName());
        return m;
    }

    private Map<String, Object> traceMap(TraceFlow t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productCode", t.getProductCode());
        m.put("traceCode", t.getTraceCode());
        m.put("buyerName", t.getBuyerName());
        m.put("buyerPhone", t.getBuyerPhone());
        m.put("buyerAddress", t.getBuyerAddress());
        m.put("idCard", t.getIdCard());
        m.put("saleTime", t.getSaleTime() == null ? null : t.getSaleTime().toString());
        return m;
    }

    private List<String> collectFileRefs(List<SpectrumRecord> records, List<SpectrumFile> files) {
        Set<String> refs = new LinkedHashSet<>();
        for (SpectrumRecord r : records) {
            addRef(refs, r.getImagePathTime());
            addRef(refs, r.getImagePathFreq());
        }
        for (SpectrumFile f : files) addRef(refs, f.getFilePath());
        return new ArrayList<>(refs);
    }

    private void addRef(Set<String> refs, String ref) {
        if (ref != null && !ref.isBlank()) refs.add(ref);
    }

    // ===== JSON / 类型工具 =====
    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("快照序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("快照解析失败", e);
        }
    }

    private List<?> list(Object o) {
        return o instanceof List<?> ? (List<?>) o : List.of();
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Long lng(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private LocalDate dte(Object o) {
        String s = str(o);
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    private LocalDateTime dtm(Object o) {
        String s = str(o);
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }

    private BigDecimal bd(Object o) {
        String s = str(o);
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }
}
