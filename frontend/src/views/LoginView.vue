<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { Role } from '../api/types'
import { landingRouteName } from '../router'
import { login } from '../state/auth'
import { trimText } from '../utils/validation'
import { safeErrorMessage } from '../utils/errors'

const router = useRouter()
const role = ref<Role>('ADMIN')
const form = reactive({ loginAccount: '', password: '' })
const errors = reactive({ loginAccount: '', password: '', submit: '' })
const submitting = ref(false)

function selectRole(value: Role): void {
  role.value = value
  errors.submit = ''
}

async function handleSubmit(): Promise<void> {
  errors.loginAccount = trimText(form.loginAccount) ? '' : '请输入登录账号'
  errors.password = form.password ? '' : '请输入密码'
  errors.submit = ''
  if (errors.loginAccount || errors.password || submitting.value) return

  submitting.value = true
  try {
    const user = await login({
      loginAccount: trimText(form.loginAccount),
      password: form.password,
      expectedRole: role.value,
    })
    await router.replace({ name: landingRouteName(user.role) })
  } catch (error: unknown) {
    errors.submit = safeErrorMessage(error, '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="intro">
      <div class="intro-content">
        <div class="intro-brand"><span>法</span>法律条文标注系统</div>
        <h1>让法律条文管理与标注<br />清晰、可靠、可追溯</h1>
        <ul>
          <li>完整法律导入与结构化管理</li>
          <li>逐条标注、审核、驳回与复审</li>
          <li>内容版本与标注版本全程可追溯</li>
        </ul>
      </div>
    </section>

    <section class="login-area">
      <form class="login-card" @submit.prevent="handleSubmit">
        <header><h2>账号登录</h2><p>请选择角色并输入账号密码</p></header>
        <div class="role-tabs" role="tablist" aria-label="登录角色">
          <button type="button" :class="{ active: role === 'ADMIN' }" @click="selectRole('ADMIN')">管理员登录</button>
          <button type="button" :class="{ active: role === 'ANNOTATOR' }" @click="selectRole('ANNOTATOR')">标注员登录</button>
        </div>
        <div class="form-grid">
          <div class="form-field">
            <label for="login-account">登录账号 <span class="required">*</span></label>
            <input id="login-account" v-model="form.loginAccount" class="input" autocomplete="username" placeholder="请输入登录账号" :disabled="submitting" />
            <p v-if="errors.loginAccount" class="field-error">{{ errors.loginAccount }}</p>
          </div>
          <div class="form-field">
            <label for="login-password">登录密码 <span class="required">*</span></label>
            <input id="login-password" v-model="form.password" class="input" type="password" autocomplete="current-password" placeholder="请输入密码" :disabled="submitting" />
            <p v-if="errors.password" class="field-error">{{ errors.password }}</p>
          </div>
          <div v-if="errors.submit" class="login-error" role="alert">{{ errors.submit }}</div>
          <button class="button button--primary submit-button" type="submit" :disabled="submitting">
            <span v-if="submitting" class="spinner" />{{ submitting ? '登录中…' : '登录' }}
          </button>
        </div>
        <p class="session-tip">会话失效后将返回本页面，请重新登录</p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100vh; grid-template-columns: 1.1fr .9fr; background: #fff; }
.intro { position: relative; display: grid; overflow: hidden; place-items: center; background: linear-gradient(145deg, #172b4c, #245ca8); color: #fff; }
.intro::before, .intro::after { position: absolute; width: 440px; height: 440px; border: 1px solid rgb(255 255 255 / 9%); border-radius: 50%; content: ''; }
.intro::before { top: -210px; left: -140px; }.intro::after { right: -220px; bottom: -230px; }
.intro-content { position: relative; z-index: 1; width: min(520px, 74%); }
.intro-brand { display: flex; align-items: center; gap: 12px; font-size: 18px; font-weight: 650; }
.intro-brand span { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 8px; background: #fff; color: #2868c7; }
.intro h1 { margin: 80px 0 36px; font-size: 36px; line-height: 1.45; letter-spacing: 1px; }
.intro ul { display: grid; gap: 20px; margin: 0; padding: 0; list-style: none; color: #d8e4f5; }
.intro li::before { margin-right: 12px; color: #8bb8f3; content: '✓'; }
.login-area { display: grid; place-items: center; padding: 32px; }
.login-card { width: min(420px, 100%); }
.login-card header h2 { margin: 0 0 9px; font-size: 28px; }.login-card header p { margin: 0; color: #7b8494; }
.role-tabs { display: grid; grid-template-columns: 1fr 1fr; margin: 34px 0 26px; border-bottom: 1px solid #dce2ea; }
.role-tabs button { height: 46px; border: 0; border-bottom: 2px solid transparent; border-radius: 0; background: transparent; color: #697386; cursor: pointer; }
.role-tabs button.active { border-color: #2868c7; color: #2868c7; font-weight: 650; }
.login-error { border-radius: 6px; background: #fff1f1; color: #b93737; padding: 11px 13px; font-size: 13px; }
.submit-button { width: 100%; height: 44px; margin-top: 4px; }
.session-tip { margin: 22px 0 0; color: #9aa2af; font-size: 12px; text-align: center; }
@media (max-width: 820px) { .login-page { grid-template-columns: 1fr; }.intro { display: none; }.login-area { background: #f4f7fb; }.login-card { border-radius: 10px; background: #fff; padding: 28px; box-shadow: 0 8px 30px rgb(15 23 42 / 8%); } }
</style>
