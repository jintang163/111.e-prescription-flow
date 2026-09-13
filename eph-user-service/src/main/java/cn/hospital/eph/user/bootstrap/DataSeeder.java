package cn.hospital.eph.user.bootstrap;

import cn.hospital.eph.common.enums.UserType;
import cn.hospital.eph.user.ca.CaService;
import cn.hospital.eph.user.entity.DoctorProfile;
import cn.hospital.eph.user.entity.PatientProfile;
import cn.hospital.eph.user.entity.PharmacistProfile;
import cn.hospital.eph.user.entity.SysUser;
import cn.hospital.eph.user.mapper.DoctorProfileMapper;
import cn.hospital.eph.user.mapper.PatientProfileMapper;
import cn.hospital.eph.user.mapper.PharmacistProfileMapper;
import cn.hospital.eph.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 根 CA 初始化 + 演示种子数据（幂等） */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final CaService caService;
    private final SysUserMapper userMapper;
    private final DoctorProfileMapper doctorMapper;
    private final PharmacistProfileMapper pharmacistMapper;
    private final PatientProfileMapper patientMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        caService.ensureRootCa();
        seed();
    }

    private void seed() {
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", "admin")) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        SysUser admin = user("admin", "admin123", "系统管理员", UserType.ADMIN, null);
        SysUser doctor = user("doctor", "doctor123", "张明华", UserType.DOCTOR, "13800000001");
        SysUser pharmacist = user("pharmacist", "pharma123", "李审方", UserType.PHARMACIST, "13800000002");
        SysUser patient = user("patient", "patient123", "王患者", UserType.PATIENT, "13900000001");
        for (SysUser u : new SysUser[]{admin, doctor, pharmacist, patient}) {
            userMapper.insert(u);
        }

        DoctorProfile dp = new DoctorProfile();
        dp.setUserId(doctor.getId());
        dp.setLicenseNo("110101199001011234");
        dp.setTitle("主治医师");
        dp.setDeptCode("CARD");
        dp.setDeptName("心血管内科");
        dp.setHospitalName("互联网医院");
        dp.setPracticeScope("内科专业");
        dp.setLicenseStatus(1);
        dp.setQualifiedAt(LocalDate.of(2015, 7, 1));
        dp.setCreatedAt(now);
        doctorMapper.insert(dp);

        PharmacistProfile pp = new PharmacistProfile();
        pp.setUserId(pharmacist.getId());
        pp.setLicenseNo("20180101PH00001");
        pp.setTitle("主管药师");
        pp.setPharmacyDept("门诊药房");
        pp.setLicenseStatus(1);
        pp.setCreatedAt(now);
        pharmacistMapper.insert(pp);

        PatientProfile pat = new PatientProfile();
        pat.setUserId(patient.getId());
        pat.setName("王患者");
        pat.setGender(1);
        pat.setBirthDate(LocalDate.of(1985, 3, 12));
        pat.setPhone("13900000001");
        pat.setAllergyHistory("青霉素过敏");
        pat.setDefaultAddress("北京市朝阳区示例路 1 号");
        pat.setCreatedAt(now);
        patientMapper.insert(pat);

        // 医生/药师默认签发证书
        caService.issueUserCertificate(doctor.getId(), doctor.getRealName(), "DOCTOR");
        caService.issueUserCertificate(pharmacist.getId(), pharmacist.getRealName(), "PHARMACIST");
        log.info("种子数据完成: admin/admin123 doctor/doctor123 pharmacist/pharma123 patient/patient123");
    }

    private SysUser user(String username, String rawPwd, String realName, UserType type, String phone) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPwd));
        u.setRealName(realName);
        u.setUserType(type.code());
        u.setPhone(phone);
        u.setIdCardMask("1101**********0000");
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }
}
