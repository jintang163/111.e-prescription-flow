package cn.hospital.eph.prescription.web;

import cn.hospital.eph.common.enums.RxStatus;
import cn.hospital.eph.common.security.LoginUser;
import cn.hospital.eph.common.security.RequireRole;
import cn.hospital.eph.common.security.UserContext;
import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.common.web.PageResult;
import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.prescription.api.PrescriptionDtos;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.PrescriptionDiagnosis;
import cn.hospital.eph.prescription.entity.PrescriptionItem;
import cn.hospital.eph.prescription.entity.ReviewRecord;
import cn.hospital.eph.prescription.entity.RxStatusLog;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.pdf.PdfService;
import cn.hospital.eph.prescription.service.PrescriptionService;
import cn.hospital.eph.prescription.service.SignatureVerifyService;
import cn.hospital.eph.prescription.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService rxService;
    private final PrescriptionMapper rxMapper;
    private final SignatureVerifyService verifyService;
    private final PdfService pdfService;
    private final StorageService storage;

    @PostMapping("/prescriptions")
    @RequireRole("DOCTOR")
    public Result<Map<String, Object>> create(@Valid @RequestBody PrescriptionDtos.SaveRequest req) {
        Prescription rx = rxService.createDraft(req, UserContext.get());
        return Result.ok(Map.of("rxNo", rx.getRxNo(), "rxStatus", rx.getRxStatus()));
    }

    @PutMapping("/prescriptions/{rxNo}")
    @RequireRole("DOCTOR")
    public Result<Map<String, Object>> update(@PathVariable String rxNo,
                                              @Valid @RequestBody PrescriptionDtos.SaveRequest req) {
        Prescription rx = rxService.update(rxNo, req, UserContext.get());
        return Result.ok(Map.of("rxNo", rx.getRxNo(), "rxVersion", rx.getRxVersion()));
    }

    @PostMapping("/prescriptions/{rxNo}/submit")
    @RequireRole("DOCTOR")
    public Result<Object> submit(@PathVariable String rxNo,
                                 @RequestBody(required = false) PrescriptionDtos.SubmitRequest req) {
        return Result.ok(rxService.submit(rxNo, UserContext.get()));
    }

    @PostMapping("/prescriptions/{rxNo}/cancel")
    @RequireRole({"DOCTOR", "ADMIN"})
    public Result<Void> cancel(@PathVariable String rxNo,
                               @RequestBody(required = false) Map<String, String> body) {
        LoginUser me = UserContext.get();
        rxService.cancel(rxNo, me, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    @GetMapping("/prescriptions")
    public Result<PageResult<Prescription>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        LoginUser me = UserContext.get();
        QueryWrapper<Prescription> qw = new QueryWrapper<Prescription>().orderByDesc("id");
        switch (me.getRole()) {
            case "DOCTOR" -> qw.eq("doctor_id", me.getUserId());
            case "PATIENT" -> qw.eq("patient_id", me.getUserId());
            case "PHARMACIST" -> qw.in("rx_status",
                    RxStatus.SUBMITTED.name(), RxStatus.REVIEWING.name(),
                    RxStatus.AMENDMENT_REQUESTED.name(), RxStatus.EFFECTIVE.name());
            case "ADMIN" -> {
                if (status != null) qw.eq("rx_status", status);
            }
            default -> throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (status != null && !"ADMIN".equals(me.getRole())) {
            qw.eq("rx_status", status);
        }
        Page<Prescription> p = rxMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(page, size, p.getTotal(), p.getRecords()));
    }

    @GetMapping("/prescriptions/{rxNo}")
    public Result<Map<String, Object>> detail(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        return Result.ok(assemble(rx));
    }

    @GetMapping("/prescriptions/{rxNo}/signatures")
    public Result<List<SignatureRecord>> signatures(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        return Result.ok(rxService.listSignatures(rx.getId()));
    }

    @PostMapping("/verify/signatures/{rxNo}")
    public Result<Map<String, Object>> verify(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        return Result.ok(verifyService.verify(rxNo, rx.getId()));
    }

    @GetMapping("/prescriptions/{rxNo}/logs")
    public Result<List<RxStatusLog>> logs(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        return Result.ok(verifyService.logs(rx.getId()));
    }

    @GetMapping("/prescriptions/{rxNo}/reviews")
    public Result<List<ReviewRecord>> reviews(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        return Result.ok(verifyService.reviews(rx.getId()));
    }

    @GetMapping("/patients/prescriptions/{rxNo}/progress")
    @RequireRole("PATIENT")
    public Result<Map<String, Object>> progress(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        Map<String, Object> data = new HashMap<>();
        data.put("rxNo", rx.getRxNo());
        data.put("rxStatus", rx.getRxStatus());
        data.put("fulfillmentStatus", rx.getFulfillmentStatus());
        data.put("pharmacyName", rx.getCurrentPharmacyName());
        data.put("orderNo", rx.getCurrentOrderNo());
        data.put("effectiveAt", rx.getEffectiveAt());
        data.put("expireAt", rx.getExpireAt());
        data.put("timeline", rxService.listLogs(rx.getId()));
        return Result.ok(data);
    }

    @GetMapping("/prescriptions/{rxNo}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String rxNo) {
        Prescription rx = checkRead(rxNo);
        if (rx.getPdfOssKey() == null || !"UPLOADED".equals(rx.getPdfStatus())) {
            // 容错：现场补生成
            pdfService.generate(rxNo);
            rx = rxService.mustGet(rxNo);
        }
        byte[] bytes = storage.fetch(rx.getPdfOssKey());
        String filename = URLEncoder.encode(rxNo + ".pdf", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    /** 本地存储回读（resolveUrl 指向的内部路径） */
    @GetMapping("/prescriptions/file/**")
    public ResponseEntity<byte[]> file(jakarta.servlet.http.HttpServletRequest request) {
        String key = request.getRequestURI().substring("/api/prescriptions/file/".length());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(storage.fetch(key));
    }

    private Map<String, Object> assemble(Prescription rx) {
        Map<String, Object> data = new HashMap<>();
        data.put("prescription", rx);
        List<PrescriptionItem> items = rxService.listItems(rx.getId());
        List<PrescriptionDiagnosis> diagnoses = rxService.listDiagnoses(rx.getId());
        data.put("items", items);
        data.put("diagnoses", diagnoses);
        data.put("signatures", rxService.listSignatures(rx.getId()));
        data.put("logs", verifyService.logs(rx.getId()));
        data.put("reviews", verifyService.reviews(rx.getId()));
        return data;
    }

    private Prescription checkRead(String rxNo) {
        Prescription rx = rxService.mustGet(rxNo);
        LoginUser me = UserContext.get();
        boolean allowed = switch (me.getRole()) {
            case "DOCTOR" -> rx.getDoctorId().equals(me.getUserId());
            case "PATIENT" -> rx.getPatientId().equals(me.getUserId());
            case "PHARMACIST", "ADMIN" -> true;
            default -> false;
        };
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return rx;
    }
}
