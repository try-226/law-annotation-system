<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { safeErrorMessage } from '../utils/errors'
import { authState, logout } from '../state/auth'
import { notify } from '../state/notifications'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const loggingOut = ref(false)

const roleLabel = computed(() => (authState.user?.role === 'ADMIN' ? '管理员' : '标注员'))
const pageTitle = computed(() => route.meta.title ?? '账户中心')

async function handleLogout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await logout()
    notify('已安全退出', 'success')
    await router.replace({ name: 'login' })
  } catch (error: unknown) {
    notify(safeErrorMessage(error, '退出失败，请稍后重试'), 'error')
  } finally {
    loggingOut.value = false
    menuOpen.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">法</span>
        <div><strong>法律条文标注系统</strong><small>LAW ANNOTATION</small></div>
      </div>
      <nav class="nav" aria-label="系统导航">
        <RouterLink v-if="authState.user?.role === 'ADMIN'" :to="{ name: 'users' }" class="nav-item">
          <span>▦</span>用户管理
        </RouterLink>
        <RouterLink v-if="authState.user?.role === 'ADMIN'" :to="{ name: 'law-list' }" class="nav-item">
          <span>§</span>法律管理
        </RouterLink>
        <RouterLink v-if="authState.user?.role === 'ADMIN'" :to="{ name: 'law-import' }" class="nav-item">
          <span>＋</span>导入法律
        </RouterLink>
        <RouterLink v-if="authState.user?.role === 'ADMIN'" :to="{ name: 'field-config' }" class="nav-item">
          <span>⚙</span>字段配置
        </RouterLink>
        <RouterLink v-if="authState.user?.role === 'ADMIN'" :to="{ name: 'law-recycle' }" class="nav-item">
          <span>♲</span>法律回收站
        </RouterLink>
        <RouterLink v-if="authState.user?.role === 'ADMIN'" :to="{ name: 'admin-tasks' }" class="nav-item">
          <span>☷</span>任务管理
        </RouterLink>
        <RouterLink v-if="authState.user?.role === 'ANNOTATOR'" :to="{ name: 'my-tasks' }" class="nav-item">
          <span>☑</span>我的任务
        </RouterLink>
      </nav>
    </aside>

    <div class="shell-main">
      <header class="topbar">
        <div class="breadcrumb">首页&nbsp; / &nbsp;<strong>{{ pageTitle }}</strong></div>
        <div class="user-menu">
          <button type="button" class="user-trigger" @click="menuOpen = !menuOpen">
            <span class="avatar">{{ authState.user?.name.slice(0, 1) }}</span>
            <span class="user-copy"><strong>{{ authState.user?.name }}</strong><small>{{ roleLabel }}</small></span>
            <span>⌄</span>
          </button>
          <div v-if="menuOpen" class="user-dropdown">
            <RouterLink :to="{ name: 'profile' }" @click="menuOpen = false">个人信息</RouterLink>
            <RouterLink :to="{ name: 'change-password' }" @click="menuOpen = false">修改密码</RouterLink>
            <button type="button" :disabled="loggingOut" @click="handleLogout">
              {{ loggingOut ? '退出中…' : '退出登录' }}
            </button>
          </div>
        </div>
      </header>
      <main class="content"><RouterView /></main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #f4f6f9;
  color: #1f2937;
}

.sidebar {
  position: fixed;
  z-index: 20;
  inset: 0 auto 0 0;
  display: flex;
  width: 232px;
  flex-direction: column;
  background: #17243a;
  color: #fff;
}

.brand { display: flex; height: 72px; align-items: center; gap: 11px; padding: 0 20px; border-bottom: 1px solid rgb(255 255 255 / 10%); }
.brand-mark { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 7px; background: #3977d1; font-weight: 700; }
.brand strong { display: block; font-size: 15px; }
.brand small { display: block; margin-top: 3px; color: #93a4be; font-size: 9px; letter-spacing: 1px; }
.nav { display: grid; gap: 5px; padding: 18px 12px; }
.nav-item { display: flex; height: 44px; align-items: center; gap: 11px; border-radius: 6px; color: #c6d0df; padding: 0 14px; font-size: 14px; }
.nav-item:hover, .nav-item.router-link-active { background: #2868c7; color: #fff; }
.nav-item span { width: 18px; text-align: center; }
.shell-main { min-width: 0; flex: 1; margin-left: 232px; }
.topbar { position: sticky; z-index: 10; top: 0; display: flex; height: 72px; align-items: center; justify-content: space-between; border-bottom: 1px solid #e1e6ed; background: #fff; padding: 0 28px; }
.breadcrumb { color: #788395; font-size: 14px; }
.breadcrumb strong { color: #354052; font-weight: 500; }
.user-menu { position: relative; }
.user-trigger { display: flex; align-items: center; gap: 9px; border: 0; background: transparent; color: #344054; padding: 6px; cursor: pointer; }
.avatar { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 50%; background: #eaf1fb; color: #2868c7; font-weight: 700; }
.user-copy { display: grid; min-width: 70px; text-align: left; }
.user-copy strong { font-size: 13px; }
.user-copy small { margin-top: 2px; color: #8a94a4; font-size: 11px; }
.user-dropdown { position: absolute; top: 54px; right: 0; display: grid; width: 150px; overflow: hidden; border: 1px solid #dfe5ec; border-radius: 7px; background: #fff; box-shadow: 0 8px 24px rgb(15 23 42 / 12%); }
.user-dropdown a, .user-dropdown button { border: 0; background: #fff; color: #344054; padding: 11px 15px; font-size: 13px; text-align: left; cursor: pointer; }
.user-dropdown a:hover, .user-dropdown button:hover { background: #f4f7fb; }
.user-dropdown button { border-top: 1px solid #edf0f4; color: #c93636; }
.content { padding: 28px; }

@media (max-width: 760px) {
  .sidebar { width: 72px; }
  .brand { justify-content: center; padding: 0; }
  .brand div, .nav-item:not(.router-link-active) { font-size: 0; }
  .nav-item { justify-content: center; padding: 0; font-size: 0; }
  .nav-item span { font-size: 16px; }
  .shell-main { margin-left: 72px; }
  .content { padding: 20px 16px; }
  .topbar { padding: 0 16px; }
  .breadcrumb, .user-copy { display: none; }
}
</style>
