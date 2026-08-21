<script setup lang="ts">
import { reactive, ref } from 'vue'

import { changePassword } from '../api/auth'
import { notify } from '../state/notifications'
import { fieldErrors, safeErrorMessage } from '../utils/errors'
import { validatePassword, validatePasswordConfirmation } from '../utils/validation'

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const errors = reactive({ oldPassword: '', newPassword: '', confirmPassword: '', submit: '' })
const saving = ref(false)

async function save(): Promise<void> {
  errors.oldPassword = validatePassword(form.oldPassword) ?? ''
  errors.newPassword = validatePassword(form.newPassword) ?? ''
  errors.confirmPassword = validatePasswordConfirmation(form.newPassword, form.confirmPassword) ?? ''
  errors.submit = ''
  if (errors.oldPassword || errors.newPassword || errors.confirmPassword || saving.value) return
  saving.value = true
  try {
    await changePassword(form)
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    notify('密码修改成功，当前登录保持有效', 'success')
  } catch (error: unknown) {
    const fields = fieldErrors(error)
    errors.oldPassword = fields.oldPassword ?? ''
    errors.newPassword = fields.newPassword ?? ''
    errors.confirmPassword = fields.confirmPassword ?? ''
    errors.submit = errors.oldPassword || errors.newPassword || errors.confirmPassword ? '' : safeErrorMessage(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <header class="page-heading"><h1>修改密码</h1><p>定期更新密码有助于保护账户安全</p></header>
    <section class="panel password-card">
      <form class="form-grid" @submit.prevent="save">
        <div class="form-field"><label for="old-password">旧密码 <span class="required">*</span></label><input id="old-password" v-model="form.oldPassword" class="input" type="password" autocomplete="current-password" placeholder="请输入当前密码" :disabled="saving" /><p v-if="errors.oldPassword" class="field-error">{{ errors.oldPassword }}</p></div>
        <div class="form-field"><label for="new-password">新密码 <span class="required">*</span></label><input id="new-password" v-model="form.newPassword" class="input" type="password" autocomplete="new-password" placeholder="6至64个字符，不得包含空白" :disabled="saving" /><p v-if="errors.newPassword" class="field-error">{{ errors.newPassword }}</p></div>
        <div class="form-field"><label for="confirm-password">确认新密码 <span class="required">*</span></label><input id="confirm-password" v-model="form.confirmPassword" class="input" type="password" autocomplete="new-password" placeholder="再次输入新密码" :disabled="saving" /><p v-if="errors.confirmPassword" class="field-error">{{ errors.confirmPassword }}</p></div>
        <p class="form-note">修改成功后不强制退出当前登录。</p>
        <p v-if="errors.submit" class="field-error">{{ errors.submit }}</p>
        <div><button class="button button--primary" type="submit" :disabled="saving"><span v-if="saving" class="spinner" />{{ saving ? '提交中…' : '确认修改' }}</button></div>
      </form>
    </section>
  </div>
</template>

<style scoped>.password-card { width: min(620px, 100%); padding: 28px; }</style>
