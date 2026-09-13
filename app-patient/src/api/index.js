// 统一请求封装：H5 走 vite 代理；小程序在 manifest/此处配置完整网关地址
const BASE_URL = ''

function request(method, url, data) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        Authorization: uni.getStorageSync('token') ? `Bearer ${uni.getStorageSync('token')}` : ''
      },
      success: res => {
        const body = res.data
        if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error('未登录'))
          return
        }
        if (body && body.code === 0) return resolve(body.data)
        uni.showToast({ title: body?.message || '请求失败', icon: 'none' })
        reject(new Error(body?.message || 'error'))
      },
      fail: reject
    })
  })
}

export const api = {
  get: (u, d) => request('GET', u, d),
  post: (u, d) => request('POST', u, d)
}

export { BASE_URL }
