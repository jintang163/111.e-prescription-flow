#!/usr/bin/env bash
# 端到端验证脚本：开方→双签→生效PDF→派药→回调推进→患者进度，含异常用例
# 前置：网关 http://localhost:8080 已启动（脚本经网关调用）
set -euo pipefail
B=${BASE_URL:-http://localhost:8080}
j(){ python3 -c 'import sys,json
path=sys.argv[1].split(".")
d=json.load(sys.stdin)
for k in path:
    d=d[int(k)] if k.isdigit() else d[k]
print(d)' "$1"; }
login(){ curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | j data.accessToken; }

echo "== 登录 =="
D=$(login doctor doctor123); P=$(login pharmacist pharma123)
A=$(login admin admin123); T=$(login patient patient123)
curl -s -X POST $B/api/auth/sign-grant -H "Authorization: Bearer $D" -H 'Content-Type: application/json' -d '{"password":"doctor123"}' >/dev/null

echo "== 开方 =="
RX=$(curl -s -X POST $B/api/prescriptions -H "Authorization: Bearer $D" -H 'Content-Type: application/json' -d '{
 "patientId":4,"patientName":"王患者","patientIdCardMask":"1101**********0000","patientAge":41,"patientGender":1,"patientPhone":"13900000001",
 "deptCode":"CARD","deptName":"心血管内科","rxCategory":1,
 "diagnoses":[{"icd10Code":"I10.x00","diagnosisName":"原发性高血压"}],
 "items":[{"seq":1,"drugCode":"DRUG-AMLOD","drugName":"苯磺酸氨氯地平片","spec":"5mg*7片","dosageForm":"片剂","qty":2,"unit":"盒","singleDose":"5mg","doseUnit":"片","frequency":"QD","administrationRoute":"口服","days":14}]}' | j data.rxNo)
echo "rxNo=$RX"

echo "== 医生签名提交 =="
curl -s -X POST $B/api/prescriptions/$RX/submit -H "Authorization: Bearer $D" -H 'Content-Type: application/json' -d '{}' >/dev/null
sleep 2

echo "== 药师待办 =="
curl -s "$B/api/review/tasks?type=pharmacist" -H "Authorization: Bearer $P" | j data | head -c 300; echo
curl -s -X POST $B/api/auth/sign-grant -H "Authorization: Bearer $P" -H 'Content-Type: application/json' -d '{"password":"pharma123"}' >/dev/null

echo "== 药师审核通过（药师签名，双签生效）=="
curl -s -X POST $B/api/review/tasks/$RX/approve -H "Authorization: Bearer $P" -H 'Content-Type: application/json' -d '{"comment":"用法用量适宜"}' >/dev/null
sleep 5

echo "== 密码学验签 =="
curl -s -X POST $B/api/verify/signatures/$RX -H "Authorization: Bearer $P" | python3 -m json.tool | grep -E "allSignaturesValid|verifyPassed|signerRole"

echo "== 下载双签章 PDF =="
curl -s -o /tmp/$RX.pdf -w "pdf http=%{http_code} bytes=%{size_download}\n" $B/api/prescriptions/$RX/pdf -H "Authorization: Bearer $T"

echo "== 药店订单（等待派单）=="
sleep 2
ORDER=$(curl -s $B/api/pharmacy-orders/$RX -H "Authorization: Bearer $P" | j data.0.order.orderNo)
echo "orderNo=$ORDER"

echo "== 模拟药店 HMAC 签名回调（状态：配药中）=="
TS=$(python3 -c "import time;print(int(time.time()*1000))"); NONCE=$(python3 -c "import uuid;print(uuid.uuid4())")
BODY="{\"rxNo\":\"$RX\",\"orderNo\":\"$ORDER\",\"status\":\"DISPENSING\",\"eventId\":\"evt-$NONCE\"}"
SIG=$(python3 -c "
import hmac,hashlib
body='''$BODY'''; ts='$TS'; nonce='$NONCE'; secret='secret-pharm01'
bh=hashlib.sha256(body.encode()).hexdigest()
print(hmac.new(secret.encode(),f'{ts}\n{nonce}\n{bh}'.encode(),hashlib.sha256).hexdigest())")
curl -s -X POST $B/api/pharmacies/PHARM01/callbacks/orders -H 'Content-Type: application/json' \
  -H "X-Timestamp: $TS" -H "X-Nonce: $NONCE" -H "X-Signature: $SIG" -d "$BODY"; echo

echo "== 错误签名回调应被拒绝（业务码 40110）=="
curl -s -X POST $B/api/pharmacies/PHARM01/callbacks/orders \
  -H 'Content-Type: application/json' -H "X-Timestamp: $TS" -H "X-Nonce: n-bad" -H "X-Signature: deadbeef" -d "$BODY"; echo

echo "== Mock 推进到取药完成（配药中 → 已配齐 → 待取药 → 已取药，3 步）=="
for i in 1 2 3; do curl -s -X POST "$B/api/internal/mock-pharmacy/advance?rxNo=$RX" -H "Authorization: Bearer $A" >/dev/null; sleep 1; done

echo "== 患者取药进度 =="
curl -s $B/api/patients/prescriptions/$RX/progress -H "Authorization: Bearer $T" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('rxStatus=',d['rxStatus'],'fulfillment=',d['fulfillmentStatus'],'pharmacy=',d['pharmacyName'])
for t in d['timeline']: print(' ',t['createdAt'],t['toStatus'],t.get('action'))"

echo "E2E_DONE $RX"
