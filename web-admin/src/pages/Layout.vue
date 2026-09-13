<template>
  <el-container style="height:100vh">
    <el-aside width="220px" class="aside">
      <div class="logo"><el-icon><FirstAidKit/></el-icon> 电子处方平台</div>
      <el-menu :default-active="$route.path" router background-color="#0f2a47" text-color="#c3d6ea" active-text-color="#fff">
        <el-menu-item index="/workbench"><el-icon><Monitor/></el-icon>工作台</el-menu-item>
        <template v-if="auth.role==='DOCTOR'">
          <el-menu-item index="/rx/create"><el-icon><EditPen/></el-icon>开具处方</el-menu-item>
          <el-menu-item index="/rx/mine"><el-icon><Document/></el-icon>我的处方</el-menu-item>
        </template>
        <template v-if="auth.role==='PHARMACIST'">
          <el-menu-item index="/review/queue">
            <el-icon><Bell/></el-icon>待审核处方
            <el-badge v-if="todoCount" :value="todoCount" class="badge"/>
          </el-menu-item>
        </template>
        <template v-if="auth.role==='ADMIN'">
          <el-menu-item index="/admin/prescriptions"><el-icon><Tickets/></el-icon>处方监管</el-menu-item>
          <el-menu-item index="/admin/users"><el-icon><User/></el-icon>用户与证书</el-menu-item>
          <el-menu-item index="/admin/pharmacies"><el-icon><Shop/></el-icon>药店管理</el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div></div>
        <el-dropdown @command="onCmd">
          <span class="user">
            <el-tag size="small" :type="roleType">{{ roleText }}</el-tag>
            {{ auth.realName }}（{{ auth.username }}）<el-icon><ArrowDown/></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main><router-view/></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { useAuthStore } from '../store/auth'

const auth = useAuthStore()
const router = useRouter()
const todoCount = ref(0)

const roleText = computed(() => ({ DOCTOR: '医生', PHARMACIST: '药师', ADMIN: '管理员' }[auth.role]))
const roleType = computed(() => ({ DOCTOR: 'success', PHARMACIST: 'warning', ADMIN: 'danger' }[auth.role]))

function onCmd(c) {
  if (c === 'logout') { auth.logout(); router.push('/login') }
}

onMounted(() => {
  if (auth.role === 'PHARMACIST') {
    const load = () => api.get('/api/review/tasks/count').then(d => { todoCount.value = d.pharmacist || 0 }).catch(() => {})
    load(); setInterval(load, 10000)
  }
})
</script>

<style scoped>
.aside { background: #0f2a47; }
.logo { height: 60px; line-height: 60px; text-align: center; color: #fff; font-weight: bold; font-size: 16px; display:flex; gap:8px; align-items:center; justify-content:center; }
.aside .el-menu { border-right: none; }
.header { background: #fff; display: flex; align-items: center; justify-content: flex-end; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.user { cursor: pointer; display: flex; align-items: center; gap: 8px; }
.badge { margin-left: 8px; }
</style>
