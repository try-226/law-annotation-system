<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { apiErrorMessage, listRecycleLaws, restoreLaw } from '../../api/laws'
import type { PageResponse, RecycleLawListItem, ValidityStatus } from '../../types/law'

const keyword = ref('')
const loading = ref(false)
const restoringId = ref('')
const error = ref('')
const message = ref('')
const result = ref<PageResponse<RecycleLawListItem>>({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
const validityLabels: Record<ValidityStatus, string> = { ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止' }

async function load(page = 0) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try { result.value = await listRecycleLaws(keyword.value, page, result.value.size) }
  catch (caught) { error.value = apiErrorMessage(caught) }
  finally { loading.value = false }
}

async function restore(item: RecycleLawListItem) {
  if (restoringId.value || !window.confirm(`确认恢复“${item.name}”？如存在同名法律，后端将拒绝恢复。`)) return
  restoringId.value = item.id
  error.value = ''
  message.value = ''
  try {
    await restoreLaw(item.id)
    message.value = `已恢复“${item.name}”`
    const nextPage = result.value.items.length === 1 && result.value.page > 0 ? result.value.page - 1 : result.value.page
    await load(nextPage)
  } catch (caught) { error.value = apiErrorMessage(caught) }
  finally { restoringId.value = '' }
}

onMounted(() => void load())
</script>

<template>
  <section class="law-page">
    <div class="page-title"><div><h1>法律回收站</h1><p class="muted">仅查看和恢复已删除法律，不提供编辑或永久删除</p></div><RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律管理</RouterLink></div>
    <div class="card">
      <form class="toolbar" @submit.prevent="load(0)"><input v-model="keyword" placeholder="按法律名称搜索" /><button :disabled="loading" type="submit">{{ loading ? '查询中…' : '查询' }}</button></form>
      <p v-if="error" class="error">{{ error }}</p><p v-if="message" class="notice success">{{ message }}</p>
      <div v-if="loading && result.items.length === 0" class="empty">正在加载回收站…</div>
      <div v-else-if="!loading && result.items.length === 0" class="empty">回收站中没有符合条件的法律</div>
      <div v-else class="table-scroll"><table><thead><tr><th>法律名称</th><th>发布机关</th><th>发布日期</th><th>效力状态</th><th>法条数</th><th>删除时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="law in result.items" :key="law.id"><td>{{ law.name }}</td><td>{{ law.issuingAuthority }}</td><td>{{ law.publicationDate }}</td><td><span class="badge">{{ validityLabels[law.validityStatus] }}</span></td><td>{{ law.articleCount }}</td><td>{{ new Date(law.deletedAt).toLocaleString() }}</td><td><button class="small" :disabled="Boolean(restoringId)" @click="restore(law)">{{ restoringId === law.id ? '恢复中…' : '恢复' }}</button></td></tr>
      </tbody></table></div>
      <div v-if="result.totalPages > 1" class="pagination"><button class="secondary small" :disabled="result.page === 0 || loading" @click="load(result.page - 1)">上一页</button><span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 条</span><button class="secondary small" :disabled="result.page + 1 >= result.totalPages || loading" @click="load(result.page + 1)">下一页</button></div>
    </div>
  </section>
</template>

<style src="../law/law.css"></style>
