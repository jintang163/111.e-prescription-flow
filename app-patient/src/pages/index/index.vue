<template>
  <view class="page">
    <view class="header">
      <view class="hello">你好，{{ user.realName || '患者' }}</view>
      <view class="sub">互联网医院 · 我的电子处方</view>
      <text class="logout" @tap="logout">退出</text>
    </view>

    <view v-if="!list.length" class="empty">
      <text>暂无处方记录</text>
    </view>

    <view class="card" v-for="rx in list" :key="rx.rxNo" @tap="open(rx.rxNo)">
      <view class="card-top">
        <text class="rxno">{{ rx.rxNo }}</text>
        <text class="tag" :class="tagClass(rx.rxStatus)">{{ statusText(rx.rxStatus) }}</text>
      </view>
      <view class="diag">{{ rx.diagnosisSummary }}</view>
      <view class="meta">
        <text>{{ rx.doctorName }} 医生 · {{ rx.deptName }}</text>
      </view>
      <view class="card-bot">
        <text class="pharmacy" v-if="rx.currentPharmacyName">🏪 {{ rx.currentPharmacyName }}</text>
        <text class="fulfillment" v-if="rx.fulfillmentStatus">{{ fulfillmentText(rx.fulfillmentStatus) }}</text>
        <text class="time">{{ fmt(rx.createdAt) }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { api } from '../../api'
import { statusText, fulfillmentText, statusClass } from '../../utils/status'

export default {
  data() {
    return {
      list: [],
      user: uni.getStorageSync('userInfo') || {},
      timer: null
    }
  },
  onShow() {
    if (!uni.getStorageSync('token')) return uni.reLaunch({ url: '/pages/login/login' })
    this.load()
    this.timer = setInterval(this.load, 8000)
  },
  onHide() { clearInterval(this.timer) },
  onUnload() { clearInterval(this.timer) },
  methods: {
    statusText, fulfillmentText,
    tagClass(s) { return statusClass(s) },
    fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' },
    async load() {
      try {
        const d = await api.get('/api/prescriptions', { page: 1, size: 30 })
        this.list = d.list || []
      } catch (e) { /* 拦截器已提示 */ }
    },
    open(rxNo) { uni.navigateTo({ url: `/pages/detail/detail?rxNo=${rxNo}` }) },
    logout() {
      uni.removeStorageSync('token'); uni.removeStorageSync('userInfo')
      uni.reLaunch({ url: '/pages/login/login' })
    }
  }
}
</script>

<style>
.page { padding: 24rpx; }
.header { padding: 20rpx 10rpx 30rpx; position: relative; }
.hello { color: #1e2b3c; font-size: 40rpx; font-weight: bold; }
.sub { color: #8a94a6; font-size: 24rpx; margin-top: 8rpx; }
.logout { position: absolute; right: 10rpx; top: 10rpx; color: #1e6fb8; font-size: 26rpx; }
.empty { text-align: center; color: #909399; padding: 160rpx 0; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 2rpx 12rpx rgba(30,111,184,.06); }
.card-top { display: flex; justify-content: space-between; align-items: center; }
.rxno { font-weight: bold; color: #1e2b3c; }
.tag { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 20rpx; }
.tag-success { background: #e8f7ee; color: #2ba471; }
.tag-warning { background: #fdf6ec; color: #cf9236; }
.tag-danger { background: #fef0f0; color: #e05d5d; }
.tag-info { background: #f0f2f5; color: #909399; }
.tag-primary { background: #eaf3fc; color: #1e6fb8; }
.diag { margin: 16rpx 0; color: #455064; font-size: 28rpx; }
.meta { color: #8a94a6; font-size: 24rpx; }
.card-bot { display: flex; justify-content: space-between; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #f0f2f5; font-size: 24rpx; color: #606a78; }
.fulfillment { color: #2bb2a3; }
</style>
