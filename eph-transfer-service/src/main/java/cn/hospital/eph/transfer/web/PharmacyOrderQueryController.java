package cn.hospital.eph.transfer.web;

import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.transfer.entity.PharmacyOrder;
import cn.hospital.eph.transfer.entity.PharmacyOrderLog;
import cn.hospital.eph.transfer.mapper.PharmacyOrderLogMapper;
import cn.hospital.eph.transfer.mapper.PharmacyOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PharmacyOrderQueryController {

    private final PharmacyOrderMapper orderMapper;
    private final PharmacyOrderLogMapper logMapper;

    @GetMapping("/pharmacy-orders/{rxNo}")
    public Result<List<Map<String, Object>>> byRx(@PathVariable String rxNo) {
        List<PharmacyOrder> orders = orderMapper.selectList(new QueryWrapper<PharmacyOrder>()
                .eq("rx_no", rxNo).orderByDesc("id"));
        List<Map<String, Object>> result = orders.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("order", o);
            m.put("logs", logMapper.selectList(new QueryWrapper<PharmacyOrderLog>()
                    .eq("order_no", o.getOrderNo()).orderByAsc("id")));
            return m;
        }).toList();
        return Result.ok(result);
    }
}
