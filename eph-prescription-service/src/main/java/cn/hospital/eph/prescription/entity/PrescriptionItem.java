package cn.hospital.eph.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("prescription_item")
public class PrescriptionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rxId;
    private Integer seq;
    private String drugCode;
    private String drugName;
    private String spec;
    private String dosageForm;
    private BigDecimal qty;
    private String unit;
    private String singleDose;
    private String doseUnit;
    private String frequency;
    private String administrationRoute;
    private Integer days;
    private Integer skinTestFlag;
    private String remark;
}
