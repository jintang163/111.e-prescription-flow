package cn.hospital.eph.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pharmacist_profile")
public class PharmacistProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String licenseNo;
    private String title;
    private String pharmacyDept;
    private Integer licenseStatus;
    private LocalDateTime createdAt;
}
