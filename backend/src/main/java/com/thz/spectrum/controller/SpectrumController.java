package com.thz.spectrum.controller;

import com.thz.spectrum.storage.StorageService;
import com.thz.spectrum.entity.*;
import com.thz.spectrum.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 光谱数据管理控制器
 * 列表按产品维度展示（时域+频域），文件状态分行显示
 */
@RestController
@RequestMapping("/api/spectrum")
public class SpectrumController {

    @Autowired private SpectrumFileRepository repo;
    @Autowired private ProductRepository productRepo;
    @Autowired private StorageService storageService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_PREVIEW_POINTS = 20000;

    // ============================================================
    // 1. 列表（JOIN products 取追溯码+名称）
    // ============================================================
    @GetMapping("/list")
    public List<Map<String, Object>> list() {
        List<Object[]> rows = repo.findAllWithProductInfo();
        Map<Long, Map<String, Object>> productMap = new LinkedHashMap<>();

        for (Object[] row : rows) {
            SpectrumFile sf = (SpectrumFile) row[0];
            String code = (String) row[1];
            String name = (String) row[2];
            String type = (String) row[3];

            Map<String, Object> p = productMap.computeIfAbsent(sf.getProductId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", sf.getProductId());
                m.put("code", code);
                m.put("name", name);
                m.put("type", type);
                m.put("timeFile", null);
                m.put("freqFile", null);
                return m;
            });

            Map<String, Object> fileInfo = new LinkedHashMap<>();
            fileInfo.put("id", sf.getId());
            fileInfo.put("hasFile", sf.getFilePath() != null && !sf.getFilePath().isBlank());
            fileInfo.put("fileSize", sf.getFileSize());
            fileInfo.put("originalName", sf.getOriginalName());
            fileInfo.put("createdAt", sf.getCreatedAt());

            if ("时域".equals(sf.getDataType())) {
                p.put("timeFile", fileInfo);
            } else {
                p.put("freqFile", fileInfo);
            }
        }
        return new ArrayList<>(productMap.values());
    }

    // ============================================================
    // 2. 上传 / 更新文件（Admin 内部调用，JWT 保护）
    // ============================================================
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam Long productId,
            @RequestParam String dataType,
            @RequestParam MultipartFile file) {

        if (!"时域".equals(dataType) && !"频域".equals(dataType))
            return Map.of("error", "dataType 只能是 时域 或 频域");
        if (!productRepo.existsById(productId)) return Map.of("error", "产品不存在: " + productId);
        if (file.getSize() > MAX_FILE_SIZE) return Map.of("error", "文件超过10MB");
        String origName = file.getOriginalFilename();
        if (origName == null || !origName.toLowerCase().endsWith(".txt"))
            return Map.of("error", "仅支持 .txt 文件");

        try {
            // 更新已有行
            SpectrumFile sf = repo.findByProductIdAndDataType(productId, dataType)
                    .orElseGet(() -> {
                        SpectrumFile n = new SpectrumFile();
                        n.setProductId(productId);
                        n.setDataType(dataType);
                        return n;
                    });
            String url = storageService.store("spectrum_files", origName, file.getInputStream(), file.getSize());
            sf.setFilePath(url);
            sf.setFileSize(file.getSize());
            sf.setOriginalName(origName);
            repo.save(sf);
            return Map.of("success", true, "id", sf.getId(), "message", "上传成功");
        } catch (IOException e) {
            return Map.of("error", "保存失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 3. 下载
    // ============================================================
    @GetMapping("/download")
    public void download(@RequestParam Long id, HttpServletResponse response) {
        SpectrumFile sf = repo.findById(id).orElse(null);
        if (sf == null || sf.getFilePath() == null) {
            try { response.sendError(404, "文件不存在"); } catch (IOException e) {}
            return;
        }
        try {
            response.setContentType("text/plain;charset=UTF-8");
            String name = URLEncoder.encode(sf.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + name);
            response.setContentLengthLong(sf.getFileSize());
            try (OutputStream out = response.getOutputStream(); InputStream in = storageService.open(sf.getFilePath())) {
                byte[] buf = new byte[8192]; int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                out.flush();
            }
        } catch (IOException e) { try { response.sendError(500); } catch (IOException ex) {} }
    }

    // ============================================================
    // 4. 批量下载（zip）
    // ============================================================
    @PostMapping("/batch-download")
    public void batchDownload(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        // 类型归一化：JSON 数字可能是 Integer，统一转 Long
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> raw) {
            for (Object v : raw) {
                try { ids.add(Long.valueOf(String.valueOf(v))); }
                catch (NumberFormatException e) { /* 跳过非法 id */ }
            }
        }
        if (ids.isEmpty()) { try { response.sendError(400); } catch (IOException e) {} return; }
        List<SpectrumFile> files = repo.findAllById(ids);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''batch_" + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (SpectrumFile sf : files) {
                if (sf.getFilePath() == null) continue;
                // 文件名加 id 前缀，避免重名条目导致 zip 写入失败
                String entryName = sf.getId() + "_" + (sf.getOriginalName() == null ? "file.txt" : sf.getOriginalName());
                try (InputStream in = storageService.open(sf.getFilePath())) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    in.transferTo(zos);
                    zos.closeEntry();
                } catch (IOException ignore) { /* 单个文件缺失则跳过 */ }
            }
            zos.flush();
        } catch (IOException e) { try { response.sendError(500); } catch (IOException ex) {} }
    }

    // ============================================================
    // 5. 预览
    // ============================================================
    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam Long id) {
        SpectrumFile sf = repo.findById(id).orElse(null);
        if (sf == null || sf.getFilePath() == null) return Map.of("error", "文件不存在");
        try {
            List<String> lines;
            try (InputStream in = storageService.open(sf.getFilePath());
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                lines = br.lines().toList();
            }
            List<List<Double>> data = parseSpectrumLines(lines, MAX_PREVIEW_POINTS);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", sf.getId());
            result.put("dataType", sf.getDataType());
            result.put("originalName", sf.getOriginalName());
            result.put("data", data);
            result.put("pointCount", data.size());
            result.put("truncated", data.size() >= MAX_PREVIEW_POINTS);
            return result;
        } catch (IOException e) { return Map.of("error", "读取失败"); }
    }

    private List<List<Double>> parseSpectrumLines(List<String> lines, int maxPoints) {
        List<List<Double>> data = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("[\\s,;]+");
            if (parts.length >= 2) {
                try {
                    data.add(List.of(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
                    if (data.size() >= maxPoints) break;
                } catch (NumberFormatException e) { /* skip */ }
            }
        }
        return data;
    }
}
