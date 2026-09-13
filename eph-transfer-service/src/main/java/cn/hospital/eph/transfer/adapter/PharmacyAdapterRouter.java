package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.transfer.entity.Pharmacy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按药店 adapter_type 选择适配器 */
@Component
public class PharmacyAdapterRouter {

    private final Map<String, PharmacyAdapter> adapters;

    public PharmacyAdapterRouter(List<PharmacyAdapter> all) {
        this.adapters = all.stream().collect(Collectors.toMap(PharmacyAdapter::type, Function.identity()));
    }

    public PharmacyAdapter route(Pharmacy pharmacy) {
        PharmacyAdapter adapter = adapters.get(pharmacy.getAdapterType());
        if (adapter == null) {
            throw new BizException(ErrorCode.SYS_ERROR, "未支持的药店适配器: " + pharmacy.getAdapterType());
        }
        return adapter;
    }
}
