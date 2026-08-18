package com.thz.spectrum.controller;

import com.thz.spectrum.storage.StorageService;
import com.thz.spectrum.entity.*;
import com.thz.spectrum.repository.*;
import com.thz.spectrum.util.Texts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.*;

/**
 * 政策链接 & 公告公开接口 + 管理员发布接口
 */
@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired private PolicyLinkRepository policyLinkRepo;
    @Autowired private AnnouncementRepository announcementRepo;
    @Autowired private NewsArticleRepository newsArticleRepo;
    @Autowired private StorageService storageService;

    // ============================================================
    // 公开接口
    // ============================================================

    @GetMapping("/policy-links")
    public List<PolicyLink> listPolicyLinks() {
        return policyLinkRepo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/news")
    public List<NewsArticle> listNews() {
        return newsArticleRepo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/all")
    public Map<String, Object> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);
        // 组合三表，按时间排序分页
        List<Map<String, Object>> all = new ArrayList<>();
        // 简单方案：加载全部然后排序分页（数据量小时可用）
        newsArticleRepo.findAll().forEach(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "news"); m.put("id", n.getId()); m.put("title", n.getTitle());
            m.put("createdAt", n.getCreatedAt()); all.add(m);
        });
        policyLinkRepo.findAll().forEach(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "policy"); m.put("id", p.getId()); m.put("title", p.getTitle());
            m.put("createdAt", p.getCreatedAt()); all.add(m);
        });
        announcementRepo.findAll().forEach(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "announce"); m.put("id", a.getId()); m.put("title", a.getTitle());
            m.put("createdAt", a.getCreatedAt()); all.add(m);
        });
        all.sort((a, b) -> {
            var ta = (java.time.LocalDateTime) a.get("createdAt");
            var tb = (java.time.LocalDateTime) b.get("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1; if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        int total = all.size();
        int from = page * size;
        int to = Math.min(from + size, total);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("pages", (total + size - 1) / size);
        result.put("items", from < total ? all.subList(from, to) : List.of());
        return result;
    }

    @GetMapping("/announcements")
    public List<Map<String, Object>> listAnnouncements() {
        return announcementRepo.findAllByOrderByCreatedAtDesc().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("content", a.getContent());
            m.put("filePath", a.getFilePath());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList();
    }

    @GetMapping("/announcement/{id}")
    public Announcement getAnnouncement(@PathVariable Long id) {
        return announcementRepo.findById(id).orElse(null);
    }

    // ============================================================
    // 管理员发布接口 (JWT 保护 — 拦截 /admin/content/** )
    // ============================================================

    @PostMapping("/policy-link/add")
    public Map<String, Object> addPolicyLink(@RequestBody Map<String, String> body) {
        String title = Texts.clip(body.get("title"), 300);
        String url = body.get("url") == null ? "" : body.get("url").trim();
        if (title == null || title.isBlank()) return Map.of("error", "标题不能为空");
        if (!url.matches("^(https?://|/).*")) return Map.of("error", "链接必须以 http(s):// 或 / 开头");
        if (url.length() > 1000) url = url.substring(0, 1000);
        PolicyLink p = new PolicyLink();
        p.setTitle(title);
        p.setUrl(url);
        p.setSummary(Texts.clip(body.getOrDefault("summary", ""), 500));
        policyLinkRepo.save(p);
        return Map.of("success", true);
    }

    @PostMapping("/news/add")
    public Map<String, Object> addNewsArticle(@RequestBody Map<String, String> body) {
        String title = Texts.clip(body.get("title"), 300);
        if (title == null || title.isBlank()) return Map.of("error", "标题不能为空");
        String url = body.get("url") == null ? "" : body.get("url").trim();
        if (!url.isEmpty() && !url.matches("^(https?://|/).*"))
            return Map.of("error", "链接必须以 http(s):// 或 / 开头");
        if (url.length() > 1000) url = url.substring(0, 1000);
        NewsArticle n = new NewsArticle();
        n.setTitle(title);
        n.setUrl(url);
        n.setSummary(Texts.clip(body.getOrDefault("summary", ""), 500));
        newsArticleRepo.save(n);
        return Map.of("success", true);
    }

    /** 统一的删除端点：/api/content/{type}/{id} — 仅管理员 */
    @DeleteMapping("/{type}/{id}")
    public ResponseEntity<?> deleteContent(@PathVariable String type, @PathVariable Long id,
                                           HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足：仅管理员可删除"));
        }
        boolean ok;
        switch (type) {
            case "policy-link": policyLinkRepo.deleteById(id); ok = true; break;
            case "news": newsArticleRepo.deleteById(id); ok = true; break;
            case "announcement": announcementRepo.deleteById(id); ok = true; break;
            default: ok = false;
        }
        return ok ? ResponseEntity.ok(Map.of("success", true))
                : ResponseEntity.badRequest().body(Map.of("error", "未知类型"));
    }

    @PostMapping("/announcement/add")
    public Map<String, Object> addAnnouncement(
            @RequestParam String title,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile file) {
        String t = Texts.clip(title, 300);
        if (t == null || t.isBlank()) return Map.of("error", "标题不能为空");
        if (file != null && !file.isEmpty() && file.getSize() > 10 * 1024 * 1024) {
            return Map.of("error", "附件不能超过 10MB");
        }
        Announcement a = new Announcement();
        a.setTitle(t);
        a.setContent(Texts.clip(content, 20000));
        if (file != null && !file.isEmpty()) {
            try {
                a.setFilePath(storageService.store("announcements", file.getOriginalFilename(),
                        file.getInputStream(), file.getSize()));
            } catch (IOException e) {
                return Map.of("error", "文件保存失败");
            }
        }
        announcementRepo.save(a);
        return Map.of("success", true);
    }
}
