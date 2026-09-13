package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.common.event.Events;

import java.util.List;

/** 药店适配器：对接不同药店协议（Mock / 样例 HTTP / 真实厂商）的统一接口 */
public interface PharmacyAdapter {

    /** 适配器类型，对应 pharmacy.adapter_type */
    String type();

    /** 批量库存校验 */
    StockCheckResult checkStock(AdapterContext ctx, List<Events.RxItem> items);

    /** 库存预占（短时锁定） */
    StockHoldResult hold(AdapterContext ctx, List<Events.RxItem> items);

    /** 下单（幂等键由调用方保证） */
    OrderCreateResult createOrder(AdapterContext ctx, List<Events.RxItem> items, String idempotencyKey);

    /** 主动查询订单状态（对账用） */
    String queryStatus(AdapterContext ctx, String externalOrderNo);
}
