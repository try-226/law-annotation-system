<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  createFieldDefinition,
  deactivateFieldDefinition,
  listFieldDefinitions,
  updateFieldDefinition,
  type FieldDefinition,
  type FieldDefinitionStatus,
  type FieldType,
} from '../../api/fieldDefinitions'
import type { PageResponse } from '../../api/types'
import AppModal from '../../components/AppModal.vue'
import { notify } from '../../state/notifications'
import { fieldErrors, parseFailure, safeErrorMessage } from '../../utils/errors'

type ModalType = 'form' | 'deactivate' | null

const PAGE_SIZE = 20
const result = ref<PageResponse<FieldDefinition>>({
  items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0,
})
const loading = ref(false)
const listError = ref('')
const modal = ref<ModalType>(null)
const selected = ref<FieldDefinition | null>(null)
const modalBusy = ref(false)
const modalError = ref('')
const errors = reactive({ name: '', displayName: '', fieldType: '', options: '', status: '' })
const form = reactive({
  name: '',
  displayName: '',
  description: '',
  fieldType: 'TEXT' as FieldType,
  required: false,
  optionsText: '',
  status: 'ACTIVE' as FieldDefinitionStatus,
})

const typeLabels: Record<FieldType, string> = {
  TEXT: '文本',
  NUMBER: '数字',
  DATE: '日期',
  SELECT: '单选',
  MULTI_SELECT: '多选',
  BOOLEAN: '布尔值',
}

function isSelectable(type: FieldType): boolean {
  return type === 'SELECT' || type === 'MULTI_SELECT'
}

function optionsFromText(value: string): string[] {
  return value
    .split(/[\n,，]/)
    .map((option) => option.trim())
    .filter(Boolean)
}

function operationError(error: unknown, fallback: string): string {
  const failure = parseFailure(error)
  if (failure.status === 404) return failure.userMessage || '字段配置已不存在'
  return safeErrorMessage(error, fallback)
}

async function loadFields(page = result.value.page): Promise<void> {
  loading.value = true
  listError.value = ''
  try {
    result.value = await listFieldDefinitions(page, PAGE_SIZE)
  } catch (error: unknown) {
    listError.value = operationError(error, '字段配置加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function resetErrors(): void {
  Object.assign(errors, { name: '', displayName: '', fieldType: '', options: '', status: '' })
  modalError.value = ''
}

function closeModal(): void {
  modal.value = null
  selected.value = null
  modalBusy.value = false
  resetErrors()
}

function openCreate(): void {
  selected.value = null
  Object.assign(form, {
    name: '', displayName: '', description: '', fieldType: 'TEXT',
    required: false, optionsText: '', status: 'ACTIVE',
  })
  resetErrors()
  modal.value = 'form'
}

function openEdit(field: FieldDefinition): void {
  selected.value = field
  Object.assign(form, {
    name: field.name,
    displayName: field.displayName,
    description: field.description ?? '',
    fieldType: field.fieldType,
    required: field.required,
    optionsText: field.options.join('\n'),
    status: field.status,
  })
  resetErrors()
  modal.value = 'form'
}

function openDeactivate(field: FieldDefinition): void {
  if (field.status === 'INACTIVE') return
  selected.value = field
  resetErrors()
  modal.value = 'deactivate'
}

function validateForm(): boolean {
  resetErrors()
  if (!form.name.trim()) errors.name = '系统字段名称不能为空'
  if (!form.displayName.trim()) errors.displayName = '显示名称不能为空'
  if (!form.fieldType) errors.fieldType = '请选择字段类型'
  return !Object.values(errors).some(Boolean)
}

async function submitForm(): Promise<void> {
  if (!validateForm() || modalBusy.value) return
  const editing = selected.value !== null
  const returnPage = result.value.page
  modalBusy.value = true
  const options = isSelectable(form.fieldType) ? optionsFromText(form.optionsText) : []
  try {
    if (selected.value) {
      await updateFieldDefinition(selected.value.id, {
        displayName: form.displayName.trim(),
        description: form.description.trim() || null,
        required: form.required,
        options,
        status: form.status,
      })
      notify('字段配置已更新', 'success')
    } else {
      await createFieldDefinition({
        name: form.name.trim(),
        displayName: form.displayName.trim(),
        description: form.description.trim() || null,
        fieldType: form.fieldType,
        required: form.required,
        options,
      })
      notify('字段创建成功', 'success')
    }
    closeModal()
    await loadFields(editing ? returnPage : 0)
  } catch (error: unknown) {
    const serverErrors = fieldErrors(error)
    errors.name = serverErrors.name ?? ''
    errors.displayName = serverErrors.displayName ?? ''
    errors.fieldType = serverErrors.fieldType ?? ''
    errors.options = serverErrors.options ?? Object.entries(serverErrors)
      .find(([path]) => path.startsWith('options['))?.[1] ?? ''
    errors.status = serverErrors.status ?? ''
    modalError.value = Object.values(errors).some(Boolean)
      ? ''
      : operationError(error, selected.value ? '字段更新失败' : '字段创建失败')
  } finally {
    modalBusy.value = false
  }
}

async function submitDeactivate(): Promise<void> {
  if (!selected.value || modalBusy.value) return
  modalBusy.value = true
  modalError.value = ''
  try {
    await deactivateFieldDefinition(selected.value.id)
    closeModal()
    await loadFields(result.value.page)
    notify('字段已停用', 'success')
  } catch (error: unknown) {
    modalError.value = operationError(error, '停用字段失败')
  } finally {
    modalBusy.value = false
  }
}

async function goToPage(page: number): Promise<void> {
  if (page < 0 || page >= result.value.totalPages || page === result.value.page) return
  await loadFields(page)
}

onMounted(() => loadFields(0))
</script>

<template>
  <div>
    <header class="page-heading heading-row">
      <div><h1>字段配置</h1><p>定义后续标注业务可使用的系统字段，字段名称创建后不可修改</p></div>
      <button class="button button--primary" type="button" @click="openCreate">＋ 新增字段</button>
    </header>

    <section class="panel table-panel">
      <div v-if="loading" class="state"><span class="spinner" />正在加载字段配置…</div>
      <div v-else-if="listError" class="state state--error">
        <p>{{ listError }}</p><button class="button" type="button" @click="loadFields()">重新加载</button>
      </div>
      <div v-else-if="result.items.length === 0" class="state">
        <div class="empty-icon">◇</div><p>尚未配置字段</p>
      </div>
      <div v-else class="table-scroll">
        <table>
          <thead><tr><th>字段名称</th><th>显示名称</th><th>类型</th><th>是否必填</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="field in result.items" :key="field.id">
              <td class="system-name">{{ field.name }}</td>
              <td><strong>{{ field.displayName }}</strong></td>
              <td>{{ typeLabels[field.fieldType] }}</td>
              <td>{{ field.required ? '必填' : '选填' }}</td>
              <td><span class="status" :class="field.status === 'ACTIVE' ? 'status--active' : 'status--inactive'">{{ field.status === 'ACTIVE' ? '启用' : '已停用' }}</span></td>
              <td class="actions">
                <button class="button button--text" type="button" @click="openEdit(field)">编辑</button>
                <button class="button button--text button--text-danger" type="button" :disabled="field.status === 'INACTIVE'" @click="openDeactivate(field)">停用</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !listError && result.totalElements > 0" class="pagination">
        <span>共 {{ result.totalElements }} 个字段</span>
        <div>
          <button class="button" type="button" :disabled="result.page === 0" @click="goToPage(result.page - 1)">上一页</button>
          <span>第 {{ result.page + 1 }} / {{ Math.max(result.totalPages, 1) }} 页</span>
          <button class="button" type="button" :disabled="result.page + 1 >= result.totalPages" @click="goToPage(result.page + 1)">下一页</button>
        </div>
      </footer>
    </section>

    <AppModal :open="modal === 'form'" :title="selected ? '编辑字段' : '新增字段'" :busy="modalBusy" width="600px" @close="closeModal">
      <form id="field-definition-form" class="form-grid" @submit.prevent="submitForm">
        <div class="form-field">
          <label for="field-name">系统字段名称 <span class="required">*</span></label>
          <input id="field-name" v-model="form.name" class="input" placeholder="例如 penalty_amount" :disabled="modalBusy || !!selected" />
          <p class="form-note">作为稳定 identity 使用，创建后不可修改。</p>
          <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
        </div>
        <div class="form-field">
          <label for="field-display-name">显示名称 <span class="required">*</span></label>
          <input id="field-display-name" v-model="form.displayName" class="input" placeholder="例如 处罚金额" :disabled="modalBusy" />
          <p v-if="errors.displayName" class="field-error">{{ errors.displayName }}</p>
        </div>
        <div class="form-field">
          <label for="field-type">字段类型 <span class="required">*</span></label>
          <select id="field-type" v-model="form.fieldType" class="select" :disabled="modalBusy || !!selected">
            <option v-for="(label, value) in typeLabels" :key="value" :value="value">{{ label }}</option>
          </select>
          <p v-if="selected" class="form-note">字段类型创建后不可修改。</p>
          <p v-if="errors.fieldType" class="field-error">{{ errors.fieldType }}</p>
        </div>
        <div class="form-field">
          <label for="field-description">描述</label>
          <textarea id="field-description" v-model="form.description" class="textarea" rows="3" placeholder="说明字段含义或填写要求" :disabled="modalBusy" />
        </div>
        <div v-if="isSelectable(form.fieldType)" class="form-field">
          <label for="field-options">选项</label>
          <textarea id="field-options" v-model="form.optionsText" class="textarea" rows="5" placeholder="每行一个选项，也可用逗号分隔" :disabled="modalBusy" />
          <p class="form-note">选项将自动去除首尾空格，重复选项会被拒绝。</p>
          <p v-if="errors.options" class="field-error">{{ errors.options }}</p>
        </div>
        <label class="checkbox-row"><input v-model="form.required" type="checkbox" :disabled="modalBusy" />该字段必填</label>
        <div v-if="selected" class="form-field">
          <label for="field-status">状态</label>
          <select id="field-status" v-model="form.status" class="select" :disabled="modalBusy">
            <option value="ACTIVE">启用</option><option value="INACTIVE">停用</option>
          </select>
          <p v-if="errors.status" class="field-error">{{ errors.status }}</p>
        </div>
        <p v-if="modalError" class="field-error">{{ modalError }}</p>
      </form>
      <template #footer>
        <button class="button" type="button" :disabled="modalBusy" @click="closeModal">取消</button>
        <button class="button button--primary" type="submit" form="field-definition-form" :disabled="modalBusy"><span v-if="modalBusy" class="spinner" />保存</button>
      </template>
    </AppModal>

    <AppModal :open="modal === 'deactivate'" title="确认停用字段" :busy="modalBusy" @close="closeModal">
      <p class="modal-copy">确定停用 <strong>{{ selected?.displayName }}</strong>（{{ selected?.name }}）吗？</p>
      <p class="warning-note">字段记录不会被物理删除；历史业务仍可通过稳定字段名称识别该配置。</p>
      <p v-if="modalError" class="field-error modal-error">{{ modalError }}</p>
      <template #footer>
        <button class="button" type="button" :disabled="modalBusy" @click="closeModal">取消</button>
        <button class="button button--danger" type="button" :disabled="modalBusy" @click="submitDeactivate"><span v-if="modalBusy" class="spinner" />确认停用</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.heading-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.table-panel { overflow: hidden; }
.table-scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; white-space: nowrap; }
th, td { border-bottom: 1px solid #e8ecf1; padding: 15px 16px; font-size: 13px; text-align: left; }
th { background: #f7f9fb; color: #657083; font-weight: 600; }
tbody tr:hover { background: #fbfcfe; }
td strong { font-weight: 600; }
.system-name { color: #566274; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.status { display: inline-flex; border-radius: 12px; padding: 3px 9px; font-size: 12px; }
.status--active { background: #e9f7ef; color: #18794e; }
.status--inactive { background: #f1f2f4; color: #687386; }
.actions { min-width: 120px; }
.state { display: grid; min-height: 280px; place-items: center; align-content: center; gap: 12px; color: #788395; }
.state--error { color: #a63c3c; }
.state p { margin: 0; }
.empty-icon { color: #a9b2c0; font-size: 38px; }
.pagination { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; color: #697386; font-size: 13px; }
.pagination div { display: flex; align-items: center; gap: 12px; }
.pagination .button { min-height: 32px; padding: 0 11px; }
.textarea { width: 100%; resize: vertical; border: 1px solid #cfd6df; border-radius: 6px; outline: none; background: #fff; color: #263244; padding: 10px 12px; line-height: 1.55; }
.textarea:focus { border-color: #3b78d2; box-shadow: 0 0 0 3px rgb(59 120 210 / 12%); }
.textarea:disabled { background: #f4f6f8; color: #667085; }
.checkbox-row { display: flex; align-items: center; gap: 9px; color: #344054; font-size: 14px; }
.checkbox-row input { width: 16px; height: 16px; }
.modal-copy { margin: 0; color: #465264; line-height: 1.7; }
.warning-note { margin: 16px 0 0; border-radius: 6px; background: #fff8e7; color: #8a5a13; padding: 12px; font-size: 13px; line-height: 1.6; }
.modal-error { margin-top: 14px; }
@media (max-width: 700px) {
  .heading-row { align-items: stretch; flex-direction: column; }
  .pagination { align-items: flex-start; flex-direction: column; gap: 12px; }
}
</style>
