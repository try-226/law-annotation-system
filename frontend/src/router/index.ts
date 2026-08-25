import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import type { Role } from '../api/types'
import BasicLayout from '../layouts/BasicLayout.vue'
import { authState, restoreSession } from '../state/auth'
import LawDetailView from '../views/law/LawDetailView.vue'
import LawImportView from '../views/law/LawImportView.vue'
import LawListView from '../views/law/LawListView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: Role[]
    title?: string
    accountLanding?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
  },
  {
    path: '/session-recovery',
    name: 'session-recovery',
    component: () => import('../views/SessionRecoveryView.vue'),
  },
  {
    path: '/',
    component: BasicLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'account-home',
        component: () => import('../views/RouterLandingView.vue'),
        meta: { accountLanding: true },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: { roles: ['ADMIN'], title: '用户管理' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { title: '个人信息' },
      },
      {
        path: 'profile/password',
        name: 'change-password',
        component: () => import('../views/ChangePasswordView.vue'),
        meta: { title: '修改密码' },
      },
      {
        path: 'laws',
        name: 'law-list',
        component: LawListView,
        meta: { roles: ['ADMIN'], title: '法律管理' },
      },
      {
        path: 'laws/import',
        name: 'law-import',
        component: LawImportView,
        meta: { roles: ['ADMIN'], title: '导入法律' },
      },
      {
        path: 'laws/recycle',
        name: 'law-recycle',
        component: () => import('../views/recycle/LawRecycleView.vue'),
        meta: { roles: ['ADMIN'], title: '法律回收站' },
      },
      {
        path: 'field-config',
        name: 'field-config',
        component: () => import('../views/field/FieldConfigView.vue'),
        meta: { roles: ['ADMIN'], title: '字段配置' },
      },
      {
        path: 'laws/:lawId',
        name: 'law-detail',
        component: LawDetailView,
        meta: { roles: ['ADMIN'], title: '法律详情' },
      },
      {
        path: 'tasks',
        name: 'admin-tasks',
        component: () => import('../views/task/AdminTaskListView.vue'),
        meta: { roles: ['ADMIN'], title: '任务管理' },
      },
      {
        path: 'tasks/:taskId',
        name: 'admin-task-detail',
        component: () => import('../views/task/TaskDetailView.vue'),
        meta: { roles: ['ADMIN'], title: '任务详情' },
      },
      {
        path: 'my-tasks',
        name: 'my-tasks',
        component: () => import('../views/task/MyTasksView.vue'),
        meta: { roles: ['ANNOTATOR'], title: '我的任务' },
      },
      {
        path: 'my-tasks/:taskId',
        name: 'my-task-detail',
        component: () => import('../views/task/TaskDetailView.vue'),
        meta: { roles: ['ANNOTATOR'], title: '任务详情' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export function landingRouteName(role: Role): 'users' | 'my-tasks' {
  return role === 'ADMIN' ? 'users' : 'my-tasks'
}

router.beforeEach(async (to) => {
  if (authState.status === 'unknown') {
    await restoreSession()
  }

  if (authState.status === 'restore-error') {
    if (to.name === 'session-recovery') {
      return true
    }
    return { name: 'session-recovery', query: { redirect: to.fullPath } }
  }

  if (to.name === 'session-recovery') {
    if (authState.status === 'authenticated' && authState.user) {
      return { name: landingRouteName(authState.user.role) }
    }
    return { name: 'login' }
  }

  if (to.name === 'login') {
    if (authState.status === 'authenticated' && authState.user) {
      return { name: landingRouteName(authState.user.role) }
    }
    return true
  }

  if (to.meta.requiresAuth && (authState.status !== 'authenticated' || !authState.user)) {
    return { name: 'login' }
  }

  if (to.meta.accountLanding && authState.user) {
    return { name: landingRouteName(authState.user.role) }
  }

  if (to.meta.roles?.length && authState.user && !to.meta.roles.includes(authState.user.role)) {
    return { name: landingRouteName(authState.user.role) }
  }

  return true
})

export default router
