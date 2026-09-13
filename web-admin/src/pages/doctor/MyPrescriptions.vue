<template>
  <el-card>
    <template #header>
      <div class="hd">
        <span>我的处方</span>
        <el-select v-model="status" placeholder="全部状态" clearable style="width:160px" @change="load">
          <el-option v-for="(t,k) in RX_STATUS" :key="k" :label="t" :value="k"/>
        </el-select>
      </div>
    </template>
    <el-table :data="list" stripe @row-click="open" size="small">
      <el-table-column prop="rxNo" label="处方号" width="190"/>
      <el-table-column prop="rxVersion" label="版本" width="60"/>
      <el-table-column prop="patientName" label="患者" width="90"/>
      <el-table-column prop="diagnosisSummary" label="诊断" show-overflow-tooltip/>
      <el-table-column prop="fulfillmentStatus" label="履约" width="120">
        <template #default="{row}">{{ fulfillmentText(row.fulfillmentStatus) }}</template>
      </el-table-column>
      <el-table-column prop="rxStatus" label="状态" width="120">
        <template #default="{row}"><el-tag size="small" :type="statusType(row.rxStatus)">{{ statusText(row.rxStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createdAt" label="开具时间" width="180"/>
    </el-table>
    <el-pagination style="margin-top:12px" layout="prev,pager,next,total" :total="total" :page-size="size" :current-page="page"
      @current-change="p => { page=p; load() }"/>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../api'
import { RX_STATUS, statusText, statusType, fulfillmentText } from '../../utils/status'

const router = useRouter()
const list = ref([]); const total = ref(0); const page = ref(1); const size = 20
const status = ref('')
function load() {
  api.get('/api/prescriptions', { params: { page: page.value, size, status: status.value || undefined } })
    .then(d => { list.value = d.list; total.value = d.total })
}
function open(row) { router.push(`/rx/${row.rxNo}`) }
onMounted(load)
</script>
<style scoped>.hd{display:flex;justify-content:space-between;align-items:center}</style>
