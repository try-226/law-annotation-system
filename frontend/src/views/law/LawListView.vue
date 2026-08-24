<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { apiErrorMessage, listLaws } from '../../api/laws'
import type { LawListItem, PageResponse, ValidityStatus } from '../../types/law'

const keyword = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<PageResponse<LawListItem>>({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })

const statusLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
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

onMounted(() => { void load() })
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>法律管理</h1><p class="muted">查看、导入和维护法律基础数据</p></div>
      <div class="actions">
        <RouterLink class="button secondary" :to="{ name: 'field-config' }">字段配置</RouterLink>
        <RouterLink class="button secondary" :to="{ name: 'law-recycle' }">回收站</RouterLink>
        <RouterLink class="button" :to="{ name: 'law-import' }">导入法律</RouterLink>
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
