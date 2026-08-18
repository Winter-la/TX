package com.thz.spectrum.controller;

import com.thz.spectrum.entity.Member;
import com.thz.spectrum.repository.MemberRepository;
import com.thz.spectrum.security.JwtAuthInterceptor;
import com.thz.spectrum.util.Texts;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 会员管理（/admin/** 由 JWT 拦截器保护，仅 admin 角色可访问）
 * - 会员/管理员的创建、删除
 * - 当前登录账号修改密码
 */
@RestController
@RequestMapping("/admin")
public class MemberController {

    @Autowired private MemberRepository memberRepo;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 用户列表（不含密码哈希） */
    @GetMapping("/members")
    public List<Map<String, Object>> listMembers() {
        return memberRepo.findAll().stream().map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("username", m.getUsername());
            r.put("role", m.getRole());
            r.put("email", m.getEmail());
            r.put("phone", m.getPhone());
            r.put("createdAt", m.getCreatedAt());
            return r;
        }).toList();
    }

    /** 创建会员/管理员 */
    @PostMapping("/members")
    public Map<String, Object> createMember(@RequestBody Map<String, String> body) {
        String username = Texts.clip(body.getOrDefault("username", ""), 50);
        String password = body.getOrDefault("password", "");
        String role = Texts.clip(body.getOrDefault("role", "member"), 20);

        if (username == null || username.isBlank()) return Map.of("error", "用户名不能为空");
        if (password.length() < 6) return Map.of("error", "密码长度至少 6 位");
        if (password.length() > 64) return Map.of("error", "密码长度不能超过 64 位");
        if (!"admin".equals(role) && !"member".equals(role)) return Map.of("error", "角色只能是 admin 或 member");
        if (memberRepo.findByUsername(username).isPresent()) return Map.of("error", "用户名已存在");

        Member m = new Member();
        m.setUsername(username);
        m.setPasswordHash(encoder.encode(password));
        m.setRole(role);
        m.setEmail(Texts.clip(body.getOrDefault("email", ""), 100));
        m.setPhone(Texts.clip(body.getOrDefault("phone", ""), 30));
        memberRepo.save(m);
        return Map.of("success", true, "message", "用户 " + username + " 创建成功");
    }

    /** 删除用户（不能删除自己，系统至少保留一名管理员） */
    @DeleteMapping("/members/{id}")
    public Map<String, Object> deleteMember(@PathVariable Long id, HttpServletRequest request) {
        Member target = memberRepo.findById(id).orElse(null);
        if (target == null) return Map.of("error", "用户不存在");

        String currentUser = (String) request.getAttribute(JwtAuthInterceptor.USER_ATTR);
        if (target.getUsername().equals(currentUser)) return Map.of("error", "不能删除当前登录账号");
        if ("admin".equals(target.getRole()) && memberRepo.countByRole("admin") <= 1) {
            return Map.of("error", "系统至少保留一名管理员");
        }

        memberRepo.deleteById(id);
        return Map.of("success", true);
    }

    /** 修改当前登录账号密码 */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body,
                                              HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtAuthInterceptor.USER_ATTR);
        Member m = memberRepo.findByUsername(username).orElse(null);
        if (m == null) return Map.of("error", "账号不存在");

        String oldPassword = body.getOrDefault("oldPassword", "");
        String newPassword = body.getOrDefault("newPassword", "");
        if (!encoder.matches(oldPassword, m.getPasswordHash())) return Map.of("error", "原密码错误");
        if (newPassword.length() < 6) return Map.of("error", "新密码长度至少 6 位");
        if (newPassword.length() > 64) return Map.of("error", "新密码长度不能超过 64 位");

        m.setPasswordHash(encoder.encode(newPassword));
        memberRepo.save(m);
        return Map.of("success", true, "message", "密码修改成功");
    }
}
