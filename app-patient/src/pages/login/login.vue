<template>
  <view class="login-page">
    <view class="logo">🏥 互联网医院</view>
    <view class="card">
      <view class="title">患者登录</view>
      <input class="input" v-model="phone" placeholder="手机号" type="number"/>
      <view class="code-line">
        <input class="input code" v-model="code" placeholder="短信验证码（演示任意6位）" type="number"/>
        <button class="btn-code" size="mini" @tap="sendCode">{{ sec > 0 ? sec + 's' : '获取验证码' }}</button>
      </view>
      <button class="btn-primary" :loading="loading" @tap="login">登录</button>
      <view class="hint">演示账号 13900000001，验证码任意（映射 patient/patient123）</view>
    </view>
  </view>
</template>

<script>
import { api } from '../../api'

export default {
  data() {
    return { phone: '13900000001', code: '', sec: 0, loading: false }
  },
  methods: {
    sendCode() {
      if (!/^1\d{10}$/.test(this.phone)) return uni.showToast({ title: '手机号格式不正确', icon: 'none' })
      this.sec = 60
      uni.showToast({ title: '验证码已发送（演示环境任意码）', icon: 'none' })
      const t = setInterval(() => { this.sec--; if (this.sec <= 0) clearInterval(t) }, 1000)
    },
    async login() {
      if (!/^1\d{10}$/.test(this.phone) || !this.code) {
        return uni.showToast({ title: '请输入手机号和验证码', icon: 'none' })
      }
      this.loading = true
      try {
        // 演示：手机号验证码登录映射到统一认证账号
        const d = await api.post('/api/auth/login', { username: 'patient', password: 'patient123' })
        uni.setStorageSync('token', d.accessToken)
        uni.setStorageSync('userInfo', d)
        uni.reLaunch({ url: '/pages/index/index' })
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style>
.login-page { min-height: 100vh; background: linear-gradient(160deg, #1e6fb8, #2bb2a3); padding: 120rpx 40rpx; }
.logo { color: #fff; text-align: center; font-size: 40rpx; font-weight: bold; margin-bottom: 60rpx; }
.card { background: #fff; border-radius: 20rpx; padding: 50rpx 40rpx; }
.title { font-size: 34rpx; font-weight: bold; margin-bottom: 30rpx; }
.input { border: 1rpx solid #dcdfe6; border-radius: 10rpx; padding: 20rpx; margin-bottom: 24rpx; font-size: 28rpx; }
.code-line { display: flex; align-items: center; gap: 16rpx; }
.code { flex: 1; }
.btn-code { white-space: nowrap; }
.btn-primary { background: #1e6fb8; color: #fff; border-radius: 10rpx; margin-top: 10rpx; }
.hint { color: #909399; font-size: 22rpx; margin-top: 24rpx; text-align: center; }
</style>
