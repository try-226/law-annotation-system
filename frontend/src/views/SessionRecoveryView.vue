<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

import { landingRouteName } from '../router'
import { authState, restoreSession } from '../state/auth'

const route = useRoute()
const router = useRouter()

async function retry(): Promise<void> {
  await restoreSession(true)
  if (authState.status === 'anonymous') {
    await router.replace({ name: 'login' })
    return
  }
  if (authState.status === 'authenticated' && authState.user) {
    const requested = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = requested.startsWith('/') && !requested.startsWith('//') && requested !== '/session-recovery'
      ? requested
      : { name: landingRouteName(authState.user.role) }
    await router.replace(target)
  }
}
</script>

<template>
  <main class="recovery-page">
    <section class="recovery-card">
      <div class="warning">!</div>
      <h1>登录状态恢复失败</h1>
      <p>暂时无法连接服务确认登录状态。我们没有清除当前会话，请检查网络后重试。</p>
      <button class="button button--primary" type="button" :disabled="authState.status === 'restoring'" @click="retry">
        <span v-if="authState.status === 'restoring'" class="spinner" />
        {{ authState.status === 'restoring' ? '正在重试…' : '重新尝试' }}
      </button>
    </section>
  </main>
</template>

<style scoped>
.recovery-page { display: grid; min-height: 100vh; place-items: center; background: #f4f6f9; padding: 24px; }
.recovery-card { width: min(460px, 100%); border: 1px solid #e1e6ed; border-radius: 10px; background: #fff; padding: 38px; text-align: center; box-shadow: 0 10px 30px rgb(15 23 42 / 7%); }
.warning { display: grid; width: 52px; height: 52px; margin: 0 auto 18px; place-items: center; border-radius: 50%; background: #fff1d8; color: #a76300; font-size: 26px; font-weight: 700; }
.recovery-card h1 { margin: 0 0 12px; font-size: 23px; }.recovery-card p { margin: 0 0 26px; color: #707b8d; line-height: 1.7; }
</style>
