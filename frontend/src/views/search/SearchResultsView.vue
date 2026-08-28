<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

import { searchLaws } from '../../api/search'
import type { PageResponse } from '../../api/types'
import type { SearchHit, SearchScope } from '../../types/search'
import { safeErrorMessage } from '../../utils/errors'
import { normalizeSearchQuery, validateRequiredSearch } from '../../utils/validation'
import {
  isAnnotationHit,
  SEARCH_SCOPE_OPTIONS,
  searchFieldLabel,
  searchHitKey,
  searchResultRoute,
  splitSearchSnippet,
} from './searchPresentation'

const keyword = ref('')
const appliedKeyword = ref('')
const scope = ref<SearchScope>('ALL')
const loading = ref(false)
const error = ref('')
const hasSearched = ref(false)
const pageSize = 10
const result = ref<PageResponse<SearchHit>>(emptyPage())
let requestSequence = 0

function emptyPage(page = 0): PageResponse<SearchHit> {
  return { items: [], page, size: pageSize, totalElements: 0, totalPages: 0 }
}

async function load(page: number): Promise<void> {
  if (!appliedKeyword.value) return
  const currentRequest = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const response = await searchLaws({
      q: appliedKeyword.value,
      scope: scope.value,
      page,
      size: pageSize,
    })
    if (currentRequest === requestSequence) result.value = response
  } catch (caught: unknown) {
    if (currentRequest === requestSequence) {
      error.value = safeErrorMessage(caught, '搜索失败，请稍后重试')
    }
  } finally {
    if (currentRequest === requestSequence) loading.value = false
  }
}

function submitSearch(): void {
  const validation = validateRequiredSearch(keyword.value)
  if (validation) {
    ++requestSequence
    loading.value = false
    error.value = validation
    hasSearched.value = false
    result.value = emptyPage()
    return
  }
  appliedKeyword.value = normalizeSearchQuery(keyword.value)
  keyword.value = appliedKeyword.value
  hasSearched.value = true
  result.value = emptyPage()
  void load(0)
}

function changeScope(): void {
  submitSearch()
}

function changePage(page: number): void {
  if (loading.value || page < 0 || page >= result.value.totalPages) return
  void load(page)
}

onBeforeUnmount(() => { ++requestSequence })
</script>

<template>
  <section class="search-page">
    <header class="search-heading">
      <div><h1>全库搜索</h1><p>搜索未删除法律的当前正文与当前正式标注结果</p></div>
      <RouterLink class="button" :to="{ name: 'law-list' }">返回法律管理</RouterLink>
    </header>

    <section class="panel search-controls">
      <form class="search-form" @submit.prevent="submitSearch">
        <label class="search-query-field">
          <span>搜索关键词</span>
          <input v-model="keyword" class="input" :disabled="loading" placeholder="输入任意连续文字片段" autocomplete="off" />
        </label>
        <label>
          <span>搜索范围</span>
          <select v-model="scope" class="select" :disabled="loading" @change="changeScope">
            <option v-for="option in SEARCH_SCOPE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <button class="button button--primary" type="submit" :disabled="loading">
          <span v-if="loading" class="spinner" aria-hidden="true"></span>{{ loading ? '搜索中…' : '搜索' }}
        </button>
      </form>
      <p class="search-hint">关键词会去除首尾空白，最多 100 个字符；符号按普通文字搜索。</p>
      <p v-if="error" class="search-error" role="alert">{{ error }}</p>
    </section>

    <section class="panel search-results" aria-live="polite">
      <div class="search-results-heading">
        <div><h2>搜索结果</h2><p v-if="hasSearched">“{{ appliedKeyword }}”共命中 {{ result.totalElements }} 项</p><p v-else>输入关键词开始搜索</p></div>
        <span v-if="hasSearched" class="search-scope-badge">{{ SEARCH_SCOPE_OPTIONS.find((item) => item.value === scope)?.label }}</span>
      </div>

      <div v-if="loading" class="search-empty">正在查询当前有效数据…</div>
      <div v-else-if="hasSearched && result.items.length === 0" class="search-empty">暂无匹配结果</div>
      <div v-else-if="!hasSearched" class="search-empty">可搜索法律名称、发布机关、章节、条号、正文及正式标注字段</div>
      <ol v-else class="search-result-list">
        <li v-for="(hit, index) in result.items" :key="searchHitKey(hit, index)" class="search-result-card">
          <div class="search-result-title">
            <div><h3>{{ hit.lawName }}</h3><p>{{ [...hit.structurePath, hit.articleNumber].filter(Boolean).join(' / ') || '整部法律' }}</p></div>
            <span>{{ searchFieldLabel(hit.hitField) }}</span>
          </div>
          <p class="search-snippet">
            <template v-for="(segment, segmentIndex) in splitSearchSnippet(hit)" :key="segmentIndex"><mark v-if="segment.highlighted">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template>
          </p>
          <div class="search-result-actions">
            <RouterLink class="button button--text" :to="searchResultRoute(hit)">查看法律</RouterLink>
            <RouterLink v-if="isAnnotationHit(hit)" class="button button--text" :to="searchResultRoute(hit, true)">查看正式结果</RouterLink>
          </div>
        </li>
      </ol>

      <footer v-if="!loading && result.totalPages > 1" class="search-pagination">
        <button class="button" type="button" :disabled="result.page === 0" @click="changePage(result.page - 1)">上一页</button>
        <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 项</span>
        <button class="button" type="button" :disabled="result.page + 1 >= result.totalPages" @click="changePage(result.page + 1)">下一页</button>
      </footer>
    </section>
  </section>
</template>

<style src="./search.css"></style>
