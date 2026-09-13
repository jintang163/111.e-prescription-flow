package cn.hospital.eph.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rx_process_binding")
public class RxProcessBinding {
    @TableId
    private String rxNo;
    private Integer rxVersion;
    private String processInstanceId;
    private String currentTaskId;
    private String currentNode;
    private Long candidatePharmacist;
    /** RUNNING / APPROVED / REJECTED / AMENDING */
    private String status;
    private LocalDateTime updatedAt;
}
