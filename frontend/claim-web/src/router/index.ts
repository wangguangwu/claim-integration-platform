import { createRouter, createWebHistory } from 'vue-router'

import DefaultLayout from '../layouts/DefaultLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: DefaultLayout,
      children: [
        { path: '', name: 'portal', component: () => import('../views/portal/PortalView.vue') },
        {
          path: 'application',
          name: 'application',
          component: () => import('../views/application/ApplicationView.vue')
        },
        { path: 'case', name: 'case', component: () => import('../views/case/CaseView.vue') },
        {
          path: 'diagnosis',
          name: 'diagnosis',
          component: () => import('../views/diagnosis/DiagnosisView.vue')
        },
        { path: 'tenant', name: 'tenant', component: () => import('../views/tenant/TenantView.vue') }
      ]
    }
  ]
})

export default router
