package com.thz.spectrum.controller;

import com.thz.spectrum.storage.StorageService;
import com.thz.spectrum.entity.Feedback;
import com.thz.spectrum.repository.FeedbackRepository;
import com.thz.spectrum.util.Texts;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意见反馈
 * - POST /api/feedback         公众提交（公开，带 IP 限流）
 * - GET  /admin/feedback       管理员列表（JWT 保护）
 * - DELETE /admin/feedback/{id} 管理员删除（JWT 保护）
 */
@RestController
public class FeedbackController {

    @Autowired private FeedbackRepository feedbackRepo;
    @Autowired private StorageService storageService;

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("功能建议", "体验问题", "数据问题", "其他");
    private static final Set<String> IMAGE_EXTS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    /** 限流：同一 IP 每 15 分钟最多 10 次 */
    private static final int RATE_LIMIT = 10;
    private static final long RATE_WINDOW_MS = 15 * 60 * 1000L;
    private static final int WINDOW_MAP_MAX = 5000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** 提交过于频繁时抛出，由全局异常处理器转为 429 */
    public static class TooManyRequestsException extends RuntimeException {}

    private static class Window {
        long start;
        int count;
        Window(long start) { this.start = start; }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private void checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        windows.merge(ip, new Window(now), (old, ign) -> now - old.start > RATE_WINDOW_MS ? new Window(now) : old);
        Window w = windows.get(ip);
        synchronized (w) {
            if (now - w.start > RATE_WINDOW_MS) { w.start = now; w.count = 0; }
            if (w.count >= RATE_LIMIT) throw new TooManyRequestsException();
            w.count++;
        }
        if (windows.size() > WINDOW_MAP_MAX) {
            windows.entrySet().removeIf(e -> now - e.getValue().start > RATE_WINDOW_MS);
        }
    }

    /** 提交意见（公开） */
    @PostMapping("/api/feedback")
    public Map<String, Object> submit(
            @RequestParam String type,
            @RequestParam String content,
            @RequestParam(required = false) String contact,
            @RequestParam(required = false) MultipartFile file,
            HttpServletRequest request) {

        checkRateLimit(clientIp(request));

        String t = Texts.clip(type, 20);
        String c = Texts.clip(content, 1000);
        if (c == null || c.isBlank()) return Map.of("error", "请填写意见内容");
        if (!TYPES.contains(t)) t = "其他";

        Feedback f = new Feedback();
        f.setType(t);
        f.setContent(c);
        f.setContact(Texts.clip(contact, 100));

        if (file != null && !file.isEmpty()) {
            String orig = file.getOriginalFilename();
            if (file.getSize() > MAX_IMAGE_SIZE) return Map.of("error", "截图不能超过 5MB");
            String lower = orig == null ? "" : orig.toLowerCase();
            if (IMAGE_EXTS.stream().noneMatch(lower::endsWith)) {
                return Map.of("error", "截图仅支持常见图片格式（png/jpg/gif/webp）");
            }
            try {
                f.setImagePath(storageService.store("feedback_images", orig, file.getInputStream(), file.getSize()));
            } catch (IOException e) {
                return Map.of("error", "截图保存失败");
            }
        }

        feedbackRepo.save(f);
        return Map.of("success", true, "message", "感谢您的反馈，我们会尽快处理！");
    }

    /** 管理员：反馈列表（最新在前） */
    @GetMapping("/admin/feedback")
    public List<Map<String, Object>> list() {
        return feedbackRepo.findAllByOrderByCreatedAtDesc().stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("type", f.getType());
            m.put("content", f.getContent());
            m.put("contact", f.getContact());
            m.put("imagePath", f.getImagePath());
            m.put("createdAt", f.getCreatedAt());
            return m;
        }).toList();
    }

    /** 管理员：删除反馈 */
    @DeleteMapping("/admin/feedback/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (!feedbackRepo.existsById(id)) return Map.of("error", "记录不存在");
        feedbackRepo.deleteById(id);
        return Map.of("success", true);
    }
}
