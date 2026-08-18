package com.thz.spectrum.controller;

import com.thz.spectrum.entity.*;
import com.thz.spectrum.repository.*;
import com.thz.spectrum.util.Texts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class QueryController {

    @Autowired private ProductRepository productRepo;
    @Autowired private SpectrumRecordRepository recordRepo;

    /**
     * 追溯码查询
     * GET /api/query?code=001
     * 返回: { found, product(基础信息), spectrum(检测信息) }
     */
    @GetMapping("/query")
    public Map<String, Object> query(@RequestParam String code) {
        String q = Texts.clip(code, 50);
        if (q == null) q = "";
        Optional<Product> opt = productRepo.findByCode(q);
        Map<String, Object> result = new LinkedHashMap<>();

        if (opt.isEmpty()) {
            result.put("found", false);
            result.put("message", "未找到该追溯码对应的产品");
            return result;
        }

        Product p = opt.get();
        result.put("found", true);

        // ---- 基础信息 ----
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("code",        p.getCode());
        product.put("name",        p.getName());
        product.put("alias",       p.getAlias());
        product.put("type",        p.getType());
        product.put("origin",      p.getOrigin());
        product.put("latin",       p.getLatin());
        product.put("part",        p.getPart());
        product.put("description", p.getDescription());
        product.put("conclusion",  p.getConclusion());
        result.put("product", product);

        // ---- 检测信息（最新一条） ----
        Optional<SpectrumRecord> recOpt = recordRepo.findFirstByProductIdOrderByTestDateDesc(p.getId());
        Map<String, Object> spectrum = new LinkedHashMap<>();
        if (recOpt.isPresent()) {
            SpectrumRecord sr = recOpt.get();
            spectrum.put("tech",          sr.getTech());
            spectrum.put("test_date",     sr.getTestDate());
            spectrum.put("match_rate",    sr.getMatchRate());
            spectrum.put("grade",         sr.getGrade());
            spectrum.put("image_time",    sr.getImagePathTime());
            spectrum.put("image_freq",    sr.getImagePathFreq());
            spectrum.put("desc_time",     sr.getImageDescTime());
            spectrum.put("desc_freq",     sr.getImageDescFreq());
        }
        result.put("spectrum", spectrum);

        return result;
    }

    /**
     * 产品列表（分页）
     * GET /api/products?page=0&size=20
     */
    @GetMapping("/products")
    public Map<String, Object> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);
        var pageResult = productRepo.findAll(
                org.springframework.data.domain.PageRequest.of(page, size));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotalElements());
        result.put("page",  pageResult.getNumber());
        result.put("items", pageResult.getContent().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getCode());
            m.put("name", p.getName());
            m.put("type", p.getType());
            m.put("origin", p.getOrigin());
            m.put("description", p.getDescription());
            return m;
        }).toList());
        return result;
    }
}
