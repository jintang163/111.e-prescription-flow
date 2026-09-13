import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const routes = [
  { path: '/login', component: () => import('../pages/Login.vue') },
  {
    path: '/',
    component: () => import('../pages/Layout.vue'),
    redirect: '/workbench',
    children: [
      { path: 'workbench', component: () => import('../pages/Workbench.vue') },
      { path: 'rx/create', component: () => import('../pages/doctor/RxCreate.vue'), meta: { roles: ['DOCTOR'] } },
      { path: 'rx/mine', component: () => import('../pages/doctor/MyPrescriptions.vue'), meta: { roles: ['DOCTOR'] } },
      { path: 'rx/:rxNo', component: () => import('../pages/RxDetail.vue') },
      { path: 'review/queue', component: () => import('../pages/pharmacist/ReviewQueue.vue'), meta: { roles: ['PHARMACIST'] } },
      { path: 'admin/users', component: () => import('../pages/admin/Users.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/pharmacies', component: () => import('../pages/admin/Pharmacies.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/prescriptions', component: () => import('../pages/admin/Prescriptions.vue'), meta: { roles: ['ADMIN'] } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) return '/workbench'
})

export default router
