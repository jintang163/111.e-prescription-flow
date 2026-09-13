<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <el-card shadow="hover" class="stat">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{color:c.color}">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px" v-if="auth.role==='DOCTOR'">
      <template #header>我的处方</template>
      <el-table :data="mine" size="small" stripe @row-click="open">
        <el-table-column prop="rxNo" label="处方号" width="200"/>
        <el-table-column prop="patientName" label="患者" width="100"/>
        <el-table-column prop="diagnosisSummary" label="诊断" show-overflow-tooltip/>
        <el-table-column prop="rxStatus" label="状态" width="150">
          <template #default="{row}"><el-tag size="small">{{ statusText(row.rxStatus) }}</el-tag></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px" v-if="auth.role==='PHARMACIST'">
      <template #header>待审核（最新 5 条）</template>
      <el-table :data="todo" size="small" stripe @row-click="openTodo">
        <el-table-column prop="rxNo" label="处方号" width="200"/>
        <el-table-column prop="patientName" label="患者" width="100"/>
        <el-table-column prop="doctorName" label="开方医生" width="100"/>
        <el-table-column prop="createTime" label="提交时间"/>
      </el-table>
      <div style="margin-top:10px"><el-button type="primary" @click="$router.push('/review/queue')">进入审核台</el-button></div>
    </el-card>

    <el-card style="margin-top:16px" v-if="auth.role==='ADMIN'">
      <template #header>系统说明</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="闭环流程">视频问诊 → 医生开方签名 → 药师审方签名（双签生效）→ 签章 PDF → 药店派单 → 配药/发药回传 → 患者取药</el-descriptions-item>
        <el-descriptions-item label="签名体系">RSA 2048 + 内置 CA 签发 X.509 证书，SHA256withRSA；PDF 为 PAdES/PKCS#7 双数字签章</el-descriptions-item>
        <el-descriptions-item label="对接药店">REST 适配层（MOCK/SAMPLE_HTTP），HMAC 回调验签 + nonce 防重放，库存校验→预占→下单→状态回传</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { useAuthStore } from '../store/auth'
import { statusText } from '../utils/status'

const auth = useAuthStore()
const router = useRouter()
const mine = ref([])
const todo = ref([])
const counts = ref({})

const cards = computed(() => [
  { label: '我的处方总数', value: counts.value.total ?? '-', color: '#1e6fb8' },
  { label: '审核中', value: counts.value.reviewing ?? '-', color: '#e6a23c' },
  { label: '已生效', value: counts.value.effective ?? '-', color: '#67c23a' },
  { label: '履约中/完成', value: counts.value.fulfilling ?? '-', color: '#2bb2a3' }
])

function open(row) { router.push(`/rx/${row.rxNo}`) }
function openTodo(row) { router.push(`/review/queue?rxNo=${row.rxNo}`) }

onMounted(() => {
  if (auth.role === 'DOCTOR') {
    api.get('/api/prescriptions', { params: { page: 1, size: 5 } }).then(d => {
      mine.value = d.list
      counts.value.total = d.total
      counts.value.reviewing = d.list.filter(x => ['SUBMITTED','REVIEWING','AMENDMENT_REQUESTED'].includes(x.rxStatus)).length
      counts.value.effective = d.list.filter(x => x.rxStatus === 'EFFECTIVE').length
      counts.value.fulfilling = d.list.filter(x => ['DISPATCHED','FULFILLING','READY_FOR_PICKUP','PICKED_UP'].includes(x.rxStatus)).length
    })
  }
  if (auth.role === 'PHARMACIST') {
    api.get('/api/review/tasks', { params: { type: 'pharmacist', page: 1, size: 5 } }).then(d => todo.value = d)
  }
})
</script>

<style scoped>
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 30px; font-weight: bold; margin-top: 6px; }
</style>
