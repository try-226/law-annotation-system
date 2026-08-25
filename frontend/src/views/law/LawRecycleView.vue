<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { listRecycleLaws, restoreLaw } from '../../api/laws'
import type { PageResponse } from '../../api/types'
import type {
  RecycleLawListItem,
  ValidityStatus,
} from '../../types/law'
import { formatDateTimeToMinute } from '../../utils/dateTime'
import { locatorValidationMessage } from '../../utils/errors'

const keyword = ref('')
const loading = ref(true)
const loaded = ref(false)
const restoringId = ref('')
const error = ref('')
const loadError = ref('')
const message = ref('')
const requestedPage = ref(0)
const selectedLaw = ref<RecycleLawListItem | null>(null)
const result = ref<PageResponse<RecycleLawListItem>>({
  items: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
})

const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效',
  NOT_EFFECTIVE: '尚未生效',
  INVALID: '失效',
  REPEALED: '已废止',
}

async function load(page = 0) {
  requestedPage.value = page
  loading.value = true
  loaded.value = false
  loadError.value = ''
  error.value = ''
  selectedLaw.value = null
  try {
    result.value = await listRecycleLaws(keyword.value.trim(), page)
    loaded.value = true
  } catch (caught) {
    loadError.value = locatorValidationMessage(caught, '回收站加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function restore(item: RecycleLawListItem) {
  if (!window.confirm('确认恢复《' + item.name + '》？恢复前系统会检查名称唯一性和业务状态。')) return
  restoringId.value = item.id
  error.value = ''
  message.value = ''
  try {
    await restoreLaw(item.id)
    message.value = '法律已恢复，可在普通法律列表中查看。'
    const nextPage = result.value.items.length === 1 && result.value.page > 0
      ? result.value.page - 1
      : result.value.page
    await load(nextPage)
  } catch (caught) {
    error.value = locatorValidationMessage(caught, '恢复失败，请稍后重试')
  } finally {
    restoringId.value = ''
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div>
        <p class="eyebrow">法律条文管理 / 回收站</p>
        <h1>法律回收站</h1>
        <p class="muted">仅展示已软删除法律，可查看保留信息或恢复，不提供永久删除。</p>
      </div>
      <RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律列表</RouterLink>
    </div>

    <p v-if="message" class="notice success">{{ message }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="card">
      <form class="toolbar" @submit.prevent="load(0)">
        <input v-model="keyword" aria-label="法律名称" placeholder="按法律名称搜索回收站" />
        <button :disabled="loading" type="submit">{{ loading ? '查询中…' : '查询' }}</button>
      </form>

      <div v-if="loading" class="empty">正在读取回收站…</div>
      <div v-else-if="loadError" class="empty error-state">
        <p class="error">{{ loadError }}</p>
        <button class="secondary small" type="button" @click="load(requestedPage)">重试</button>
      </div>
      <div v-else-if="loaded && result.items.length === 0" class="empty">回收站暂无法律</div>
      <table v-else-if="loaded">
        <thead>
          <tr>
            <th>法律名称</th>
            <th>发布机关</th>
            <th>效力状态</th>
            <th>法条数</th>
            <th>删除时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="law in result.items" :key="law.id">
            <td>{{ law.name }}</td>
            <td>{{ law.issuingAuthority }}</td>
            <td><span class="badge">{{ validityLabels[law.validityStatus] }}</span></td>
            <td>{{ law.articleCount }}</td>
            <td>{{ formatDateTimeToMinute(law.deletedAt) }}</td>
            <td>
              <div class="actions">
                <button class="secondary small" type="button" @click="selectedLaw = law">查看</button>
                <button
                  class="small"
                  type="button"
                  :disabled="Boolean(restoringId)"
                  @click="restore(law)"
                >
                  {{ restoringId === law.id ? '恢复中…' : '恢复' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="loaded && result.totalPages > 1" class="pagination">
        <button
          class="secondary small"
          :disabled="result.page === 0 || loading"
          @click="load(result.page - 1)"
        >
          上一页
        </button>
        <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页，共 {{ result.totalElements }} 条</span>
        <button
          class="secondary small"
          :disabled="result.page + 1 >= result.totalPages || loading"
          @click="load(result.page + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <section v-if="selectedLaw" class="card recycle-detail" aria-labelledby="recycle-detail-heading">
      <div class="row-head">
        <h2 id="recycle-detail-heading">回收站记录</h2>
        <button class="secondary small" type="button" @click="selectedLaw = null">关闭</button>
      </div>
      <dl class="definition-grid">
        <div><dt>法律名称</dt><dd>{{ selectedLaw.name }}</dd></div>
        <div><dt>发布机关</dt><dd>{{ selectedLaw.issuingAuthority }}</dd></div>
        <div><dt>发布日期</dt><dd>{{ selectedLaw.publicationDate }}</dd></div>
        <div><dt>效力状态</dt><dd>{{ validityLabels[selectedLaw.validityStatus] }}</dd></div>
        <div><dt>法条数量</dt><dd>{{ selectedLaw.articleCount }} 条</dd></div>
        <div><dt>修订状态</dt><dd>{{ selectedLaw.pendingRevision ? '待修订' : '无需修订' }}</dd></div>
        <div><dt>删除时间</dt><dd>{{ formatDateTimeToMinute(selectedLaw.deletedAt) }}</dd></div>
        <div><dt>最后更新</dt><dd>{{ formatDateTimeToMinute(selectedLaw.updatedAt) }}</dd></div>
      </dl>
    </section>
  </section>
</template>

<style src="./law.css"></style>
