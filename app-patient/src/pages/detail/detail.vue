<template>
  <view class="page" v-if="detail">
    <!-- 进度状态卡 -->
    <view class="status-card" :class="classOf(detail.rxStatus)">
      <view class="big">{{ statusText(detail.rxStatus) }}</view>
      <view class="sub" v-if="detail.fulfillmentStatus">{{ fulfillmentText(detail.fulfillmentStatus) }}</view>
      <view class="pharmacy" v-if="detail.pharmacyName">🏪 {{ detail.pharmacyName }}</view>
      <view class="rxno">处方号 {{ detail.rxNo }}</view>
    </view>

    <!-- 进度时间线 -->
    <view class="card">
      <view class="card-title">取药进度</view>
      <view class="step" v-for="(t, i) in progressSteps" :key="i" :class="{done: t.done, last: i===progressSteps.length-1}">
        <view class="dot"></view>
        <view class="step-body">
          <view class="step-label">{{ t.label }}</view>
          <view class="step-time" v-if="t.time">{{ t.time }}</view>
        </view>
      </view>
    </view>

    <!-- 处方明细 -->
    <view class="card">
      <view class="card-title">处方内容</view>
      <view class="line" v-for="it in detail.items" :key="it.id">
        <view class="drug">{{ it.seq }}. {{ it.drugName }} <text class="muted">{{ it.spec }}</text></view>
        <view class="muted small">数量 {{ it.qty }}{{ it.unit }} · 每次{{ it.singleDose }}{{ it.doseUnit }} · {{ it.frequency }} · {{ it.days }}天</view>
      </view>
      <view class="sign-line">
        <text class="sign-tag">医生 {{ doctorName }}</text>
        <text class="sign-tag">药师 {{ pharmacistName }}</text>
      </view>
    </view>

    <button class="btn-pdf" @tap="openPdf">查看电子处方 PDF（数字签章）</button>
  </view>
</template>

<script>
import { api, BASE_URL } from '../../api'
import { statusText, fulfillmentText, statusClass } from '../../utils/status'

export default {
  data() {
    return {
      rxNo: '',
      detail: null,
      doctorName: '', pharmacistName: '',
      full: null
    }
  },
  computed: {
    progressSteps() {
      const d = this.detail
      const t = Object.fromEntries((this.full?.logs || []).map(l => [l.toStatus, l.createdAt]))
      const pick = (...keys) => keys.map(k => t[k]).find(Boolean)
      return [
        { label: '医生开方并签名', done: true, time: this.fmt(pick('DRAFT','SUBMITTED')) },
        { label: '药师审核签名（双签生效）', done: !!pick('EFFECTIVE','REVIEWING'), time: this.fmt(pick('EFFECTIVE')) },
        { label: '药店受理', done: !!pick('DISPATCHED'), time: this.fmt(pick('DISPATCHED')) },
        { label: '药店配药', done: !!pick('FULFILLING','DISPENSED'), time: this.fmt(pick('FULFILLING','DISPENSED')) },
        { label: '待取药', done: !!pick('READY_FOR_PICKUP'), time: this.fmt(pick('READY_FOR_PICKUP')) },
        { label: '已取药', done: d.rxStatus === 'PICKED_UP', time: this.fmt(pick('PICKED_UP')) }
      ]
    }
  },
  onLoad(q) {
    this.rxNo = q.rxNo
    this.load()
  },
  methods: {
    statusText, fulfillmentText,
    classOf(s) { return 'bg-' + statusClass(s) },
    fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' },
    async load() {
      this.detail = await api.get(`/api/patients/prescriptions/${this.rxNo}/progress`)
      this.full = await api.get(`/api/prescriptions/${this.rxNo}`)
      this.doctorName = (this.full.signatures.find(s => s.signerRole === 'DOCTOR') || {}).signerName || ''
      this.pharmacistName = (this.full.signatures.find(s => s.signerRole === 'PHARMACIST') || {}).signerName || ''
      // 进度接口不含明细，明细挂在 full.prescription? 详情组装体中 items 在顶层
      this.detail.items = this.full.items
    },
    openPdf() {
      uni.showLoading({ title: '加载中' })
      const token = uni.getStorageSync('token')
      // #ifdef H5
      fetch(`${BASE_URL}/api/prescriptions/${this.rxNo}/pdf`, { headers: { Authorization: `Bearer ${token}` } })
        .then(r => r.blob()).then(b => {
          uni.hideLoading()
          window.open(URL.createObjectURL(b), '_blank')
        })
      // #endif
      // #ifndef H5
      uni.downloadFile({
        url: BASE_URL + `/api/prescriptions/${this.rxNo}/pdf`,
        header: { Authorization: `Bearer ${token}` },
        success: r => {
          uni.hideLoading()
          uni.openDocument({ filePath: r.tempFilePath, fileType: 'pdf', showMenu: true })
        },
        fail: () => { uni.hideLoading(); uni.showToast({ title: 'PDF 加载失败', icon: 'none' }) }
      })
      // #endif
    }
  }
}
</script>

<style>
.page { padding: 24rpx; }
.status-card { border-radius: 18rpx; padding: 40rpx 32rpx; color: #fff; margin-bottom: 24rpx; }
.bg-success { background: linear-gradient(135deg,#2ba471,#4cc38a); }
.bg-primary { background: linear-gradient(135deg,#1e6fb8,#3f93d6); }
.bg-warning { background: linear-gradient(135deg,#cf9236,#e6a94e); }
.bg-danger { background: linear-gradient(135deg,#d95f5f,#e8826f); }
.bg-info { background: linear-gradient(135deg,#7b8794,#98a2af); }
.big { font-size: 44rpx; font-weight: bold; }
.sub { margin-top: 10rpx; font-size: 28rpx; opacity: .92; }
.pharmacy { margin-top: 16rpx; font-size: 26rpx; }
.rxno { margin-top: 22rpx; font-size: 22rpx; opacity: .8; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.card-title { font-weight: bold; margin-bottom: 20rpx; }
.step { display: flex; padding-bottom: 28rpx; position: relative; }
.step::before { content:''; position:absolute; left: 13rpx; top: 28rpx; bottom: 0; width: 2rpx; background: #dfe3ea; }
.step.last::before { display: none; }
.dot { width: 28rpx; height: 28rpx; border-radius: 50%; background: #c4ccd8; margin-right: 20rpx; margin-top: 4rpx; z-index: 1; }
.step.done .dot { background: #2ba471; }
.step-label { color: #333; font-size: 28rpx; }
.step.done .step-label { color: #1c2735; font-weight: 600; }
.step-time { color: #97a0ae; font-size: 22rpx; margin-top: 4rpx; }
.line { padding: 14rpx 0; border-bottom: 1rpx solid #f2f4f7; }
.drug { color: #1c2735; font-size: 28rpx; }
.muted { color: #8a94a6; }
.small { font-size: 22rpx; margin-top: 4rpx; }
.sign-line { display: flex; gap: 16rpx; margin-top: 20rpx; }
.sign-tag { background: #eef4fb; color: #1e6fb8; font-size: 22rpx; padding: 6rpx 18rpx; border-radius: 20rpx; }
.btn-pdf { background: #1e6fb8; color: #fff; border-radius: 12rpx; margin-top: 8rpx; }
</style>
