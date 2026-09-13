package cn.hospital.eph.user.web;

import cn.hospital.eph.common.security.UserContext;
import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.user.entity.CaCertificate;
import cn.hospital.eph.user.entity.DoctorProfile;
import cn.hospital.eph.user.entity.PatientProfile;
import cn.hospital.eph.user.entity.PharmacistProfile;
import cn.hospital.eph.user.entity.UserSigningKey;
import cn.hospital.eph.user.mapper.CaCertificateMapper;
import cn.hospital.eph.user.mapper.DoctorProfileMapper;
import cn.hospital.eph.user.mapper.PatientProfileMapper;
import cn.hospital.eph.user.mapper.PharmacistProfileMapper;
import cn.hospital.eph.user.mapper.UserSigningKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 当前登录用户的资质与证书信息 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final DoctorProfileMapper doctorMapper;
    private final PharmacistProfileMapper pharmacistMapper;
    private final PatientProfileMapper patientMapper;
    private final CaCertificateMapper certMapper;
    private final UserSigningKeyMapper keyMapper;

    @GetMapping("/doctors/profile")
    public Result<DoctorProfile> doctor() {
        return Result.ok(doctorMapper.selectOne(new QueryWrapper<DoctorProfile>()
                .eq("user_id", UserContext.currentUserId()).last("limit 1")));
    }

    @GetMapping("/pharmacists/profile")
    public Result<PharmacistProfile> pharmacist() {
        return Result.ok(pharmacistMapper.selectOne(new QueryWrapper<PharmacistProfile>()
                .eq("user_id", UserContext.currentUserId()).last("limit 1")));
    }

    @GetMapping("/patients/profile")
    public Result<PatientProfile> patient() {
        return Result.ok(patientMapper.selectOne(new QueryWrapper<PatientProfile>()
                .eq("user_id", UserContext.currentUserId()).last("limit 1")));
    }

    @GetMapping("/users/me/certificate")
    public Result<Map<String, Object>> myCertificate() {
        long uid = UserContext.currentUserId();
        UserSigningKey key = keyMapper.selectOne(new QueryWrapper<UserSigningKey>()
                .eq("user_id", uid).eq("status", "ACTIVE").last("limit 1"));
        Map<String, Object> data = new HashMap<>();
        if (key == null) {
            data.put("issued", false);
            return Result.ok(data);
        }
        CaCertificate cert = certMapper.selectOne(new QueryWrapper<CaCertificate>()
                .eq("cert_serial", key.getCertSerial()).last("limit 1"));
        data.put("issued", true);
        data.put("certSerial", key.getCertSerial());
        data.put("keyVersion", key.getKeyVersion());
        data.put("notAfter", cert == null ? null : cert.getNotAfter());
        data.put("revoked", cert != null && Integer.valueOf(1).equals(cert.getRevoked()));
        return Result.ok(data);
    }
}
