package cn.hospital.eph.transfer.seeder;

import cn.hospital.eph.transfer.entity.Pharmacy;
import cn.hospital.eph.transfer.entity.PharmacyDrugCatalog;
import cn.hospital.eph.transfer.mapper.PharmacyDrugCatalogMapper;
import cn.hospital.eph.transfer.mapper.PharmacyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 演示药店与库存种子（幂等） */
@Slf4j
@Component
@RequiredArgsConstructor
public class PharmacySeeder implements ApplicationRunner {

    private final PharmacyMapper pharmacyMapper;
    private final PharmacyDrugCatalogMapper catalogMapper;

    @Override
    public void run(ApplicationArguments args) {
        seed("PHARM01", "仁心连锁药房（中心店）", 10,
                new String[][]{{"DRUG-AMOX", "200"}, {"DRUG-AMLOD", "150"}, {"DRUG-METFORMIN", "80"}});
        seed("PHARM02", "康邻智慧药房", 20,
                new String[][]{{"DRUG-AMOX", "0"}, {"DRUG-AMLOD", "60"}, {"DRUG-METFORMIN", "120"}});
    }

    private void seed(String code, String name, int priority, String[][] stock) {
        Pharmacy p = pharmacyMapper.selectOne(new QueryWrapper<Pharmacy>().eq("code", code));
        if (p == null) {
            p = new Pharmacy();
            p.setCode(code);
            p.setName(name);
            p.setAdapterType("MOCK");
            p.setAuthType("HMAC");
            p.setAppKey("app-" + code.toLowerCase());
            p.setSignSecret("secret-" + code.toLowerCase());
            p.setPriority(priority);
            p.setStatus(1);
            p.setCreatedAt(LocalDateTime.now());
            pharmacyMapper.insert(p);
            for (String[] s : stock) {
                PharmacyDrugCatalog c = new PharmacyDrugCatalog();
                c.setPharmacyId(p.getId());
                c.setDrugCode(s[0]);
                c.setSkuCode("SKU-" + s[0]);
                c.setPrice(new BigDecimal("25.00"));
                c.setAvailableStock(Integer.parseInt(s[1]));
                c.setStockSyncedAt(LocalDateTime.now());
                catalogMapper.insert(c);
            }
            log.info("种子药店完成: {} ({})", code, name);
        }
    }
}
