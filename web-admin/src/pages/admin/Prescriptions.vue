<template>
  <el-card>
    <template #header>
      <div class="hd"><span>处方监管与流转轨迹</span>
        <el-space>
          <el-select v-model="status" placeholder="全部状态" clearable style="width:150px" @change="load">
            <el-option v-for="(t,k) in RX_STATUS" :key="k" :label="t" :value="k"/>
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
        </el-space>
      </div>
    </template>
    <el-table :data="list" stripe size="small" @row-click="open">
      <el-table-column prop="rxNo" label="处方号" width="190"/>
      <el-table-column prop="patientName" label="患者" width="90"/>
      <el-table-column prop="doctorName" label="医生" width="90"/>
      <el-table-column prop="diagnosisSummary" label="诊断" show-overflow-tooltip/>
      <el-table-column prop="currentPharmacyName" label="履约药店" width="170"/>
      <el-table-column label="处方状态" width="120">
        <template #default="{row}"><el-tag size="small" :type="statusType(row.rxStatus)">{{ statusText(row.rxStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="履约" width="100">
        <template #default="{row}">{{ fulfillmentText(row.fulfillmentStatus) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="prev,pager,next,total" :total="total" :page-size="size" :current-page="page"
      @current-change="p=>{page=p;load()}"/>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../api'
import { RX_STATUS, statusText, statusType, fulfillmentText } from '../../utils/status'

const router = useRouter()
const list = ref([]); const total = ref(0); const page = ref(1); const size = 20; const status = ref('')
function load() {
  api.get('/api/prescriptions', { params: { page: page.value, size, status: status.value || undefined } })
    .then(d => { list.value = d.list; total.value = d.total })
}
function open(row) { router.push(`/rx/${row.rxNo}`) }
onMounted(load)
</script>
<style scoped>.hd{display:flex;justify-content:space-between;align-items:center}</style>
