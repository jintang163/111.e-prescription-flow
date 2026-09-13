<template>
  <el-card>
    <template #header>
      <div class="hd">
        <span>药师审核台</span>
        <el-radio-group v-model="type" size="small" @change="load">
          <el-radio-button label="pharmacist">待我审核</el-radio-button>
          <el-radio-button label="doctor-amend" v-if="auth.role==='DOCTOR'">补正待办</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <el-table :data="list" stripe size="small" highlight-current-row @row-click="open">
      <el-table-column prop="rxNo" label="处方号" width="200"/>
      <el-table-column prop="patientName" label="患者" width="100"/>
      <el-table-column prop="doctorName" label="开方医生" width="110"/>
      <el-table-column prop="rxVersion" label="版本" width="70"/>
      <el-table-column prop="node" label="当前节点" width="140">
        <template #default="{row}">{{ row.node === 'doctorAmend' ? '医生补正中' : '药师审核' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="到达时间"/>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../../api'
import { useAuthStore } from '../../store/auth'

const auth = useAuthStore()
const router = useRouter(); const route = useRoute()
const list = ref([]); const type = ref('pharmacist')

function load() {
  api.get('/api/review/tasks', { params: { type: type.value } }).then(d => list.value = d)
}
function open(row) { router.push(`/rx/${row.rxNo}`) }
onMounted(() => { if (route.query.rxNo) router.push(`/rx/${route.query.rxNo}`); load() })
</script>
<style scoped>.hd{display:flex;justify-content:space-between;align-items:center}</style>
