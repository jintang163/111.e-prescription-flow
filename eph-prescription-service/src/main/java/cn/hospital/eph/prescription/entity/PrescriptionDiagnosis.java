package cn.hospital.eph.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("prescription_diagnosis")
public class PrescriptionDiagnosis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rxId;
    private Integer seq;
    private String icd10Code;
    private String diagnosisName;
}
