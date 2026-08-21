package com.thz.spectrum.controller;

import com.thz.spectrum.entity.*;
import com.thz.spectrum.repository.*;
import com.thz.spectrum.security.JwtUtil;
import com.thz.spectrum.storage.StorageService;
import com.thz.spectrum.util.Texts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping
    public void redirect(HttpServletResponse response) throws Exception {
        response.sendRedirect("/admin/index.html");
    }

    @Autowired private ProductRepository productRepo;
    @Autowired private SpectrumRecordRepository recordRepo;
    @Autowired private SpectrumFileRepository spectrumFileRepo;
    @Autowired private MemberRepository memberRepo;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private StorageService storageService;

    /** 首次启动自动创建默认管理员的初始密码（生产环境请通过 APP_ADMIN_PASSWORD 环境变量指定） */
    @Value("${app.admin.initial-password:admin}")
    private String initialPassword;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final java.util.Set<String> IMAGE_EXTS =
            java.util.Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp");

    /** 首次启动时若 members 表为空，自动创建默认管理员（初始密码见 app.admin.initial-password） */
    @PostConstruct
    public void initDefaultAdmin() {
        if (memberRepo.count() == 0) {
            if (initialPassword == null || initialPassword.isBlank()) {
                throw new IllegalStateException(
                        "members 表为空且未配置初始管理员密码：请设置 APP_ADMIN_PASSWORD 环境变量后重启");
            }
            Member admin = new Member();
            admin.setUsername("admin");
            admin.setPasswordHash(encoder.encode(initialPassword));
            admin.setRole("admin");
            admin.setEmail("admin@thz.com");
            memberRepo.save(admin);
            log.info("默认管理员已创建: admin (role=admin)");
            if ("admin".equals(initialPassword)) {
                log.warn("!! 警告：正在使用默认初始密码，请登录后在【会员管理→修改密码】中立即更改");
            }
        }
    }

    /** 登录：用户名 + BCrypt 密码验证 → JWT Token */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username,
                                     @RequestParam String password) {
        Member member = memberRepo.findByUsername(username).orElse(null);
        if (member == null || !encoder.matches(password, member.getPasswordHash())) {
            return Map.of("error", "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(username, member.getRole());
        return Map.of(
                "success", true,
                "token", token,
                "username", username,
                "role", member.getRole(),
                "expiresIn", 7200
        );
    }

    /** 检查登录状态（JWT 鉴权由拦截器保证，此处返回用户信息） */
    @GetMapping("/check")
    public Map<String, Object> check() {
        return Map.of("loggedIn", true);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        return Map.of("success", true);
    }

    @GetMapping("/products")
    public Map<String, Object> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);
        var pageResult = productRepo.findAll(
                org.springframework.data.domain.PageRequest.of(page, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotalElements());
        result.put("pages", pageResult.getTotalPages());
        result.put("items", pageResult.getContent().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("code", p.getCode());
            m.put("name", p.getName());
            m.put("type", p.getType());
            m.put("origin", p.getOrigin());
            recordRepo.findFirstByProductIdOrderByTestDateDesc(p.getId()).ifPresent(r -> {
                m.put("match_rate", r.getMatchRate());
                m.put("grade", r.getGrade());
                m.put("image_time", r.getImagePathTime());
                m.put("image_freq", r.getImagePathFreq());
            });
            return m;
        }).toList());
        return result;
    }

    @GetMapping("/products/codes")
    public List<Map<String, Object>> listCodes() {
        return productRepo.findAll().stream()
                .sorted(Comparator.comparing(Product::getCode))
                .<Map<String, Object>>map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", p.getCode());
                    m.put("name", p.getName());
                    return m;
                })
                .toList();
    }

    @PostMapping("/products/add")
    public Map<String, Object> addProduct(
            @RequestParam String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String alias,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String latin,
            @RequestParam(required = false) String part,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) MultipartFile image) {
        if (code == null || code.isBlank()) return Map.of("error", "产品编码不能为空");
        code = code.trim();
        if (!code.matches("^[A-Za-z0-9_-]{1,20}$"))
            return Map.of("error", "产品编码仅允许字母、数字、下划线、连字符，且不超过 20 位");
        if (productRepo.findByCode(code).isPresent())
            return Map.of("error", "产品编码 " + code + " 已存在");

        String nm = Texts.clip(name, 100);
        if (nm == null || nm.isBlank()) return Map.of("error", "产品名称不能为空");
        if (productRepo.findByName(nm).isPresent())
            return Map.of("error", "产品名称 " + nm + " 已存在");

        Product p = new Product();
        p.setCode(Texts.clip(code, 20));
        p.setName(nm);
        p.setAlias(Texts.clip(alias, 100));
        p.setType(Texts.clip(type == null ? "中草药" : type, 20));
        p.setOrigin(Texts.clip(origin, 200));
        p.setLatin(Texts.clip(latin, 200));
        p.setPart(Texts.clip(part, 100));
        p.setDescription(Texts.clip(description, 2000));
        p.setConclusion(Texts.clip(conclusion, 200));

        // 药材图片（可选）
        if (image != null && !image.isEmpty()) {
            if (!isImageFile(image))
                return Map.of("error", "药材图片仅支持图片格式（png/jpg/jpeg/gif/webp/bmp）");
            String safeCode = code.replaceAll("[^A-Za-z0-9_-]", "_");
            String filename = safeCode + "_product" + getExtension(image.getOriginalFilename());
            try {
                String url = storageService.store("products", filename, image.getInputStream(), image.getSize());
                p.setImage(url);
            } catch (IOException e) {
                return Map.of("error", "药材图片上传失败: " + e.getMessage());
            }
        }
        productRepo.save(p);

        SpectrumRecord r = new SpectrumRecord();
        r.setProduct(p);
        r.setTech("太赫兹时域光谱");
        r.setTestDate(LocalDate.now());
        recordRepo.save(r);

        // 自动创建光谱文件占位行（时域+频域）
        for (String dt : new String[]{"时域", "频域"}) {
            SpectrumFile sf = new SpectrumFile();
            sf.setProductId(p.getId());
            sf.setDataType(dt);
            spectrumFileRepo.save(sf);
        }

        return Map.of("success", true, "message", "产品 " + code + " 添加成功");
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam String code,
            @RequestParam(required = false) MultipartFile imageTime,
            @RequestParam(required = false) MultipartFile imageFreq,
            @RequestParam(required = false) String tech,
            @RequestParam(required = false) String testDate,
            @RequestParam(required = false) BigDecimal matchRate,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String descTime,
            @RequestParam(required = false) String descFreq,
            @RequestParam(required = false) MultipartFile spectrumTimeFile,
            @RequestParam(required = false) MultipartFile spectrumFreqFile) {

        Product product = productRepo.findByCode(code.trim()).orElse(null);
        if (product == null) return Map.of("error", "产品不存在: " + code);

        SpectrumRecord record = recordRepo.findFirstByProductIdOrderByTestDateDesc(product.getId())
                .orElseGet(() -> {
                    SpectrumRecord r = new SpectrumRecord();
                    r.setProduct(product);
                    r.setTech("太赫兹时域光谱");
                    r.setTestDate(LocalDate.now());
                    return recordRepo.save(r);
                });

        if (tech != null && !tech.isBlank()) record.setTech(Texts.clip(tech, 200));
        if (testDate != null && !testDate.isBlank()) {
            try {
                record.setTestDate(LocalDate.parse(testDate));
            } catch (Exception e) {
                return Map.of("error", "检测日期格式错误（应为 yyyy-MM-dd）");
            }
        }
        if (matchRate != null) {
            if (matchRate.compareTo(BigDecimal.ZERO) < 0 || matchRate.compareTo(new BigDecimal("100")) > 0) {
                return Map.of("error", "匹配度应在 0-100 之间");
            }
            record.setMatchRate(matchRate);
        }
        if (grade != null && !grade.isBlank()) record.setGrade(Texts.clip(grade, 20));
        if (descTime != null) record.setImageDescTime(Texts.clip(descTime, 500));
        if (descFreq != null) record.setImageDescFreq(Texts.clip(descFreq, 500));

        Map<String, Object> result = new LinkedHashMap<>();
        // 追溯码用于拼文件名，此处再做一次清洗，防止路径穿越
        String safeCode = code.replaceAll("[^A-Za-z0-9_-]", "_");

        if (imageTime != null && !imageTime.isEmpty()) {
            if (!isImageFile(imageTime)) {
                return Map.of("error", "时域图仅支持图片格式（png/jpg/jpeg/gif/webp/bmp）");
            }
            try {
                String filename = safeCode + "_time" + getExtension(imageTime.getOriginalFilename());
                String url = storageService.store("spectrum", filename, imageTime.getInputStream(), imageTime.getSize());
                record.setImagePathTime(url);
                result.put("image_time", url);
            } catch (IOException e) {
                result.put("error", "时域图上传失败: " + e.getMessage());
            }
        }
        if (imageFreq != null && !imageFreq.isEmpty()) {
            if (!isImageFile(imageFreq)) {
                return Map.of("error", "频域图仅支持图片格式（png/jpg/jpeg/gif/webp/bmp）");
            }
            try {
                String filename = safeCode + "_freq" + getExtension(imageFreq.getOriginalFilename());
                String url = storageService.store("spectrum", filename, imageFreq.getInputStream(), imageFreq.getSize());
                record.setImagePathFreq(url);
                result.put("image_freq", url);
            } catch (IOException e) {
                result.put("error", "频域图上传失败: " + e.getMessage());
            }
        }
        recordRepo.save(record);

        // 保存关联的 .txt 光谱源文件
        saveSpectrumTextFile(spectrumTimeFile, product.getId(), "时域", result, "spectrum_time");
        saveSpectrumTextFile(spectrumFreqFile, product.getId(), "频域", result, "spectrum_freq");

        if (!result.containsKey("error")) {
            result.put("success", true);
            result.put("message", "上传成功");
        }
        return result;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".png";
    }

    /** 校验上传文件是否为允许的图片格式 */
    private boolean isImageFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return IMAGE_EXTS.stream().anyMatch(lower::endsWith);
    }

    /** 保存 .txt 光谱源文件到 spectrum_files 目录并更新已有数据库行 */
    private void saveSpectrumTextFile(MultipartFile file, Long productId,
                                       String dataType,
                                       Map<String, Object> result, String key) {
        if (file == null || file.isEmpty()) return;
        if (file.getSize() > 10 * 1024 * 1024) {
            result.put(key, "文件超过10MB");
            return;
        }
        String origName = file.getOriginalFilename();
        if (origName == null || !origName.toLowerCase().endsWith(".txt")) {
            result.put(key, "仅支持.txt");
            return;
        }
        try {
            // 查找已有行并更新
            SpectrumFile sf = spectrumFileRepo.findByProductIdAndDataType(productId, dataType)
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
            spectrumFileRepo.save(sf);
            result.put(key, "上传成功");
        } catch (IOException e) {
            result.put(key, "保存失败: " + e.getMessage());
        }
    }
}
