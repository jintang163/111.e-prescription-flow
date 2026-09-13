import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: localStorage.getItem('userId') || null,
    username: localStorage.getItem('username') || '',
    realName: localStorage.getItem('realName') || '',
    role: localStorage.getItem('role') || ''
  }),
  actions: {
    setSession(data) {
      this.token = data.accessToken
      this.userId = data.userId
      this.username = data.username
      this.realName = data.realName
      this.role = data.role
      localStorage.setItem('token', data.accessToken)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('username', data.username)
      localStorage.setItem('realName', data.realName)
      localStorage.setItem('role', data.role)
    },
    logout() {
      this.$reset()
      localStorage.clear()
    }
  }
})
