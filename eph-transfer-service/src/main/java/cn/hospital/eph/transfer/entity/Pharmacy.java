package cn.hospital.eph.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pharmacy")
public class Pharmacy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String adapterType;
    private String baseUrl;
    private String authType;
    private String appKey;
    private String signSecret;
    private Integer priority;
    private Integer status;
    private LocalDateTime createdAt;
}
