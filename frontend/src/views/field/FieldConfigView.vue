<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getFieldConfig,
  updateFieldRequired,
  type FieldConfig,
  type FieldConfigItem,
  type FieldValueKind,
} from '../../api/fieldConfig'
import { apiErrorMessage } from '../../api/laws'

const config = ref<FieldConfig>({ fields: [] })
const loading = ref(false)
const error = ref('')
const savingFieldKey = ref('')

const overallFields = computed(() => config.value.fields.filter((field) => field.scope === 'OVERALL'))
const articleFields = computed(() => config.value.fields.filter((field) => field.scope === 'ARTICLE'))
const typeLabels: Record<FieldValueKind, string> = { SELECT: '单选', TEXT: '文本', TEXTAREA: '长文本' }

async function load() {
  loading.value = true
  error.value = ''
  try { config.value = await getFieldConfig() }
  catch (caught) { error.value = apiErrorMessage(caught) }
  finally { loading.value = false }
}

async function changeRequired(field: FieldConfigItem, event: Event) {
  if (!field.configurable || savingFieldKey.value) return
  const checkbox = event.target as HTMLInputElement
  savingFieldKey.value = field.fieldKey
  error.value = ''
  try { config.value = await updateFieldRequired(field.fieldKey, checkbox.checked) }
  catch (caught) {
    checkbox.checked = field.required
    error.value = apiErrorMessage(caught)
  } finally { savingFieldKey.value = '' }
}

onMounted(() => void load())
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>标注字段配置</h1><p class="muted">字段定义固定，配置仅影响之后创建的任务</p></div>
      <RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律管理</RouterLink>
    </div>
    <div class="card">
      <div class="field-config-heading">
        <p class="muted">只能切换可配置字段是否必填，不能新增、删除、改名、改类型或排序。</p>
        <span class="config-note">核心字段始终必填</span>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <div v-if="loading" class="empty">正在读取字段配置…</div>
      <div v-else class="field-config-grid">
        <section v-for="group in [{ title: '整体信息字段', fields: overallFields }, { title: '具体法条字段', fields: articleFields }]" :key="group.title" class="config-section">
          <h2>{{ group.title }}</h2>
          <div class="table-scroll"><table><thead><tr><th>字段名称</th><th>类型</th><th>是否必填</th></tr></thead><tbody>
            <tr v-for="field in group.fields" :key="field.fieldKey">
              <td>{{ field.displayName }}</td><td>{{ typeLabels[field.type] }}</td>
              <td><span v-if="!field.configurable" class="core-required">必填（核心）</span><label v-else class="required-toggle"><input type="checkbox" :checked="field.required" :disabled="Boolean(savingFieldKey)" :aria-label="`${field.displayName}设为必填`" @change="changeRequired(field, $event)" />{{ savingFieldKey === field.fieldKey ? '保存中…' : field.required ? '必填' : '选填' }}</label></td>
            </tr>
          </tbody></table></div>
        </section>
      </div>
    </div>
  </section>
</template>

<style src="../law/law.css"></style>
