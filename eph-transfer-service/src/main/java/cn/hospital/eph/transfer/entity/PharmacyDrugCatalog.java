package cn.hospital.eph.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pharmacy_drug_catalog")
public class PharmacyDrugCatalog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pharmacyId;
    private String drugCode;
    private String skuCode;
    private BigDecimal price;
    private Integer availableStock;
    private LocalDateTime stockSyncedAt;
}
