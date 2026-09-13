<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">
        <el-icon size="30"><FirstAidKit/></el-icon>
        <h2>互联网医院电子处方平台</h2>
      </div>
      <el-tabs v-model="role">
        <el-tab-pane label="医生" name="doctor"/>
        <el-tab-pane label="药师" name="pharmacist"/>
        <el-tab-pane label="管理员" name="admin"/>
      </el-tabs>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" :placeholder="`${roleName}账号`" prefix-icon="User" size="large"/>
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password @keyup.enter="submit"/>
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="hint">演示账号：doctor/doctor123 · pharmacist/pharma123 · admin/admin123</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const role = ref('doctor')
const username = ref('doctor')
const password = ref('doctor123')
const loading = ref(false)

const roleName = computed(() => ({ doctor: '医生', pharmacist: '药师', admin: '管理员' }[role.value]))

function submit() {
  loading.value = true
  api.post('/api/auth/login', { username: username.value, password: password.value })
    .then(data => {
      auth.setSession(data)
      ElMessage.success(`欢迎，${data.realName}`)
      router.push('/workbench')
    })
    .finally(() => loading.value = false)
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1e6fb8 0%, #2bb2a3 100%); }
.login-card { width: 420px; padding: 12px 18px; }
.brand { display: flex; align-items: center; gap: 10px; justify-content: center; color: #1e6fb8; margin-bottom: 8px; }
.brand h2 { margin: 0; font-size: 19px; }
.hint { margin-top: 14px; font-size: 12px; color: #909399; text-align: center; }
</style>
