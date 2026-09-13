package cn.hospital.eph.transfer.web;

import cn.hospital.eph.common.security.RequireRole;
import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.transfer.entity.Pharmacy;
import cn.hospital.eph.transfer.mapper.PharmacyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端：药店注册（适配器类型/密钥/优先级） */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminPharmacyController {

    private final PharmacyMapper pharmacyMapper;

    @Data
    public static class PharmacyRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private String adapterType;
        private String baseUrl;
        private String authType;
        private String appKey;
        private String signSecret;
        private Integer priority;
        private Integer status;
    }

    @GetMapping("/admin/pharmacies")
    @RequireRole("ADMIN")
    public Result<List<Pharmacy>> list() {
        return Result.ok(pharmacyMapper.selectList(
                new QueryWrapper<Pharmacy>().orderByAsc("priority")));
    }

    @PostMapping("/admin/pharmacies")
    @RequireRole("ADMIN")
    public Result<Pharmacy> create(@Valid @RequestBody PharmacyRequest req) {
        Pharmacy p = new Pharmacy();
        p.setCode(req.getCode());
        p.setName(req.getName());
        p.setAdapterType(req.getAdapterType() == null ? "MOCK" : req.getAdapterType());
        p.setBaseUrl(req.getBaseUrl());
        p.setAuthType(req.getAuthType() == null ? "HMAC" : req.getAuthType());
        p.setAppKey(req.getAppKey());
        p.setSignSecret(req.getSignSecret());
        p.setPriority(req.getPriority() == null ? 100 : req.getPriority());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setCreatedAt(LocalDateTime.now());
        pharmacyMapper.insert(p);
        return Result.ok(p);
    }
}
