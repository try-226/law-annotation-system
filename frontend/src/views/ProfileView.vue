<script setup lang="ts">
import { reactive, ref } from 'vue'

import { updateProfile } from '../api/auth'
import { authState, setCurrentUser } from '../state/auth'
import { notify } from '../state/notifications'
import { fieldErrors, safeErrorMessage } from '../utils/errors'
import { trimText, validateName } from '../utils/validation'

const name = ref(authState.user?.name ?? '')
const errors = reactive({ name: '', submit: '' })
const saving = ref(false)
const roleLabel = authState.user?.role === 'ADMIN' ? '管理员' : '标注员'

async function save(): Promise<void> {
  errors.name = validateName(name.value) ?? ''
  errors.submit = ''
  if (errors.name || saving.value) return
  saving.value = true
  try {
    const user = await updateProfile(trimText(name.value))
    setCurrentUser(user)
    name.value = user.name
    notify('个人信息已更新', 'success')
  } catch (error: unknown) {
    errors.name = fieldErrors(error).name ?? ''
    errors.submit = errors.name ? '' : safeErrorMessage(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <header class="page-heading"><h1>个人信息</h1><p>查看账户信息并维护您的姓名</p></header>
    <section class="panel profile-card">
      <form class="form-grid" @submit.prevent="save">
        <div class="form-field">
          <label for="profile-name">姓名 <span class="required">*</span></label>
          <input id="profile-name" v-model="name" class="input" maxlength="80" :disabled="saving" />
          <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
        </div>
        <div class="form-field"><label for="profile-account">登录账号</label><input id="profile-account" class="input" :value="authState.user?.loginAccount" disabled /></div>
        <div class="form-field"><label for="profile-role">角色</label><input id="profile-role" class="input" :value="roleLabel" disabled /></div>
        <p class="form-note">登录账号和角色创建后不可修改。</p>
        <p v-if="errors.submit" class="field-error">{{ errors.submit }}</p>
        <div><button class="button button--primary" type="submit" :disabled="saving"><span v-if="saving" class="spinner" />{{ saving ? '保存中…' : '保存姓名' }}</button></div>
      </form>
    </section>
  </div>
</template>

<style scoped>.profile-card { width: min(620px, 100%); padding: 28px; }</style>
