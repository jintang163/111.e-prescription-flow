<template>
  <el-card>
    <template #header>
      <div class="hd"><span>用户与签名证书</span>
        <el-select v-model="userType" placeholder="全部类型" clearable style="width:150px" @change="load">
          <el-option label="医生" :value="1"/><el-option label="药师" :value="2"/>
          <el-option label="患者" :value="3"/><el-option label="管理员" :value="9"/>
        </el-select>
      </div>
    </template>
    <el-table :data="list.records||list" stripe size="small">
      <el-table-column prop="id" label="ID" width="70"/>
      <el-table-column prop="username" label="账号" width="120"/>
      <el-table-column prop="realName" label="姓名" width="120"/>
      <el-table-column label="类型" width="90">
        <template #default="{row}">{{ typeText[row.userType] }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机" width="140"/>
      <el-table-column label="状态" width="80">
        <template #default="{row}"><el-tag size="small" :type="row.status===1?'success':'info'">{{ row.status===1?'正常':'停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{row}">
          <el-button v-if="row.userType===1||row.userType===2" size="small" type="primary" plain
            @click="issue(row)">签发/轮换 CA 证书</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'

const list = ref([]); const userType = ref()
const typeText = { 1: '医生', 2: '药师', 3: '患者', 9: '管理员' }
function load() {
  api.get('/api/admin/users', { params: { userType: userType.value, page: 1, size: 50 } }).then(d => list.value = d)
}
async function issue(row) {
  await ElMessageBox.confirm(`确认为 ${row.realName} 签发/轮换签名证书？旧密钥将标记 ROTATED。`, 'CA 签发', { type: 'warning' })
  const d = await api.post(`/api/admin/users/${row.id}/certificates`)
  ElMessage.success('证书已签发：' + d.certSerial)
  load()
}
onMounted(load)
</script>
<style scoped>.hd{display:flex;justify-content:space-between;align-items:center}</style>
