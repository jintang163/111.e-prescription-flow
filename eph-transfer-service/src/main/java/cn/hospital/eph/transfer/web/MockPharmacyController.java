package cn.hospital.eph.transfer.web;

import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.transfer.entity.PharmacyOrder;
import cn.hospital.eph.transfer.mapper.PharmacyOrderMapper;
import cn.hospital.eph.transfer.service.OrderDispatchService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mock 药店专用：推进订单到下一履约状态（演示/E2E 用，经网关携带登录态） */
@RestController
@RequestMapping("/api/internal/mock-pharmacy")
@RequiredArgsConstructor
public class MockPharmacyController {

    private final PharmacyOrderMapper orderMapper;
    private final OrderDispatchService dispatchService;

    @PostMapping("/advance")
    public Result<PharmacyOrder> advance(@RequestParam String rxNo) {
        PharmacyOrder order = orderMapper.selectOne(new QueryWrapper<PharmacyOrder>()
                .eq("rx_no", rxNo).orderByDesc("id").last("limit 1"));
        if (order == null) {
            return Result.fail(40400, "处方无药店订单: " + rxNo);
        }
        dispatchService.advanceMockOrder(order.getOrderNo());
        return Result.ok(orderMapper.selectById(order.getId()));
    }
}
