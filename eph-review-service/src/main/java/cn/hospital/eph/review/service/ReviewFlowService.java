package cn.hospital.eph.review.service;

import cn.hospital.eph.common.canonical.CanonicalPayloadBuilder;
import cn.hospital.eph.common.canonical.CanonicalRx;
import cn.hospital.eph.common.enums.ReviewDecision;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.common.mq.OutboxService;
import cn.hospital.eph.common.security.InternalSignClient;
import cn.hospital.eph.common.security.LoginUser;
import cn.hospital.eph.common.security.SignGrantService;
import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.review.client.PrescriptionContextClient;
import cn.hospital.eph.review.entity.RxProcessBinding;
import cn.hospital.eph.review.mapper.RxProcessBindingMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Flowable 审方编排：启动流程、药师三审决（通过=药师签名）、医生补正循环、待办查询 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewFlowService {

    public static final String PROCESS_KEY = "rxReviewProcess";
    public static final String GROUP_PHARMACIST = "PHARMACIST";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RxProcessBindingMapper bindingMapper;
    private final OutboxService outboxService;
    private final SignGrantService signGrantService;
    private final InternalSignClient signClient;
    private final PrescriptionContextClient contextClient;

    /** rx.submitted：启动新审方流程实例 */
    @Transactional
    public void startReview(Events.RxSubmitted s) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("rxNo", s.rxNo());
        vars.put("rxVersion", s.rxVersion());
        vars.put("doctorId", String.valueOf(s.doctorId()));
        vars.put("doctorName", s.doctorName());
        vars.put("patientName", s.patientName());
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY, s.rxNo(), vars);

        RxProcessBinding b = new RxProcessBinding();
        b.setRxNo(s.rxNo());
        b.setRxVersion(s.rxVersion());
        b.setProcessInstanceId(pi.getProcessInstanceId());
        Task task = taskService.createTaskQuery().processInstanceId(pi.getProcessInstanceId()).singleResult();
        b.setCurrentTaskId(task == null ? null : task.getId());
        b.setCurrentNode("pharmacistReview");
        b.setStatus("RUNNING");
        b.setUpdatedAt(LocalDateTime.now());
        bindingMapper.insert(b);
        log.info("审方流程启动 rxNo={} version={} instance={}", s.rxNo(), s.rxVersion(), pi.getProcessInstanceId());
    }

    /** rx.resubmitted：医生补正重签后，推进 doctorAmend 节点回到药师审核（同实例循环） */
    @Transactional
    public void resumeAfterAmendment(Events.RxSubmitted s) {
        RxProcessBinding b = binding(s.rxNo());
        Task amendTask = taskService.createTaskQuery()
                .processInstanceId(b.getProcessInstanceId())
                .taskCandidateUser(String.valueOf(s.doctorId()))
                .singleResult();
        if (amendTask == null) {
            // 幂等：流程可能已回到药师节点
            Task reviewTask = taskService.createTaskQuery()
                    .processInstanceId(b.getProcessInstanceId())
                    .taskCandidateGroup(GROUP_PHARMACIST).singleResult();
            if (reviewTask != null) {
                return;
            }
            throw new BizException(ErrorCode.RX_STATE_ILLEGAL, "未找到补正任务");
        }
        taskService.complete(amendTask.getId(), Map.of("rxVersion", s.rxVersion()));
        Task next = taskService.createTaskQuery().processInstanceId(b.getProcessInstanceId()).singleResult();
        b.setRxVersion(s.rxVersion());
        b.setCurrentTaskId(next == null ? null : next.getId());
        b.setCurrentNode("pharmacistReview");
        b.setStatus("RUNNING");
        b.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(b);
        log.info("补正后重新进入审核 rxNo={} version={}", s.rxNo(), s.rxVersion());
    }

    @Transactional
    public void approve(String rxNo, LoginUser pharmacist, String comment) {
        Task task = claimReviewTask(rxNo, pharmacist);
        signGrantService.requireAndConsume(pharmacist.getUserId());

        // 取当前版本医生签名原文，附加审核声明后药师签名（与医生签同一份处方内容）
        Map<String, Object> ctx = contextClient.signingContext(rxNo);
        int rxVersion = ((Number) ctx.get("rxVersion")).intValue();
        String doctorCanonical = (String) ctx.get("canonicalPayload");
        String reviewedAt = OffsetDateTime.now(ZoneOffset.ofHours(8)).toString();
        CanonicalRx.ReviewDeclaration declaration =
                new CanonicalRx.ReviewDeclaration(ReviewDecision.APPROVED.name(), comment, reviewedAt);
        String pharmacistCanonical = CanonicalPayloadBuilder.buildWithReview(doctorCanonical, declaration);

        InternalSignClient.SignResult sr = signClient.sign(pharmacist.getUserId(), pharmacistCanonical);

        taskService.complete(task.getId(), Map.of(
                "result", ReviewDecision.APPROVED.name(),
                "comment", comment == null ? "" : comment,
                "pharmacistId", String.valueOf(pharmacist.getUserId()),
                "pharmacistName", pharmacist.getRealName()));

        RxProcessBinding b = binding(rxNo);
        b.setStatus(ReviewDecision.APPROVED.name());
        b.setCurrentNode("endApproved");
        b.setCurrentTaskId(null);
        b.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(b);

        Events.RxReviewDecided event = new Events.RxReviewDecided(
                rxNo, rxVersion, ReviewDecision.APPROVED.name(), comment,
                pharmacist.getUserId(), pharmacist.getRealName(),
                sr.certSerial(), sr.certPem(), sr.signatureBase64(),
                doctorCanonical, pharmacistCanonical,
                CanonicalPayloadBuilder.sha256(doctorCanonical), reviewedAt);
        outboxService.enlist(MqTopology.RK_RX_APPROVED, rxNo, null, event);
        log.info("药师审核通过并签名 rxNo={} pharmacist={}", rxNo, pharmacist.getRealName());
    }

    @Transactional
    public void reject(String rxNo, LoginUser pharmacist, String reason) {
        Task task = claimReviewTask(rxNo, pharmacist);
        decideWithoutSignature(rxNo, task, ReviewDecision.REJECTED, reason, pharmacist,
                MqTopology.RK_RX_REJECTED, "endRejected");
    }

    @Transactional
    public void requestAmendment(String rxNo, LoginUser pharmacist, String comment) {
        Task task = claimReviewTask(rxNo, pharmacist);
        Map<String, Object> ctx = contextClient.signingContext(rxNo);
        int rxVersion = ((Number) ctx.get("rxVersion")).intValue();

        taskService.complete(task.getId(), Map.of(
                "result", ReviewDecision.AMENDMENT.name(),
                "comment", comment,
                "pharmacistId", String.valueOf(pharmacist.getUserId()),
                "pharmacistName", pharmacist.getRealName()));

        RxProcessBinding b = binding(rxNo);
        Task amendTask = taskService.createTaskQuery().processInstanceId(b.getProcessInstanceId()).singleResult();
        b.setCurrentTaskId(amendTask == null ? null : amendTask.getId());
        b.setCurrentNode("doctorAmend");
        b.setStatus("AMENDING");
        b.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(b);

        Events.RxReviewDecided event = new Events.RxReviewDecided(
                rxNo, rxVersion, ReviewDecision.AMENDMENT.name(), comment,
                pharmacist.getUserId(), pharmacist.getRealName(),
                null, null, null,
                (String) ctx.get("canonicalPayload"), null,
                (String) ctx.get("payloadSha256"),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        outboxService.enlist(MqTopology.RK_RX_AMENDMENT_REQUESTED, rxNo, null, event);
        log.info("药师要求补正 rxNo={} comment={}", rxNo, comment);
    }

    private void decideWithoutSignature(String rxNo, Task task, ReviewDecision decision, String comment,
                                        LoginUser pharmacist, String routingKey, String endNode) {
        Map<String, Object> ctx = contextClient.signingContext(rxNo);
        int rxVersion = ((Number) ctx.get("rxVersion")).intValue();
        taskService.complete(task.getId(), Map.of(
                "result", decision.name(),
                "comment", comment,
                "pharmacistId", String.valueOf(pharmacist.getUserId()),
                "pharmacistName", pharmacist.getRealName()));
        RxProcessBinding b = binding(rxNo);
        b.setStatus(decision.name());
        b.setCurrentNode(endNode);
        b.setCurrentTaskId(null);
        b.setUpdatedAt(LocalDateTime.now());
        bindingMapper.updateById(b);

        Events.RxReviewDecided event = new Events.RxReviewDecided(
                rxNo, rxVersion, decision.name(), comment,
                pharmacist.getUserId(), pharmacist.getRealName(),
                null, null, null,
                (String) ctx.get("canonicalPayload"), null,
                (String) ctx.get("payloadSha256"),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        outboxService.enlist(routingKey, rxNo, null, event);
    }

    public List<Map<String, Object>> todo(String type, LoginUser user, long page, long size) {
        TaskQuery query;
        if ("doctor-amend".equals(type)) {
            query = taskService.createTaskQuery().taskCandidateUser(String.valueOf(user.getUserId()));
        } else {
            query = taskService.createTaskQuery().taskCandidateGroup(GROUP_PHARMACIST);
        }
        List<Task> tasks = query.orderByTaskCreateTime().desc()
                .listPage((int) ((page - 1) * size), (int) size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> vars = runtimeService.getVariables(t.getExecutionId());
            Map<String, Object> row = new HashMap<>();
            row.put("taskId", t.getId());
            row.put("rxNo", vars.get("rxNo"));
            row.put("node", t.getTaskDefinitionKey());
            row.put("createTime", t.getCreateTime());
            row.put("rxVersion", vars.get("rxVersion"));
            row.put("doctorName", vars.get("doctorName"));
            row.put("patientName", vars.get("patientName"));
            result.add(row);
        }
        return result;
    }

    public long todoCount(String type, LoginUser user) {
        if ("doctor-amend".equals(type)) {
            return taskService.createTaskQuery().taskCandidateUser(String.valueOf(user.getUserId())).count();
        }
        return taskService.createTaskQuery().taskCandidateGroup(GROUP_PHARMACIST).count();
    }

    private Task claimReviewTask(String rxNo, LoginUser pharmacist) {
        RxProcessBinding b = binding(rxNo);
        Task task = taskService.createTaskQuery()
                .processInstanceId(b.getProcessInstanceId())
                .taskCandidateGroup(GROUP_PHARMACIST)
                .singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.RX_STATE_ILLEGAL, "处方当前不在药师审核节点");
        }
        taskService.claim(task.getId(), String.valueOf(pharmacist.getUserId()));
        return task;
    }

    private RxProcessBinding binding(String rxNo) {
        RxProcessBinding b = bindingMapper.selectOne(new QueryWrapper<RxProcessBinding>().eq("rx_no", rxNo));
        if (b == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审方流程不存在: " + rxNo);
        }
        return b;
    }
}
