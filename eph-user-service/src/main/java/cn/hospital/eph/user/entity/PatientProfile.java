package cn.hospital.eph.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("patient_profile")
public class PatientProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String allergyHistory;
    private String defaultAddress;
    private LocalDateTime createdAt;
}
