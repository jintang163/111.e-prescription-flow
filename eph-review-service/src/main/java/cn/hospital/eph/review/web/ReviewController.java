package cn.hospital.eph.review.web;

import cn.hospital.eph.common.security.LoginUser;
import cn.hospital.eph.common.security.RequireRole;
import cn.hospital.eph.common.security.UserContext;
import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.review.service.ReviewFlowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewFlowService flowService;

    @Data
    public static class ApproveRequest {
        private String comment;
    }

    @Data
    public static class RejectRequest {
        @NotBlank
        private String reason;
    }

    @Data
    public static class AmendmentRequest {
        @NotBlank
        private String comment;
    }

    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> tasks(@RequestParam(defaultValue = "pharmacist") String type,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        LoginUser me = UserContext.get();
        if ("doctor-amend".equals(type) && !"DOCTOR".equals(me.getRole())) {
            me = UserContext.get();
        }
        return Result.ok(flowService.todo(type, me, page, size));
    }

    @GetMapping("/tasks/count")
    public Result<Map<String, Long>> count() {
        LoginUser me = UserContext.get();
        return Result.ok(Map.of(
                "pharmacist", flowService.todoCount("pharmacist", me),
                "doctorAmend", flowService.todoCount("doctor-amend", me)));
    }

    @PostMapping("/tasks/{rxNo}/approve")
    @RequireRole("PHARMACIST")
    public Result<Void> approve(@PathVariable String rxNo,
                                @RequestBody(required = false) ApproveRequest req) {
        flowService.approve(rxNo, UserContext.get(), req == null ? null : req.getComment());
        return Result.ok();
    }

    @PostMapping("/tasks/{rxNo}/reject")
    @RequireRole("PHARMACIST")
    public Result<Void> reject(@PathVariable String rxNo, @Valid @RequestBody RejectRequest req) {
        flowService.reject(rxNo, UserContext.get(), req.getReason());
        return Result.ok();
    }

    @PostMapping("/tasks/{rxNo}/request-amendment")
    @RequireRole("PHARMACIST")
    public Result<Void> amendment(@PathVariable String rxNo, @Valid @RequestBody AmendmentRequest req) {
        flowService.requestAmendment(rxNo, UserContext.get(), req.getComment());
        return Result.ok();
    }
}
