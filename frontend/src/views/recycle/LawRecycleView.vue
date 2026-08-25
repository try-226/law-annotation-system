<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { apiErrorMessage, listRecycleLaws, restoreLaw } from '../../api/laws'
import type { PageResponse } from '../../api/types'
import type { RecycleLawListItem, ValidityStatus } from '../../types/law'
import { validateSearch } from '../../utils/validation'

const keyword = ref('')
const loading = ref(false)
const restoringId = ref('')
const error = ref('')
const message = ref('')
const pageSize = 10
const result = ref<PageResponse<RecycleLawListItem>>(emptyPage())
const validityLabels: Record<ValidityStatus, string> = { ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止' }
const busy = computed(() => loading.value || Boolean(restoringId.value))
let requestSequence = 0

function emptyPage(page = 0): PageResponse<RecycleLawListItem> {
  return { items: [], page, size: pageSize, totalElements: 0, totalPages: 0 }
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  })
}

async function load(page = 0, afterRestore = false) {
  if (restoringId.value && !afterRestore) return
  const currentRequest = ++requestSequence
  const searchError = validateSearch(keyword.value)
  if (searchError) {
    loading.value = false
    error.value = searchError
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await listRecycleLaws(keyword.value, page, pageSize)
    if (currentRequest === requestSequence) result.value = response
  } catch (caught) {
    if (currentRequest === requestSequence) error.value = apiErrorMessage(caught)
  } finally {
    if (currentRequest === requestSequence) loading.value = false
  }
}

async function restore(item: RecycleLawListItem) {
  if (loading.value || restoringId.value || !window.confirm(`确认恢复“${item.name}”？如存在同名法律，后端将拒绝恢复。`)) return
  restoringId.value = item.id
  error.value = ''
  message.value = ''
  try {
    await restoreLaw(item.id)
    message.value = `已恢复“${item.name}”`
    const remainingItems = result.value.items.filter((law) => law.id !== item.id)
    const totalElements = Math.max(0, result.value.totalElements - 1)
    const totalPages = Math.ceil(totalElements / pageSize)
    const nextPage = remainingItems.length === 0 && result.value.page > 0
      ? result.value.page - 1
      : result.value.page
    result.value = {
      items: remainingItems,
      page: nextPage,
      size: pageSize,
      totalElements,
      totalPages,
    }
    await load(nextPage, true)
  } catch (caught) { error.value = apiErrorMessage(caught) }
  finally { restoringId.value = '' }
}

onMounted(() => void load())
</script>

<template>
  <section class="law-page">
    <div class="page-title"><div><h1>法律回收站</h1><p class="muted">仅查看和恢复已删除法律，不提供编辑或永久删除</p></div><RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律管理</RouterLink></div>
    <div class="card">
      <form class="toolbar" @submit.prevent="load(0)"><input v-model="keyword" maxlength="100" :disabled="busy" placeholder="按法律名称搜索" /><button :disabled="busy" type="submit">{{ loading ? '查询中…' : '查询' }}</button></form>
      <p v-if="error" class="error">{{ error }}</p><p v-if="message" class="notice success">{{ message }}</p>
      <div v-if="loading" class="empty">正在加载回收站…</div>
      <div v-else-if="!loading && result.items.length === 0" class="empty">回收站中没有符合条件的法律</div>
      <div v-else class="table-scroll"><table><thead><tr><th>法律名称</th><th>发布机关</th><th>发布日期</th><th>效力状态</th><th>法条数</th><th>删除时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="law in result.items" :key="law.id"><td>{{ law.name }}</td><td>{{ law.issuingAuthority }}</td><td>{{ law.publicationDate }}</td><td><span class="badge">{{ validityLabels[law.validityStatus] }}</span></td><td>{{ law.articleCount }}</td><td>{{ formatDateTime(law.deletedAt) }}</td><td><button class="small" :disabled="busy" @click="restore(law)">{{ restoringId === law.id ? '恢复中…' : '恢复' }}</button></td></tr>
      </tbody></table></div>
      <div v-if="result.totalPages > 1" class="pagination"><button class="secondary small" :disabled="result.page === 0 || busy" @click="load(result.page - 1)">上一页</button><span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 条</span><button class="secondary small" :disabled="result.page + 1 >= result.totalPages || busy" @click="load(result.page + 1)">下一页</button></div>
    </div>
  </section>
</template>

<style src="../law/law.css"></style>
