<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { listLaws } from '../../api/laws'
import type { PageResponse } from '../../api/types'
import type { LawDisplayStatus, LawListItem, ValidityStatus } from '../../types/law'
import { formatDateTimeToMinute } from '../../utils/dateTime'
import { locatorValidationMessage } from '../../utils/errors'

const keyword = ref('')
const loading = ref(true)
const loaded = ref(false)
const error = ref('')
const requestedPage = ref(0)
const result = ref<PageResponse<LawListItem>>({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })

const statusLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}
const displayStatusLabels: Record<LawDisplayStatus, string> = {
  UNANNOTATED: '未标注',
  PENDING_ANNOTATION: '待标注',
  ANNOTATING: '标注中',
  PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  PENDING_REREVIEW: '待复审',
  COMPLETED: '已完成',
  PENDING_REVISION: '待修订',
  REVISING: '修订中',
}

async function load(page = 0) {
  requestedPage.value = page
  loading.value = true
  loaded.value = false
  error.value = ''
  try {
    result.value = await listLaws(keyword.value.trim(), page)
    loaded.value = true
  } catch (caught) {
    error.value = locatorValidationMessage(caught, '法律列表加载失败，请稍后重试')
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
      <div v-if="loading" class="empty">正在加载法律列表…</div>
      <div v-else-if="error" class="empty error-state">
        <p class="error">{{ error }}</p>
        <button class="secondary small" type="button" @click="load(requestedPage)">重试</button>
      </div>
      <div v-else-if="loaded && result.items.length === 0" class="empty">暂无法律数据</div>
      <table v-else-if="loaded">
        <thead><tr><th>法律名称</th><th>发布机关</th><th>发布日期</th><th>效力状态</th><th>标注状态</th><th>法条数</th><th>更新时间</th></tr></thead>
        <tbody>
          <tr v-for="law in result.items" :key="law.id">
            <td><RouterLink :to="`/laws/${law.id}`">{{ law.name }}</RouterLink></td>
            <td>{{ law.issuingAuthority }}</td><td>{{ law.publicationDate }}</td>
            <td><span class="badge">{{ statusLabels[law.validityStatus] }}</span></td>
            <td><span class="status-pill primary">{{ displayStatusLabels[law.displayStatus] }}</span></td>
            <td>{{ law.articleCount }}</td><td>{{ formatDateTimeToMinute(law.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="loaded && result.totalPages > 1" class="pagination">
        <button class="secondary small" :disabled="result.page === 0 || loading" @click="load(result.page - 1)">上一页</button>
        <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 条</span>
        <button class="secondary small" :disabled="result.page + 1 >= result.totalPages || loading" @click="load(result.page + 1)">下一页</button>
      </div>
    </div>
  </section>
</template>

<style src="./law.css"></style>
