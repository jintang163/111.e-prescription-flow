<template>
  <div v-loading="loading">
    <el-page-header @back="$router.back()" :content="`处方 ${rxNo}`" style="margin-bottom:12px"/>

    <el-card v-if="data">
      <template #header>
        <div class="hd">
          <span>处方信息
            <el-tag size="small" :type="statusType(p.rxStatus)" style="margin-left:8px">{{ statusText(p.rxStatus) }}</el-tag>
            <el-tag v-if="p.fulfillmentStatus" size="small" type="primary" style="margin-left:6px">{{ fulfillmentText(p.fulfillmentStatus) }}</el-tag>
          </span>
          <el-space>
            <el-button size="small" @click="verify"><el-icon><Key/></el-icon>验签</el-button>
            <el-button size="small" @click="openPdf" :disabled="p.pdfStatus!=='UPLOADED'"><el-icon><Document/></el-icon>处方PDF</el-button>
          </el-space>
        </div>
      </template>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="处方号">{{ p.rxNo }}（v{{ p.rxVersion }}）</el-descriptions-item>
        <el-descriptions-item label="患者">{{ p.patientName }}，{{ p.patientAge }}岁，{{ p.patientGender===1?'男':'女' }}</el-descriptions-item>
        <el-descriptions-item label="开方医生">{{ p.doctorName }}（{{ p.deptName }}）</el-descriptions-item>
        <el-descriptions-item label="开具时间">{{ p.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="生效时间">{{ p.effectiveAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期至">{{ p.expireAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="临床诊断" :span="3">
          <el-tag v-for="d in data.diagnoses" :key="d.id" size="small" style="margin-right:6px">
            {{ d.diagnosisName }}<span v-if="d.icd10Code" class="muted">（{{ d.icd10Code }}）</span>
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <h4 style="margin:14px 0 8px">Rp</h4>
      <el-table :data="data.items" border size="small">
        <el-table-column type="index" label="#" width="45"/>
        <el-table-column prop="drugName" label="药品" min-width="180">
          <template #default="{row}">{{ row.drugName }} <span class="muted">{{ row.spec }} {{ row.dosageForm }}</span></template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{row}">{{ row.qty }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="用法用量" min-width="160">
          <template #default="{row}">每次 {{ row.singleDose }}{{ row.doseUnit }}，{{ row.frequency }}，{{ row.administrationRoute }}，{{ row.days }}天
            <el-tag v-if="row.skinTestFlag===1" type="danger" size="small">皮试</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-row :gutter="16" style="margin-top:16px">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header>流转轨迹</template>
            <el-timeline>
              <el-timeline-item v-for="l in data.logs" :key="l.id" :timestamp="l.createdAt" placement="top">
                <el-tag size="small" :type="statusType(l.toStatus)">{{ statusText(l.toStatus) }}</el-tag>
                <span style="margin-left:8px">{{ actionText(l.action) }}</span>
                <span v-if="l.comment" class="muted">：{{ l.comment }}</span>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never">
            <template #header>数字签名（双签）</template>
            <div v-for="s in data.signatures" :key="s.id" class="sig">
              <el-tag size="small" :type="s.signerRole==='DOCTOR'?'success':'warning'">
                {{ s.signerRole==='DOCTOR'?'医生签名':'药师签名' }}
              </el-tag>
              <b style="margin:0 6px">{{ s.signerName }}</b>
              <el-tag size="small" :type="s.signStatus==='ACTIVE'?'success':'info'">v{{ s.rxVersion }} {{ s.signStatus==='ACTIVE'?'有效':'已作废' }}</el-tag>
              <div class="muted mono">证书：{{ s.certSerial }}</div>
              <div class="muted mono">SHA256：{{ s.payloadSha256.slice(0,32) }}…</div>
              <div class="muted">{{ s.signTime }} · {{ s.alg }}</div>
            </div>
            <el-alert v-if="verifyResult" :type="verifyResult.allSignaturesValid?'success':'error'" :closable="false"
              :title="verifyResult.allSignaturesValid ? `密码学验签通过（${verifyResult.signatures.length} 个签名全部有效）` : '验签未通过'" style="margin-top:8px"/>
          </el-card>
        </el-col>
      </el-row>

      <el-alert v-if="p.rejectReason" type="error" :closable="false" show-icon style="margin-top:12px"
        :title="(p.rxStatus==='REJECTED'?'驳回原因：':'补正意见：') + p.rejectReason"/>

      <!-- 药师操作 -->
      <el-card v-if="auth.role==='PHARMACIST' && canReview" shadow="never" style="margin-top:16px">
        <template #header>审核结论（通过即完成药师数字签名）</template>
        <el-form inline>
          <el-form-item label="审核意见"><el-input v-model="comment" style="width:360px" placeholder="适应证/剂量/联用/过敏/特殊人群审核意见"/></el-form-item>
          <el-form-item>
            <el-button type="success" @click="signDialog=true">通过并签名</el-button>
            <el-button type="warning" @click="askAmend">要求补正</el-button>
            <el-button type="danger" @click="reject">驳回</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 医生补正操作 -->
      <el-card v-if="auth.role==='DOCTOR' && p.rxStatus==='AMENDMENT_REQUESTED'" shadow="never" style="margin-top:16px">
        <el-alert type="warning" :title="`药师要求补正：${p.rejectReason}`" :closable="false" style="margin-bottom:10px"/>
        <el-button type="primary" @click="$router.push('/rx/mine')">在我的处方中修改并重新签名提交</el-button>
      </el-card>
    </el-card>

    <el-dialog v-model="signDialog" title="药师签名重认证" width="420px">
      <el-input v-model="signPassword" type="password" placeholder="请输入登录密码" show-password @keyup.enter="approve"/>
      <template #footer>
        <el-button @click="signDialog=false">取消</el-button>
        <el-button type="success" @click="approve">确认审核通过并签名</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useAuthStore } from '../store/auth'
import { statusText, statusType, fulfillmentText } from '../utils/status'

const route = useRoute(); const auth = useAuthStore()
const rxNo = route.params.rxNo
const data = ref(null); const loading = ref(true)
const comment = ref(''); const signDialog = ref(false); const signPassword = ref('')
const verifyResult = ref(null)

const p = computed(() => data.value?.prescription || {})
const canReview = computed(() => ['SUBMITTED','REVIEWING'].includes(p.value.rxStatus))
const actionText = a => ({ CREATE:'创建草稿', SUBMIT:'签名提交', RESUBMIT:'补正后重提', ENTER_REVIEW:'进入审核',
  APPROVE:'审核通过（药师签名）', REJECT:'驳回', REQUEST_AMENDMENT:'要求补正', CANCEL:'取消' }[a] || a)

async function load() {
  loading.value = true
  data.value = await api.get(`/api/prescriptions/${rxNo}`)
  loading.value = false
}
function verify() {
  api.post(`/api/verify/signatures/${rxNo}`).then(d => {
    verifyResult.value = d
    ElMessage[d.allSignaturesValid ? 'success' : 'error'](d.allSignaturesValid ? '双签验签通过' : '存在无效签名')
  })
}
function openPdf() {
  api.get(`/api/prescriptions/${rxNo}/pdf`, { responseType: 'blob' }).then(blob => {
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  })
}
async function approve() {
  await api.post('/api/auth/sign-grant', { password: signPassword.value })
  await api.post(`/api/review/tasks/${rxNo}/approve`, { comment: comment.value })
  ElMessage.success('审核通过并完成药师签名'); signDialog.value = false
  setTimeout(load, 800)
}
async function askAmend() {
  const c = await ElMessageBox.prompt('请填写补正意见', '要求补正', { inputValue: comment.value }).catch(() => null)
  if (!c) return
  await api.post(`/api/review/tasks/${rxNo}/request-amendment`, { comment: c.value })
  ElMessage.success('已退回医生补正'); setTimeout(load, 800)
}
async function reject() {
  const r = await ElMessageBox.prompt('请填写驳回原因（必填）', '驳回处方', { inputPattern: /.+/, inputErrorMessage: '驳回原因必填' }).catch(() => null)
  if (!r) return
  await api.post(`/api/review/tasks/${rxNo}/reject`, { reason: r.value })
  ElMessage.success('已驳回'); setTimeout(load, 800)
}
onMounted(load)
</script>
<style scoped>
.hd{display:flex;justify-content:space-between;align-items:center}
.muted{color:#909399;font-size:12px}.mono{font-family:monospace;word-break:break-all}
.sig{padding:8px 0;border-bottom:1px dashed #ebeef5}.sig:last-child{border:none}
</style>
