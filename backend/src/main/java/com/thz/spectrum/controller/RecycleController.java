package com.thz.spectrum.controller;

import com.thz.spectrum.security.JwtAuthInterceptor;
import com.thz.spectrum.service.RecycleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产品删除与回收站（/admin/** 由 JWT 拦截器保护，仅 admin 角色）
 */
@RestController
@RequestMapping("/admin")
public class RecycleController {

    @Autowired private RecycleService recycleService;

    /** 删除产品 → 移入回收站 */
    @DeleteMapping("/products/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        String operator = (String) request.getAttribute(JwtAuthInterceptor.USER_ATTR);
        return recycleService.deleteProduct(id, operator);
    }

    /** 回收站列表 */
    @GetMapping("/recycle")
    public List<Map<String, Object>> list() {
        return recycleService.list();
    }

    /** 恢复 */
    @PostMapping("/recycle/{id}/restore")
    public Map<String, Object> restore(@PathVariable Long id) {
        return recycleService.restore(id);
    }

    /** 彻底删除（连同文件） */
    @DeleteMapping("/recycle/{id}")
    public Map<String, Object> permanentDelete(@PathVariable Long id) {
        return recycleService.permanentDelete(id);
    }

    /** 清空回收站 */
    @DeleteMapping("/recycle")
    public Map<String, Object> emptyAll() {
        return recycleService.emptyAll();
    }
}
