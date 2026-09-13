package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.transfer.entity.Pharmacy;
import cn.hospital.eph.transfer.entity.PharmacyDrugCatalog;
import cn.hospital.eph.transfer.entity.PharmacyOrder;
import cn.hospital.eph.transfer.mapper.PharmacyDrugCatalogMapper;
import cn.hospital.eph.transfer.mapper.PharmacyOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mock 药店适配器状态测试：库存校验/预占/下单/对账查询 */
class MockPharmacyAdapterTest {

    private PharmacyDrugCatalogMapper catalogMapper;
    private PharmacyOrderMapper orderMapper;
    private MockPharmacyAdapter adapter;
    private Pharmacy pharmacy;

    @BeforeEach
    void setUp() {
        catalogMapper = mock(PharmacyDrugCatalogMapper.class);
        orderMapper = mock(PharmacyOrderMapper.class);
        adapter = new MockPharmacyAdapter(catalogMapper, orderMapper);
        pharmacy = new Pharmacy();
        pharmacy.setId(7L);
        pharmacy.setCode("MOCK01");
        pharmacy.setName("Mock 药店");
    }

    private AdapterContext ctx() {
        return AdapterContext.of(pharmacy, "RX-1", 1, "PO-1");
    }

    private static Events.RxItem item(String drugCode, String drugName, String qty) {
        return new Events.RxItem(drugCode, drugName, "0.25g*24粒", "胶囊", qty,
                "盒", "0.5g", "tid", "po", 3, 0);
    }

    private static PharmacyDrugCatalog catalogRow(String drugCode, int stock) {
        PharmacyDrugCatalog row = new PharmacyDrugCatalog();
        row.setId(1L);
        row.setPharmacyId(7L);
        row.setDrugCode(drugCode);
        row.setAvailableStock(stock);
        return row;
    }

    // ---------- checkStock ----------

    @Test
    void checkStock_allAvailable() {
        when(catalogMapper.selectOne(any())).thenReturn(catalogRow("D001", 10));
        StockCheckResult r = adapter.checkStock(ctx(), List.of(item("D001", "阿莫西林", "2")));
        assertTrue(r.allAvailable());
    }

    @Test
    void checkStock_shortageReportsNeedAndHave() {
        when(catalogMapper.selectOne(any())).thenReturn(catalogRow("D001", 1));
        StockCheckResult r = adapter.checkStock(ctx(), List.of(item("D001", "阿莫西林", "3")));
        assertFalse(r.allAvailable());
        assertTrue(r.detail().contains("需3/有1"), r.detail());
    }

    @Test
    void checkStock_missingCatalogRowTreatedAsZero() {
        when(catalogMapper.selectOne(any())).thenReturn(null);
        StockCheckResult r = adapter.checkStock(ctx(), List.of(item("D404", "未知药", "1")));
        assertFalse(r.allAvailable());
        assertTrue(r.detail().contains("有0"), r.detail());
    }

    // ---------- hold ----------

    @Test
    void hold_deductsStockAndReturnsHoldNo() {
        PharmacyDrugCatalog row = catalogRow("D001", 5);
        when(catalogMapper.selectOne(any())).thenReturn(row);
        StockHoldResult r = adapter.hold(ctx(), List.of(item("D001", "阿莫西林", "2")));
        assertTrue(r.success());
        assertEquals("HOLD-PO-1", r.holdNo());
        assertEquals(3, row.getAvailableStock());
        verify(catalogMapper).updateById(row);
    }

    @Test
    void hold_insufficientStockFailsWithoutUpdate() {
        when(catalogMapper.selectOne(any())).thenReturn(catalogRow("D001", 1));
        StockHoldResult r = adapter.hold(ctx(), List.of(item("D001", "阿莫西林", "2")));
        assertFalse(r.success());
        assertNull(r.holdNo());
        verify(catalogMapper, never()).updateById(any(PharmacyDrugCatalog.class));
    }

    @Test
    void hold_missingCatalogRowFails() {
        when(catalogMapper.selectOne(any())).thenReturn(null);
        StockHoldResult r = adapter.hold(ctx(), List.of(item("D404", "未知药", "1")));
        assertFalse(r.success());
        assertTrue(r.detail().contains("D404"), r.detail());
    }

    // ---------- createOrder ----------

    @Test
    void createOrder_returnsExternalOrderNoDerivedFromOrderNo() {
        OrderCreateResult r = adapter.createOrder(ctx(), List.of(item("D001", "阿莫西林", "1")), "idem-1");
        assertTrue(r.success());
        assertEquals("EXT-PO-1", r.externalOrderNo());
    }

    // ---------- queryStatus（对账） ----------

    @Test
    void queryStatus_returnsCurrentStatusOfLocalMockOrder() {
        PharmacyOrder order = new PharmacyOrder();
        order.setPharmacyId(7L);
        order.setExternalOrderNo("EXT-PO-1");
        order.setStatus("DISPENSING");
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertEquals("DISPENSING", adapter.queryStatus(ctx(), "EXT-PO-1"));
    }

    @Test
    void queryStatus_unknownExternalOrderNoReturnsNull() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertNull(adapter.queryStatus(ctx(), "EXT-PO-404"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void queryStatus_scopesLookupByPharmacyAndExternalOrderNo() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        adapter.queryStatus(ctx(), "EXT-PO-1");

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectOne(captor.capture());
        QueryWrapper<?> wrapper = captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("pharmacy_id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getSqlSegment().contains("external_order_no"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("EXT-PO-1"));
    }
}
