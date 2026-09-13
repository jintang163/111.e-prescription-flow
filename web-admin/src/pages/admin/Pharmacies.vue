<template>
  <el-card>
    <template #header>
      <div class="hd"><span>合作药店与适配器</span>
        <el-button type="primary" size="small" @click="dialog=true">注册药店</el-button>
      </div>
    </template>
    <el-table :data="list" stripe size="small">
      <el-table-column prop="code" label="编码" width="120"/>
      <el-table-column prop="name" label="药店名称"/>
      <el-table-column prop="adapterType" label="适配器" width="110"/>
      <el-table-column prop="authType" label="鉴权" width="90"/>
      <el-table-column prop="appKey" label="AppKey" width="150"/>
      <el-table-column prop="signSecret" label="HMAC 密钥" width="180"/>
      <el-table-column prop="priority" label="优先级" width="80"/>
      <el-table-column label="状态" width="80">
        <template #default="{row}"><el-tag size="small" :type="row.status===1?'success':'info'">{{ row.status===1?'启用':'停用' }}</el-tag></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="注册合作药店" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="PHARM03"/></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name"/></el-form-item>
        <el-form-item label="适配器">
          <el-select v-model="form.adapterType"><el-option label="MOCK（内置模拟）" value="MOCK"/></el-select>
        </el-form-item>
        <el-form-item label="鉴权方式"><el-input v-model="form.authType" value="HMAC"/></el-form-item>
        <el-form-item label="AppKey"><el-input v-model="form.appKey"/></el-form-item>
        <el-form-item label="HMAC 密钥"><el-input v-model="form.signSecret" placeholder="回调验签密钥"/></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="1"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([]); const dialog = ref(false)
const form = reactive({ code: '', name: '', adapterType: 'MOCK', authType: 'HMAC', appKey: '', signSecret: '', priority: 100 })
function load() { api.get('/api/admin/pharmacies').then(d => list.value = d) }
async function save() {
  await api.post('/api/admin/pharmacies', form)
  ElMessage.success('药店已注册'); dialog.value = false; load()
}
onMounted(load)
</script>
<style scoped>.hd{display:flex;justify-content:space-between;align-items:center}</style>
