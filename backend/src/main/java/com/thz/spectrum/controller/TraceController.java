package com.thz.spectrum.controller;

import com.thz.spectrum.entity.*;
import com.thz.spectrum.repository.*;
import com.thz.spectrum.util.Texts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class TraceController {

    @Autowired private TraceFlowRepository traceFlowRepo;
    @Autowired private ProductRepository productRepo;

    /**
     * 追溯流向查询（公开，无需登录）
     * GET /api/trace/query?code=xxx
     * 注意：购买者隐私字段（姓名/电话/地址）做脱敏处理，仅管理员列表接口返回完整信息
     */
    @GetMapping("/api/trace/query")
    public Map<String, Object> query(@RequestParam String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 按追溯码查询（截断超长输入，避免异常大查询）
        String q = Texts.clip(code, 50);
        if (q == null || q.isBlank()) q = "";
        TraceFlow flow = traceFlowRepo.findByTraceCode(q).orElse(null);

        if (flow == null) {
            result.put("found", false);
            result.put("message", "未找到该追溯码对应的流通记录");
            return result;
        }

        result.put("found", true);
        // 查产品信息
        Product product = productRepo.findByCode(flow.getProductCode()).orElse(null);
        if (product != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("code", product.getCode());
            p.put("name", product.getName());
            p.put("type", product.getType());
            p.put("origin", product.getOrigin());
            result.put("product", p);
        }

        result.put("flows", List.of(Map.of(
            "traceCode", flow.getTraceCode(),
            "buyerName", maskName(flow.getBuyerName()),
            "buyerPhone", maskPhone(flow.getBuyerPhone()),
            "buyerAddress", maskAddress(flow.getBuyerAddress()),
            "saleTime", flow.getSaleTime() != null ? flow.getSaleTime().toString() : ""
        )));

        return result;
    }

    /** 姓名脱敏：保留首字，其余用 * 代替 */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) return "";
        if (name.length() == 1) return "*";
        return name.substring(0, 1) + "*".repeat(name.length() - 1);
    }

    /** 电话脱敏：保留前 3 后 4 位，中间用 * 代替 */
    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        if (phone.length() < 7) return "*".repeat(phone.length());
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 地址脱敏：保留前 4 个字符，其余用 * 代替 */
    private String maskAddress(String address) {
        if (address == null || address.isEmpty()) return "";
        if (address.length() <= 4) return "****";
        return address.substring(0, 4) + "****";
    }

    /**
     * 管理员列表（JWT保护，分页）
     * GET /api/admin/trace/list?page=0&size=20
     */
    @GetMapping("/api/admin/trace/list")
    public Map<String, Object> listTrace(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);
        var pageResult = traceFlowRepo.findAll(
                PageRequest.of(page, size, Sort.by("saleTime").descending()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotalElements());
        result.put("pages", pageResult.getTotalPages());
        result.put("items", pageResult.getContent().stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("productCode", f.getProductCode());
            m.put("traceCode", f.getTraceCode());
            m.put("buyerName", f.getBuyerName());
            m.put("buyerPhone", f.getBuyerPhone());
            m.put("saleTime", f.getSaleTime());
            return m;
        }).toList());
        return result;
    }

    /**
     * 管理员：按产品编码查询其全部追溯记录（JWT保护）
     * GET /api/admin/trace/by-product?productCode=xxx
     */
    @GetMapping("/api/admin/trace/by-product")
    public Map<String, Object> listByProduct(@RequestParam String productCode) {
        String pc = Texts.clip(productCode, 20);
        List<Map<String, Object>> items = traceFlowRepo
                .findByProductCodeOrderBySaleTimeDesc(pc).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("traceCode", f.getTraceCode());
            m.put("buyerName", f.getBuyerName());
            m.put("buyerPhone", f.getBuyerPhone());
            m.put("buyerAddress", f.getBuyerAddress());
            m.put("idCard", f.getIdCard());
            m.put("saleTime", f.getSaleTime());
            return m;
        }).toList();
        return Map.of("items", items);
    }

    /**
     * 录入追溯信息（Admin，JWT保护）
     */
    @PostMapping("/api/admin/trace/add")
    public Map<String, Object> addTrace(@RequestBody Map<String, String> body) {
        String productCode = Texts.clip(body.get("productCode"), 20);
        if (productCode == null || productCode.isBlank()) return Map.of("error", "产品编码不能为空");
        if (productRepo.findByCode(productCode).isEmpty())
            return Map.of("error", "产品编码不存在: " + productCode);

        String buyerName = Texts.clip(body.getOrDefault("buyerName", ""), 100);
        String buyerPhone = Texts.clip(body.getOrDefault("buyerPhone", ""), 30);
        if (buyerName == null || buyerName.isBlank()) return Map.of("error", "购买者姓名不能为空");
        if (!buyerName.matches("^[\\u4e00-\\u9fa5]{2,4}$"))
            return Map.of("error", "购买者姓名应为2-4个汉字");
        if (buyerPhone == null || buyerPhone.isBlank()) return Map.of("error", "联系电话不能为空");
        if (!buyerPhone.matches("^1\\d{10}$"))
            return Map.of("error", "联系电话应为11位手机号");
        String idCard = Texts.clip(body.getOrDefault("idCard", ""), 30);
        if (!idCard.isBlank() && !idCard.matches("^\\d{17}[\\dXx]$"))
            return Map.of("error", "身份证号应为18位（末位可为X）");

        // 追溯码：使用传入值（校验唯一）或自动生成（时间戳+随机数防冲突）
        String traceCode = Texts.clip(body.getOrDefault("traceCode", ""), 50);
        if (traceCode == null || traceCode.isBlank()) {
            traceCode = "TR" + System.currentTimeMillis()
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } else if (traceFlowRepo.findByTraceCode(traceCode).isPresent()) {
            return Map.of("error", "追溯码已存在: " + traceCode);
        }

        TraceFlow tf = new TraceFlow();
        tf.setProductCode(productCode);
        tf.setTraceCode(traceCode);
        tf.setBuyerName(buyerName);
        tf.setBuyerPhone(buyerPhone);
        tf.setBuyerAddress(Texts.clip(body.getOrDefault("buyerAddress", ""), 300));
        tf.setIdCard(idCard);
        if (body.containsKey("saleTime")) {
            try { tf.setSaleTime(LocalDateTime.parse(body.get("saleTime"))); } catch (Exception e) {}
        }
        traceFlowRepo.save(tf);
        return Map.of("success", true, "traceCode", tf.getTraceCode());
    }
}
