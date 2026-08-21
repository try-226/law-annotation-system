<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'

import AppModal from '../components/AppModal.vue'
import {
  createUser,
  deleteUser,
  listUsers,
  resetUserPassword,
  setUserEnabled,
  updateUserName,
} from '../api/users'
import type { PageResponse, Role, User } from '../api/types'
import { authState, setCurrentUser } from '../state/auth'
import { notify } from '../state/notifications'
import { fieldErrors, parseFailure, safeErrorMessage } from '../utils/errors'
import {
  trimText,
  validateLoginAccount,
  validateName,
  validatePassword,
  validatePasswordConfirmation,
  validateSearch,
} from '../utils/validation'

type ModalType = 'create' | 'edit' | 'reset' | 'toggle' | 'delete' | null
type EnabledFilter = 'all' | 'enabled' | 'disabled'

const PAGE_SIZE = 20
const searchInput = ref('')
const appliedSearch = ref('')
const roleFilter = ref<'' | Role>('')
const enabledFilter = ref<EnabledFilter>('all')
const currentPage = ref(1)
const result = ref<PageResponse<User>>({ items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const listError = ref('')
const searchError = ref('')
let requestSequence = 0

const modal = ref<ModalType>(null)
const selectedUser = ref<User | null>(null)
const modalBusy = ref(false)
const modalError = ref('')
const createForm = reactive({ name: '', loginAccount: '', initialPassword: '', role: 'ANNOTATOR' as Role })
const createErrors = reactive({ name: '', loginAccount: '', initialPassword: '', role: '' })
const editName = ref('')
const editNameError = ref('')
const resetForm = reactive({ newPassword: '', confirmPassword: '' })
const resetErrors = reactive({ newPassword: '', confirmPassword: '' })

function queryEnabled(): boolean | undefined {
  if (enabledFilter.value === 'enabled') return true
  if (enabledFilter.value === 'disabled') return false
  return undefined
}

async function loadUsers(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  listError.value = ''
  try {
    const data = await listUsers({
      search: appliedSearch.value || undefined,
      role: roleFilter.value || undefined,
      enabled: queryEnabled(),
      page: currentPage.value - 1,
      size: PAGE_SIZE,
    })
    if (sequence === requestSequence) result.value = data
  } catch (error: unknown) {
    if (sequence === requestSequence) listError.value = safeErrorMessage(error, '用户列表加载失败，请稍后重试')
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function applySearch(): Promise<void> {
  searchError.value = validateSearch(searchInput.value) ?? ''
  if (searchError.value) return
  appliedSearch.value = trimText(searchInput.value)
  currentPage.value = 1
  await loadUsers()
}

async function goToPage(page: number): Promise<void> {
  if (page < 1 || page > result.value.totalPages || page === currentPage.value) return
  currentPage.value = page
  await loadUsers()
}

function clearModalState(): void {
  modal.value = null
  selectedUser.value = null
  modalBusy.value = false
  modalError.value = ''
}

function openCreate(): void {
  createForm.name = ''
  createForm.loginAccount = ''
  createForm.initialPassword = ''
  createForm.role = 'ANNOTATOR'
  Object.assign(createErrors, { name: '', loginAccount: '', initialPassword: '', role: '' })
  modalError.value = ''
  modal.value = 'create'
}

function openEdit(user: User): void {
  selectedUser.value = user
  editName.value = user.name
  editNameError.value = ''
  modalError.value = ''
  modal.value = 'edit'
}

function openReset(user: User): void {
  if (user.id === authState.user?.id) return
  selectedUser.value = user
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetErrors.newPassword = ''
  resetErrors.confirmPassword = ''
  modalError.value = ''
  modal.value = 'reset'
}

function openToggle(user: User): void {
  if (user.id === authState.user?.id && user.enabled) return
  selectedUser.value = user
  modalError.value = ''
  modal.value = 'toggle'
}

function openDelete(user: User): void {
  if (user.id === authState.user?.id) return
  selectedUser.value = user
  modalError.value = ''
  modal.value = 'delete'
}

async function handleMissing(error: unknown): Promise<boolean> {
  if (parseFailure(error).status !== 404) return false
  notify('目标用户已不存在，列表已刷新', 'error')
  clearModalState()
  await loadUsers()
  return true
}

async function submitCreate(): Promise<void> {
  createErrors.name = validateName(createForm.name) ?? ''
  createErrors.loginAccount = validateLoginAccount(createForm.loginAccount) ?? ''
  createErrors.initialPassword = validatePassword(createForm.initialPassword) ?? ''
  createErrors.role = createForm.role ? '' : '请选择角色'
  modalError.value = ''
  if (Object.values(createErrors).some(Boolean) || modalBusy.value) return
  modalBusy.value = true
  try {
    await createUser({
      name: trimText(createForm.name),
      loginAccount: trimText(createForm.loginAccount),
      initialPassword: createForm.initialPassword,
      role: createForm.role,
    })
    clearModalState()
    currentPage.value = 1
    await loadUsers()
    notify('用户创建成功', 'success')
  } catch (error: unknown) {
    const fields = fieldErrors(error)
    createErrors.name = fields.name ?? ''
    createErrors.loginAccount = fields.loginAccount ?? ''
    createErrors.initialPassword = fields.initialPassword ?? ''
    createErrors.role = fields.role ?? ''
    modalError.value = Object.values(createErrors).some(Boolean) ? '' : safeErrorMessage(error, '创建用户失败')
  } finally {
    modalBusy.value = false
  }
}

async function submitEdit(): Promise<void> {
  if (!selectedUser.value) return
  editNameError.value = validateName(editName.value) ?? ''
  modalError.value = ''
  if (editNameError.value || modalBusy.value) return
  modalBusy.value = true
  try {
    const updated = await updateUserName(selectedUser.value.id, trimText(editName.value))
    if (updated.id === authState.user?.id) setCurrentUser(updated)
    clearModalState()
    await loadUsers()
    notify('用户姓名已更新', 'success')
  } catch (error: unknown) {
    if (await handleMissing(error)) return
    editNameError.value = fieldErrors(error).name ?? ''
    modalError.value = editNameError.value ? '' : safeErrorMessage(error, '修改姓名失败')
  } finally {
    modalBusy.value = false
  }
}

async function submitReset(): Promise<void> {
  if (!selectedUser.value) return
  resetErrors.newPassword = validatePassword(resetForm.newPassword) ?? ''
  resetErrors.confirmPassword = validatePasswordConfirmation(resetForm.newPassword, resetForm.confirmPassword) ?? ''
  modalError.value = ''
  if (resetErrors.newPassword || resetErrors.confirmPassword || modalBusy.value) return
  modalBusy.value = true
  try {
    await resetUserPassword(selectedUser.value.id, resetForm)
    clearModalState()
    notify('密码重置成功', 'success')
  } catch (error: unknown) {
    if (await handleMissing(error)) return
    const fields = fieldErrors(error)
    resetErrors.newPassword = fields.newPassword ?? ''
    resetErrors.confirmPassword = fields.confirmPassword ?? ''
    modalError.value = resetErrors.newPassword || resetErrors.confirmPassword ? '' : safeErrorMessage(error, '重置密码失败')
  } finally {
    modalBusy.value = false
  }
}

async function submitToggle(): Promise<void> {
  if (!selectedUser.value || modalBusy.value) return
  modalBusy.value = true
  modalError.value = ''
  try {
    const enabling = !selectedUser.value.enabled
    await setUserEnabled(selectedUser.value.id, enabling)
    clearModalState()
    await loadUsers()
    notify(enabling ? '用户已启用' : '用户已停用', 'success')
  } catch (error: unknown) {
    if (await handleMissing(error)) return
    modalError.value = safeErrorMessage(error, '更新用户状态失败')
  } finally {
    modalBusy.value = false
  }
}

async function submitDelete(): Promise<void> {
  if (!selectedUser.value || modalBusy.value) return
  modalBusy.value = true
  modalError.value = ''
  try {
    await deleteUser(selectedUser.value.id)
    const shouldMoveBack = result.value.items.length === 1 && currentPage.value > 1
    clearModalState()
    if (shouldMoveBack) currentPage.value -= 1
    await loadUsers()
    notify('用户已删除', 'success')
  } catch (error: unknown) {
    if (await handleMissing(error)) return
    modalError.value = safeErrorMessage(error, '删除用户失败')
  } finally {
    modalBusy.value = false
  }
}

function roleLabel(role: Role): string {
  return role === 'ADMIN' ? '管理员' : '标注员'
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '--' : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}

watch([roleFilter, enabledFilter], () => {
  currentPage.value = 1
  void loadUsers()
})

onMounted(loadUsers)
</script>

<template>
  <div>
    <header class="page-heading heading-row">
      <div><h1>用户管理</h1><p>维护管理员与标注员账户、状态和登录凭据</p></div>
      <button class="button button--primary" type="button" @click="openCreate">＋ 创建用户</button>
    </header>

    <section class="panel filters">
      <form class="search-box" @submit.prevent="applySearch">
        <input v-model="searchInput" class="input" placeholder="搜索姓名 / 登录账号" aria-label="搜索用户" />
        <button class="button button--primary" type="submit">搜索</button>
      </form>
      <div class="filter-item"><label for="role-filter">角色</label><select id="role-filter" v-model="roleFilter" class="select"><option value="">全部角色</option><option value="ADMIN">管理员</option><option value="ANNOTATOR">标注员</option></select></div>
      <div class="filter-item"><label for="status-filter">状态</label><select id="status-filter" v-model="enabledFilter" class="select"><option value="all">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select></div>
      <p v-if="searchError" class="field-error filter-error">{{ searchError }}</p>
    </section>

    <section class="panel table-panel">
      <div v-if="loading" class="state"><span class="spinner" />正在加载用户…</div>
      <div v-else-if="listError" class="state state--error"><p>{{ listError }}</p><button class="button" type="button" @click="loadUsers">重新加载</button></div>
      <div v-else-if="result.items.length === 0" class="state"><div class="empty-icon">◎</div><p>没有符合条件的用户</p></div>
      <div v-else class="table-scroll">
        <table>
          <thead><tr><th>姓名</th><th>登录账号</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="user in result.items" :key="user.id">
              <td><strong>{{ user.name }}</strong><small v-if="user.id === authState.user?.id" class="self-label">当前账号</small></td>
              <td class="account">{{ user.loginAccount }}</td>
              <td>{{ roleLabel(user.role) }}</td>
              <td><span class="status" :class="user.enabled ? 'status--enabled' : 'status--disabled'">{{ user.enabled ? '启用' : '已停用' }}</span></td>
              <td>{{ formatDate(user.createdAt) }}</td>
              <td class="actions">
                <button class="button button--text" type="button" @click="openEdit(user)">编辑</button>
                <button class="button button--text" type="button" :disabled="user.id === authState.user?.id" :title="user.id === authState.user?.id ? '请在修改密码页面修改自己的密码' : ''" @click="openReset(user)">重置密码</button>
                <button class="button button--text" type="button" :disabled="user.id === authState.user?.id && user.enabled" :title="user.id === authState.user?.id && user.enabled ? '不能停用自己' : ''" @click="openToggle(user)">{{ user.enabled ? '停用' : '启用' }}</button>
                <button class="button button--text button--text-danger" type="button" :disabled="user.id === authState.user?.id" title="仅无业务历史用户可删除" @click="openDelete(user)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !listError && result.totalElements > 0" class="pagination">
        <span>共 {{ result.totalElements }} 条</span>
        <div><button class="button" type="button" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">上一页</button><span>第 {{ currentPage }} / {{ Math.max(result.totalPages, 1) }} 页</span><button class="button" type="button" :disabled="currentPage >= result.totalPages" @click="goToPage(currentPage + 1)">下一页</button></div>
      </footer>
    </section>

    <AppModal :open="modal === 'create'" title="创建用户" :busy="modalBusy" @close="clearModalState">
      <form id="create-user-form" class="form-grid" @submit.prevent="submitCreate">
        <div class="form-field"><label for="create-name">姓名 <span class="required">*</span></label><input id="create-name" v-model="createForm.name" class="input" placeholder="请输入姓名" :disabled="modalBusy" /><p v-if="createErrors.name" class="field-error">{{ createErrors.name }}</p></div>
        <div class="form-field"><label for="create-account">登录账号 <span class="required">*</span></label><input id="create-account" v-model="createForm.loginAccount" class="input" placeholder="4至32位字母、数字或 . _ -" :disabled="modalBusy" /><p class="form-note">账号创建后不可修改，英文字母大小写不区分唯一性。</p><p v-if="createErrors.loginAccount" class="field-error">{{ createErrors.loginAccount }}</p></div>
        <div class="form-field"><label for="create-password">初始密码 <span class="required">*</span></label><input id="create-password" v-model="createForm.initialPassword" class="input" type="password" autocomplete="new-password" placeholder="6至64个字符，不得包含空白" :disabled="modalBusy" /><p v-if="createErrors.initialPassword" class="field-error">{{ createErrors.initialPassword }}</p></div>
        <div class="form-field"><label for="create-role">角色 <span class="required">*</span></label><select id="create-role" v-model="createForm.role" class="select" :disabled="modalBusy"><option value="ADMIN">管理员</option><option value="ANNOTATOR">标注员</option></select><p class="form-note">新用户默认启用，角色创建后不可更改。</p><p v-if="createErrors.role" class="field-error">{{ createErrors.role }}</p></div>
        <p v-if="modalError" class="field-error">{{ modalError }}</p>
      </form>
      <template #footer><button class="button" type="button" :disabled="modalBusy" @click="clearModalState">取消</button><button class="button button--primary" type="submit" form="create-user-form" :disabled="modalBusy"><span v-if="modalBusy" class="spinner" />{{ modalBusy ? '创建中…' : '确认创建' }}</button></template>
    </AppModal>

    <AppModal :open="modal === 'edit'" title="修改用户姓名" :busy="modalBusy" @close="clearModalState">
      <form id="edit-user-form" class="form-grid" @submit.prevent="submitEdit">
        <div class="readonly-summary"><span>登录账号</span><strong>{{ selectedUser?.loginAccount }}</strong><small>账号与角色不可修改</small></div>
        <div class="form-field"><label for="edit-name">姓名 <span class="required">*</span></label><input id="edit-name" v-model="editName" class="input" :disabled="modalBusy" /><p v-if="editNameError" class="field-error">{{ editNameError }}</p></div>
        <p v-if="modalError" class="field-error">{{ modalError }}</p>
      </form>
      <template #footer><button class="button" type="button" :disabled="modalBusy" @click="clearModalState">取消</button><button class="button button--primary" type="submit" form="edit-user-form" :disabled="modalBusy"><span v-if="modalBusy" class="spinner" />保存</button></template>
    </AppModal>

    <AppModal :open="modal === 'reset'" title="重置用户密码" :busy="modalBusy" @close="clearModalState">
      <form id="reset-password-form" class="form-grid" @submit.prevent="submitReset">
        <p class="modal-copy">正在为 <strong>{{ selectedUser?.name }}</strong>（{{ selectedUser?.loginAccount }}）重置密码。</p>
        <div class="form-field"><label for="reset-password">新密码 <span class="required">*</span></label><input id="reset-password" v-model="resetForm.newPassword" class="input" type="password" autocomplete="new-password" placeholder="6至64个字符，不得包含空白" :disabled="modalBusy" /><p v-if="resetErrors.newPassword" class="field-error">{{ resetErrors.newPassword }}</p></div>
        <div class="form-field"><label for="reset-confirm">确认新密码 <span class="required">*</span></label><input id="reset-confirm" v-model="resetForm.confirmPassword" class="input" type="password" autocomplete="new-password" placeholder="再次输入新密码" :disabled="modalBusy" /><p v-if="resetErrors.confirmPassword" class="field-error">{{ resetErrors.confirmPassword }}</p></div>
        <p v-if="modalError" class="field-error">{{ modalError }}</p>
      </form>
      <template #footer><button class="button" type="button" :disabled="modalBusy" @click="clearModalState">取消</button><button class="button button--primary" type="submit" form="reset-password-form" :disabled="modalBusy"><span v-if="modalBusy" class="spinner" />确认重置</button></template>
    </AppModal>

    <AppModal :open="modal === 'toggle'" :title="selectedUser?.enabled ? '确认停用用户' : '确认启用用户'" :busy="modalBusy" @close="clearModalState">
      <p class="modal-copy">{{ selectedUser?.enabled ? `停用后，${selectedUser?.name} 将无法登录或接收新任务。` : `启用后，${selectedUser?.name} 将可以重新登录。` }}</p>
      <p class="form-note">系统将根据未结束任务、审核轮次及管理员保留规则进行最终校验。</p>
      <p v-if="modalError" class="field-error modal-error">{{ modalError }}</p>
      <template #footer><button class="button" type="button" :disabled="modalBusy" @click="clearModalState">取消</button><button class="button" :class="selectedUser?.enabled ? 'button--danger' : 'button--primary'" type="button" :disabled="modalBusy" @click="submitToggle"><span v-if="modalBusy" class="spinner" />{{ selectedUser?.enabled ? '确认停用' : '确认启用' }}</button></template>
    </AppModal>

    <AppModal :open="modal === 'delete'" title="确认删除用户" :busy="modalBusy" @close="clearModalState">
      <p class="modal-copy">确定删除 <strong>{{ selectedUser?.name }}</strong>（{{ selectedUser?.loginAccount }}）吗？此操作仅对从未参与业务的用户生效。</p>
      <p class="danger-note">删除后无法恢复；如用户已有业务历史，后端会拒绝删除，请改为停用。</p>
      <p v-if="modalError" class="field-error modal-error">{{ modalError }}</p>
      <template #footer><button class="button" type="button" :disabled="modalBusy" @click="clearModalState">取消</button><button class="button button--danger" type="button" :disabled="modalBusy" @click="submitDelete"><span v-if="modalBusy" class="spinner" />确认删除</button></template>
    </AppModal>
  </div>
</template>

<style scoped>
.heading-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.filters { position: relative; display: flex; align-items: end; gap: 16px; margin-bottom: 18px; padding: 18px; }
.search-box { display: flex; width: min(440px, 100%); gap: 8px; }.search-box .input { flex: 1; }
.filter-item { display: grid; min-width: 150px; gap: 6px; }.filter-item label { color: #5d6878; font-size: 12px; }
.filter-error { position: absolute; top: calc(100% + 3px); left: 18px; }
.table-panel { overflow: hidden; }
.table-scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; white-space: nowrap; }
th, td { border-bottom: 1px solid #e8ecf1; padding: 15px 16px; font-size: 13px; text-align: left; }
th { background: #f7f9fb; color: #657083; font-weight: 600; }
tbody tr:hover { background: #fbfcfe; }
td strong { font-weight: 600; }.account { color: #566274; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.self-label { margin-left: 7px; border-radius: 10px; background: #edf3fc; color: #2868c7; padding: 2px 6px; font-size: 10px; }
.status { display: inline-flex; border-radius: 12px; padding: 3px 9px; font-size: 12px; }.status--enabled { background: #e9f7ef; color: #18794e; }.status--disabled { background: #f1f2f4; color: #687386; }
.actions { min-width: 290px; }.actions .button:disabled { opacity: .35; }
.state { display: grid; min-height: 280px; place-items: center; align-content: center; gap: 12px; color: #788395; }.state--error { color: #a63c3c; }.state p { margin: 0; }.empty-icon { color: #a9b2c0; font-size: 38px; }
.pagination { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; color: #697386; font-size: 13px; }.pagination div { display: flex; align-items: center; gap: 12px; }.pagination .button { min-height: 32px; padding: 0 11px; }
.readonly-summary { display: grid; gap: 5px; border-radius: 6px; background: #f5f7fa; padding: 14px; }.readonly-summary span, .readonly-summary small { color: #7c8797; font-size: 12px; }
.modal-copy { margin: 0; color: #465264; line-height: 1.7; }.danger-note { margin: 16px 0 0; border-radius: 6px; background: #fff1f1; color: #a43b3b; padding: 12px; font-size: 13px; line-height: 1.6; }.modal-error { margin-top: 14px; }
@media (max-width: 900px) { .filters { flex-wrap: wrap; }.search-box { width: 100%; }.filter-item { flex: 1; }.heading-row { align-items: center; }.pagination { align-items: flex-start; gap: 14px; }.pagination div { flex-wrap: wrap; justify-content: flex-end; } }
@media (max-width: 600px) { .heading-row { align-items: stretch; flex-direction: column; }.filters { align-items: stretch; flex-direction: column; }.filter-item { width: 100%; }.pagination { flex-direction: column; }.pagination div { justify-content: flex-start; } }
</style>
