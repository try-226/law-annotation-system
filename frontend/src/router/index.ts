import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import BasicLayout from '../layouts/BasicLayout.vue'
import LawDetailView from '../views/law/LawDetailView.vue'
import LawImportView from '../views/law/LawImportView.vue'
import LawListView from '../views/law/LawListView.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'root',
    component: BasicLayout,
    redirect: '/laws',
    children: [
      { path: 'laws', name: 'law-list', component: LawListView },
      { path: 'laws/import', name: 'law-import', component: LawImportView },
      { path: 'laws/:lawId', name: 'law-detail', component: LawDetailView },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
