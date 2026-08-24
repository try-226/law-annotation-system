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

const fieldConfig = ref<FieldConfig>({ fields: [] })
const loading = ref(true)
const error = ref('')
const message = ref('')
const savingFieldKey = ref('')

const overallFields = computed(() => fieldConfig.value.fields.filter((field) => field.scope === 'OVERALL'))
const articleFields = computed(() => fieldConfig.value.fields.filter((field) => field.scope === 'ARTICLE'))

const typeLabels: Record<FieldValueKind, string> = {
  SELECT: '单选',
  TEXT: '文本',
  TEXTAREA: '长文本',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    fieldConfig.value = await getFieldConfig()
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function changeRequired(field: FieldConfigItem, event: Event) {
  if (!field.configurable) return
  const checkbox = event.target as HTMLInputElement
  const nextRequired = checkbox.checked
  savingFieldKey.value = field.fieldKey
  error.value = ''
  message.value = ''
  try {
    fieldConfig.value = await updateFieldRequired(field.fieldKey, nextRequired)
    message.value = '字段必填配置已更新，仅影响之后创建的任务。'
  } catch (caught) {
    checkbox.checked = field.required
    error.value = apiErrorMessage(caught)
  } finally {
    savingFieldKey.value = ''
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div>
        <p class="eyebrow">法律条文管理 / 字段配置</p>
        <h1>标注字段配置</h1>
        <p class="muted">字段名称、类型、范围和顺序固定，仅可配置非核心字段是否必填。</p>
      </div>
      <RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律列表</RouterLink>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="notice success">{{ message }}</p>
    <div v-if="loading" class="card empty">正在读取字段配置…</div>

    <div v-else class="card field-config-card">
      <div class="field-config-heading">
        <div>
          <h2>固定字段</h2>
          <p class="muted">核心字段始终必填，任务创建时会快照当时的必填配置。</p>
        </div>
        <span class="config-note">核心字段不可关闭</span>
      </div>

      <div class="field-config-grid">
        <section class="config-section" aria-labelledby="overall-field-heading">
          <h3 id="overall-field-heading">整体信息字段</h3>
          <table>
            <thead><tr><th>字段名称</th><th>类型</th><th>必填配置</th></tr></thead>
            <tbody>
              <tr v-for="field in overallFields" :key="field.fieldKey">
                <td>{{ field.displayName }}</td>
                <td>{{ typeLabels[field.type] }}</td>
                <td>
                  <span v-if="!field.configurable" class="core-required">必填（核心）</span>
                  <label v-else class="required-toggle">
                    <input
                      type="checkbox"
                      :checked="field.required"
                      :disabled="Boolean(savingFieldKey)"
                      :aria-label="field.displayName + '设为必填'"
                      @change="changeRequired(field, $event)"
                    />
                    {{ savingFieldKey === field.fieldKey ? '保存中…' : field.required ? '必填' : '选填' }}
                  </label>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="config-section" aria-labelledby="article-field-heading">
          <h3 id="article-field-heading">具体法条字段</h3>
          <table>
            <thead><tr><th>字段名称</th><th>类型</th><th>必填配置</th></tr></thead>
            <tbody>
              <tr v-for="field in articleFields" :key="field.fieldKey">
                <td>{{ field.displayName }}</td>
                <td>{{ typeLabels[field.type] }}</td>
                <td>
                  <span v-if="!field.configurable" class="core-required">必填（核心）</span>
                  <label v-else class="required-toggle">
                    <input
                      type="checkbox"
                      :checked="field.required"
                      :disabled="Boolean(savingFieldKey)"
                      :aria-label="field.displayName + '设为必填'"
                      @change="changeRequired(field, $event)"
                    />
                    {{ savingFieldKey === field.fieldKey ? '保存中…' : field.required ? '必填' : '选填' }}
                  </label>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
  </section>
</template>

<style src="./law.css"></style>
