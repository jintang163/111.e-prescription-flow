# 互联网医院电子处方流转系统

视频问诊 → 医生开方 → 药师审方 → **医生/药师数字双签生效** → 签章处方 PDF（OSS）→ 推送合作药店（库存校验 → 下单 → 状态回传）→ 患者查询取药进度的端到端闭环。

## 技术架构

```
web-admin(Vue3+ElementPlus)  app-patient(UniApp H5/小程序)
                     │ JWT
              [api-gateway:8080]  Spring Cloud Gateway，静态路由 + JWT 校验
 ┌────────────┬──────────────┬───────────────┬──────────────┐
 user :8081   prescription :8082  review :8083   transfer :8084
 用户/资质/CA  处方/签名/PDF     Flowable 审方    药店适配/回调
              └──── RabbitMQ 事件驱动（服务间零同步调用，Feign/Nacos 均未引入）────┘
                       MySQL 8（4 schema） · Redis · 阿里云 OSS（本地文件默认实现）
```

| 组件 | 选型 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3.5、Spring Cloud Gateway 2023.0.3、Flowable 7.1.0、MyBatis-Plus 3.5.7、Flyway |
| 签名/存储 | SHA256withRSA（内置 X.509 模拟 CA）、OpenPDF 1.3.43 + BouncyCastle（PAdES/PKCS#7 双签章）、本地 FS / 阿里云 OSS 接口 |
| 消息 | RabbitMQ：topic `eph.rx` + 死信队列；Transactional Outbox + publisher confirm + inbox 幂等 |
| 前端 | Vue3/Vite/Element Plus/Pinia；UniApp(Vue3) H5 与微信小程序 |
| 药店 | 适配器模式（MOCK / 可扩展 SAMPLE_HTTP），HMAC 回调验签 + nonce 防重放 + 状态码映射 + 对账扩展点 |

## 处方状态机

`DRAFT → SUBMITTED → REVIEWING → EFFECTIVE → DISPATCHING → DISPATCHED → FULFILLING → DISPENSED → READY_FOR_PICKUP → PICKED_UP`

分支：`REJECTED`（终态）、`AMENDMENT_REQUESTED ↔ REVIEWING`（补正循环，版本 +1，旧签名置 SUPERSEDED）、`TRANSFER_FAILED`（全店缺货待改派）、`CANCELLED`。

## 合规设计要点

- **双签语义**：医生提交审方时对 canonical 处方快照签名（签发）；药师"通过"动作本身对同版本快照 + 审核声明签名；两签齐备本事务内置 `EFFECTIVE`，补正升版本后旧签全部作废重签。
- **签名载荷规范化**：字段白名单、key 字典序、无空白、null 剔除、ISO-8601、明细按 seq；原文与 SHA-256 落库存证，提供重新验签接口。
- **CA 与密钥**：首启生成 RSA4096 根 CA（私钥落 `eph-keys/`，生产替换 KMS/HSM）；用户 RSA2048 证书含 digitalSignature/nonRepudiation；私钥主密钥 AES-GCM 包裹入库；签名前必须**密码重认证**换取 5 分钟一次性 sign-grant。
- **PDF 双数字签章**：渲染中文章节式处方后，医生/药师两次追加 PKCS#7 detached 签章（Adobe Acrobat 可验证），含签章矩形区与证书链。
- **消息可靠性**：业务与 outbox 同事务，relay 定时 + confirm 标记；消费端 inbox 唯一键 + 状态机单调守卫；乱序消息按链逐跳推进。

## 目录结构

```
eph-common/              公共：Result/异常/JWT/角色切面/枚举/CA工具/MQ拓扑/事件契约/outbox/inbox/canonical
eph-user-service/        登录、资质、CA/证书/托管密钥、内部签名与 PDF 签章接口、种子用户
eph-prescription-service/开方、签名提交、审方事件消费（双签生效）、PDF 渲染/存储、验签、患者进度
eph-review-service/      Flowable BPMN（通过/驳回/补正循环）、待办、药师签名
eph-transfer-service/    药店适配器、派单（库存→预占→下单）、HMAC 回调、Mock 推进、种子药店
eph-gateway/             8080 统一入口
web-admin/               医生/药师/管理端
app-patient/             患者端 UniApp
deploy/                  docker-compose、init.sql、e2e.sh
```

## 快速开始

### 1. 基础设施（任选一种）

```bash
# 方式 A：docker compose
cd deploy && docker compose up -d          # MySQL8 / Redis7 / RabbitMQ3.13(管理台 15672)

# 方式 B：无 Docker 的用户态便携中间件见（仅开发机参考，仓库外）
```

### 2. 后端（JDK17 + Maven 3.9）

```bash
mvn clean install -Dskip-tests
# 依次启动（每个目录）
mvn -pl eph-user-service         spring-boot:run
mvn -pl eph-prescription-service spring-boot:run
mvn -pl eph-review-service       spring-boot:run
mvn -pl eph-transfer-service     spring-boot:run
mvn -pl eph-gateway              spring-boot:run
```

首次启动自动：Flyway 建表、Flowable 建 ACT_* 表、生成根 CA、写入演示用户与药店。

### 3. 前端

```bash
cd web-admin && npm install && npm run dev      # http://localhost:5173 → :8080
cd app-patient && npm install && npm run dev:h5 # http://localhost:5174
```

### 演示账号

| 角色 | 账号/密码 |
|---|---|
| 医生 张明华 | `doctor / doctor123` |
| 药师 李审方 | `pharmacist / pharma123` |
| 患者 王患者 | `patient / patient123`（患者端手机号 13900000001 + 任意验证码） |
| 管理员 | `admin / admin123` |

### 端到端验证

```bash
bash deploy/e2e.sh   # 开方→双签→验签→PDF→派药→HMAC回调→取药完成，含错误签名 401 用例
```

也可手工在管理端走一遍：医生开方签名提交 → 药师审核台通过并输密码 → 处方详情验签/看 PDF → 管理端或 e2e 推进 Mock 订单 → 患者端查看取药进度。

## 主要 REST API（均经网关 8080）

| 模块 | 端点 |
|---|---|
| 认证 | `POST /api/auth/login` `POST /api/auth/sign-grant` `GET /api/auth/me` |
| 处方 | `POST /api/prescriptions` `PUT /api/prescriptions/{rxNo}` `POST /{rxNo}/submit` `GET /{rxNo}` `GET /{rxNo}/pdf` |
| 验签 | `POST /api/verify/signatures/{rxNo}` |
| 审方 | `GET /api/review/tasks?type=pharmacist|doctor-amend`、`POST /tasks/{rxNo}/approve|reject|request-amendment` |
| 患者 | `GET /api/patients/prescriptions/{rxNo}/progress` |
| 药店 | `GET /api/pharmacy-orders/{rxNo}`、`POST /api/pharmacies/{code}/callbacks/orders`（HMAC 验签，无 JWT） |
| 管理 | `/api/admin/users`、`/api/admin/users/{id}/certificates`、`/api/admin/pharmacies` |

药店回调签名串：`HMAC_SHA256(secret, timestamp + "\n" + nonce + "\n" + sha256(body))`，头 `X-Timestamp/X-Nonce/X-Signature`（±5 分钟、nonce 10 分钟防重放）。

## 接入真实药店

1. 实现 `PharmacyAdapter`（checkStock/hold/createOrder/queryStatus），注册为 Spring Bean，`type()` 返回新编码；
2. 管理端注册药店（adapter_type、base_url、HMAC/RSA 密钥、priority）；
3. 外部状态码在 `ExternalStatusMapping` 增映射；回调报文无需改动（统一验签入口）。

## 生产化待办（演示版边界）

托管私钥签名应替换为 KMS/HSM 或前端 WebCrypto；短信验证码、OSS STS 预签名 URL、退款/改派审批流、对账定时任务、MQ 分级延迟重试与监控告警、Springdoc OpenAPI 发布。
