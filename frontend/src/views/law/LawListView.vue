<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getFieldConfig,
  updateFieldRequired,
  type FieldConfig,
  type FieldConfigItem,
  type FieldValueKind,
} from '../../api/fieldConfig'
import { apiErrorMessage, listLaws } from '../../api/laws'
import type { LawListItem, PageResponse, ValidityStatus } from '../../types/law'

const keyword = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<PageResponse<LawListItem>>({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
const fieldConfig = ref<FieldConfig>({ fields: [] })
const fieldConfigLoading = ref(false)
const fieldConfigError = ref('')
const savingFieldKey = ref('')

const overallFields = computed(() => fieldConfig.value.fields.filter((field) => field.scope === 'OVERALL'))
const articleFields = computed(() => fieldConfig.value.fields.filter((field) => field.scope === 'ARTICLE'))

const statusLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}

const typeLabels: Record<FieldValueKind, string> = {
  SELECT: '单选', TEXT: '文本', TEXTAREA: '长文本',
}

async function load(page = 0) {
  loading.value = true
  error.value = ''
  try {
    result.value = await listLaws(keyword.value.trim(), page)
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadFieldConfig() {
  fieldConfigLoading.value = true
  fieldConfigError.value = ''
  try {
    fieldConfig.value = await getFieldConfig()
  } catch (caught) {
    fieldConfigError.value = apiErrorMessage(caught)
  } finally {
    fieldConfigLoading.value = false
  }
}

async function changeRequired(field: FieldConfigItem, event: Event) {
  const checkbox = event.target as HTMLInputElement
  const nextRequired = checkbox.checked
  savingFieldKey.value = field.fieldKey
  fieldConfigError.value = ''
  try {
    fieldConfig.value = await updateFieldRequired(field.fieldKey, nextRequired)
  } catch (caught) {
    checkbox.checked = field.required
    fieldConfigError.value = apiErrorMessage(caught)
  } finally {
    savingFieldKey.value = ''
  }
}

onMounted(() => {
  void load()
  void loadFieldConfig()
})
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>法律管理</h1><p class="muted">查看、导入和维护法律基础数据</p></div>
      <RouterLink class="button" to="/laws/import">导入法律</RouterLink>
    </div>
    <div class="card field-config-card">
      <div class="field-config-heading">
        <div>
          <h2>标注字段配置</h2>
          <p class="muted">字段名称、类型和顺序固定；配置仅影响之后创建的任务。</p>
        </div>
        <span class="config-note">4 个核心字段始终必填</span>
      </div>
      <p v-if="fieldConfigError" class="error">{{ fieldConfigError }}</p>
      <div v-if="fieldConfigLoading" class="empty">正在读取字段配置…</div>
      <div v-else class="field-config-grid">
        <section class="config-section" aria-labelledby="overall-field-heading">
          <h3 id="overall-field-heading">整体信息字段</h3>
          <table>
            <thead><tr><th>字段名称</th><th>类型</th><th>是否必填</th></tr></thead>
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
                      :aria-label="`${field.displayName}设为必填`"
                      @change="changeRequired(field, $event)"
                    />
                    {{ field.required ? '必填' : '选填' }}
                  </label>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
        <section class="config-section" aria-labelledby="article-field-heading">
          <h3 id="article-field-heading">具体法条字段</h3>
          <table>
            <thead><tr><th>字段名称</th><th>类型</th><th>是否必填</th></tr></thead>
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
                      :aria-label="`${field.displayName}设为必填`"
                      @change="changeRequired(field, $event)"
                    />
                    {{ field.required ? '必填' : '选填' }}
                  </label>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
    <div class="card">
      <form class="toolbar" @submit.prevent="load(0)">
        <input v-model="keyword" aria-label="法律名称" placeholder="按法律名称搜索" />
        <button :disabled="loading" type="submit">{{ loading ? '查询中…' : '查询' }}</button>
      </form>
      <p v-if="error" class="error">{{ error }}</p>
      <div v-if="!loading && result.items.length === 0" class="empty">暂无法律数据</div>
      <table v-else>
        <thead><tr><th>法律名称</th><th>发布机关</th><th>发布日期</th><th>效力状态</th><th>法条数</th><th>更新时间</th></tr></thead>
        <tbody>
          <tr v-for="law in result.items" :key="law.id">
            <td><RouterLink :to="`/laws/${law.id}`">{{ law.name }}</RouterLink></td>
            <td>{{ law.issuingAuthority }}</td><td>{{ law.publicationDate }}</td>
            <td><span class="badge">{{ statusLabels[law.validityStatus] }}</span></td>
            <td>{{ law.articleCount }}</td><td>{{ new Date(law.updatedAt).toLocaleString() }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="result.totalPages > 1" class="pagination">
        <button class="secondary small" :disabled="result.page === 0 || loading" @click="load(result.page - 1)">上一页</button>
        <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 条</span>
        <button class="secondary small" :disabled="result.page + 1 >= result.totalPages || loading" @click="load(result.page + 1)">下一页</button>
      </div>
    </div>
  </section>
</template>

<style src="./law.css"></style>
