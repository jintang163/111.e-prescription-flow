package cn.hospital.eph.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("doctor_profile")
public class DoctorProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String licenseNo;
    private String title;
    private String deptCode;
    private String deptName;
    private String hospitalName;
    private String practiceScope;
    private Integer licenseStatus;
    private LocalDate qualifiedAt;
    private LocalDateTime createdAt;
}
