<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { apiErrorMessage, listLaws } from '../../api/laws'
import type { PageResponse } from '../../api/types'
import type { LawDisplayStatus, LawListItem, ValidityStatus } from '../../types/law'
import { validateSearch } from '../../utils/validation'

const keyword = ref('')
const validityStatus = ref<ValidityStatus | ''>('')
const displayStatus = ref<LawDisplayStatus | ''>('')
const loading = ref(false)
const error = ref('')
const pageSize = 10
const result = ref<PageResponse<LawListItem>>(emptyPage())
let requestSequence = 0

const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}

const displayLabels: Record<LawDisplayStatus, string> = {
  UNANNOTATED: '未标注', ANNOTATING: '标注中', PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回', PENDING_REREVIEW: '待复审', PENDING_REVISION: '待修订',
  REVISING: '修订中', COMPLETED: '已完成',
}

function emptyPage(page = 0): PageResponse<LawListItem> {
  return { items: [], page, size: pageSize, totalElements: 0, totalPages: 0 }
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  })
}

async function load(page = 0) {
  const currentRequest = ++requestSequence
  const searchError = validateSearch(keyword.value)
  if (searchError) {
    loading.value = false
    error.value = searchError
    result.value = emptyPage(page)
    return
  }
  loading.value = true
  error.value = ''
  result.value = emptyPage(page)
  try {
    const response = await listLaws({
      name: keyword.value,
      validityStatus: validityStatus.value || undefined,
      displayStatus: displayStatus.value || undefined,
      page,
      size: pageSize,
    })
    if (currentRequest === requestSequence) result.value = response
  } catch (caught) {
    if (currentRequest === requestSequence) error.value = apiErrorMessage(caught)
  } finally {
    if (currentRequest === requestSequence) loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  validityStatus.value = ''
  displayStatus.value = ''
  void load(0)
}

onMounted(() => void load())
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>法律管理</h1><p class="muted">查看、筛选、导入和维护法律基础数据</p></div>
      <div class="actions">
        <RouterLink class="button secondary" :to="{ name: 'admin-search' }">全库搜索</RouterLink>
        <RouterLink class="button secondary" :to="{ name: 'field-config' }">字段配置</RouterLink>
        <RouterLink class="button secondary" :to="{ name: 'law-recycle' }">回收站</RouterLink>
        <RouterLink class="button" :to="{ name: 'law-import' }">导入法律</RouterLink>
      </div>
    </div>

    <div class="card">
      <form class="filter-grid" @submit.prevent="load(0)">
        <label class="field"><span>法律名称</span><input v-model="keyword" maxlength="100" :disabled="loading" placeholder="按法律名称搜索" /></label>
        <label class="field"><span>效力状态</span><select v-model="validityStatus" :disabled="loading"><option value="">全部</option><option v-for="(label, value) in validityLabels" :key="value" :value="value">{{ label }}</option></select></label>
        <label class="field"><span>业务状态</span><select v-model="displayStatus" :disabled="loading"><option value="">全部</option><option v-for="(label, value) in displayLabels" :key="value" :value="value">{{ label }}</option></select></label>
        <div class="filter-actions"><button :disabled="loading" type="submit">{{ loading ? '查询中…' : '查询' }}</button><button class="secondary" :disabled="loading" type="button" @click="resetFilters">重置</button></div>
      </form>

      <p v-if="error" class="error">{{ error }}</p>
      <div v-if="loading" class="empty">正在加载法律数据…</div>
      <div v-else-if="!loading && result.items.length === 0" class="empty">没有符合条件的法律</div>
      <div v-else class="table-scroll">
        <table>
          <thead><tr><th>法律名称</th><th>发布机关</th><th>发布日期</th><th>效力状态</th><th>业务状态</th><th>法条数</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="law in result.items" :key="law.id">
              <td><RouterLink class="table-link" :to="`/laws/${law.id}`">{{ law.name }}</RouterLink></td>
              <td>{{ law.issuingAuthority }}</td><td>{{ law.publicationDate }}</td>
              <td><span class="badge">{{ validityLabels[law.validityStatus] }}</span></td>
              <td><span class="badge business-status">{{ displayLabels[law.displayStatus] }}</span></td>
              <td>{{ law.articleCount }}</td><td>{{ formatDateTime(law.updatedAt) }}</td>
              <td><RouterLink class="button secondary small" :to="`/laws/${law.id}`">查看</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="result.totalPages > 1" class="pagination">
        <button class="secondary small" :disabled="result.page === 0 || loading" @click="load(result.page - 1)">上一页</button>
        <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 条</span>
        <button class="secondary small" :disabled="result.page + 1 >= result.totalPages || loading" @click="load(result.page + 1)">下一页</button>
      </div>
    </div>
  </section>
</template>

<style src="./law.css"></style>
