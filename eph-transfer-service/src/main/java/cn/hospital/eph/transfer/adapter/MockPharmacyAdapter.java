package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.transfer.entity.Pharmacy;
import cn.hospital.eph.transfer.entity.PharmacyDrugCatalog;
import cn.hospital.eph.transfer.mapper.PharmacyDrugCatalogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Mock 药店：基于本地库存表判断库存并模拟预占/下单，零外部依赖演示全链路 */
@Component
@RequiredArgsConstructor
public class MockPharmacyAdapter implements PharmacyAdapter {

    private final PharmacyDrugCatalogMapper catalogMapper;

    @Override
    public String type() {
        return "MOCK";
    }

    @Override
    public StockCheckResult checkStock(AdapterContext ctx, List<Events.RxItem> items) {
        List<String> shortage = new ArrayList<>();
        for (Events.RxItem item : items) {
            Integer stock = stock(ctx.pharmacy().getId(), item.drugCode());
            int need = parseQty(item.qty());
            if (stock == null || stock < need) {
                shortage.add(item.drugName() + "(需" + need + "/有" + (stock == null ? 0 : stock) + ")");
            }
        }
        return shortage.isEmpty() ? StockCheckResult.ok()
                : StockCheckResult.out("缺货: " + String.join("、", shortage));
    }

    @Override
    public StockHoldResult hold(AdapterContext ctx, List<Events.RxItem> items) {
        // Mock：预占即扣减库存（真实药店持 holdNo 短时锁定）
        for (Events.RxItem item : items) {
            PharmacyDrugCatalog row = catalog(ctx.pharmacy().getId(), item.drugCode());
            if (row == null) {
                return new StockHoldResult(false, null, "库存记录缺失: " + item.drugCode());
            }
            int left = row.getAvailableStock() - parseQty(item.qty());
            if (left < 0) {
                return new StockHoldResult(false, null, "库存不足: " + item.drugName());
            }
            row.setAvailableStock(left);
            catalogMapper.updateById(row);
        }
        return new StockHoldResult(true, "HOLD-" + ctx.orderNo(), "预占成功");
    }

    @Override
    public OrderCreateResult createOrder(AdapterContext ctx, List<Events.RxItem> items, String idempotencyKey) {
        return new OrderCreateResult(true, "EXT-" + ctx.orderNo(), "下单成功");
    }

    @Override
    public String queryStatus(AdapterContext ctx, String externalOrderNo) {
        // Mock 订单状态由管理推进接口/回调驱动，无外部查询
        return null;
    }

    private Integer stock(long pharmacyId, String drugCode) {
        PharmacyDrugCatalog row = catalog(pharmacyId, drugCode);
        return row == null ? null : row.getAvailableStock();
    }

    private PharmacyDrugCatalog catalog(long pharmacyId, String drugCode) {
        return catalogMapper.selectOne(new QueryWrapper<PharmacyDrugCatalog>()
                .eq("pharmacy_id", pharmacyId).eq("drug_code", drugCode).last("limit 1"));
    }

    private int parseQty(String qty) {
        try {
            return new java.math.BigDecimal(qty).intValue();
        } catch (Exception e) {
            return 1;
        }
    }
}
