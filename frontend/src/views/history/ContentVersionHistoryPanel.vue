<script setup lang="ts">
import type { ContentVersionHistory } from '../../types/history'
import { formatTaskDateTime } from '../../types/task'

defineProps<{ detail: ContentVersionHistory }>()
</script>

<template>
  <div class="history-detail-stack">
    <section class="panel history-detail-card">
      <div class="history-detail-heading"><div><span class="history-kicker">语义内容快照</span><h1>内容版本 C{{ detail.seq }}</h1></div><span class="history-readonly-badge">只读</span></div>
      <dl class="history-definition-grid"><div><dt>内容版本 ID</dt><dd>{{ detail.contentVersionId }}</dd></div><div><dt>操作者 ID</dt><dd>{{ detail.createdBy }}</dd></div><div><dt>形成时间</dt><dd>{{ formatTaskDateTime(detail.createdAt) }}</dd></div><div><dt>历史法条数</dt><dd>{{ detail.semanticArticlesSnapshot.length }} 条</dd></div></dl>
    </section>
    <section class="panel history-detail-card"><h2>历史正文快照</h2><p v-if="detail.semanticArticlesSnapshot.length === 0" class="history-empty-inline">该内容版本没有法条快照</p><div v-else class="history-article-list"><details v-for="article in detail.semanticArticlesSnapshot" :key="article.articleId"><summary><strong>{{ article.number }}</strong><span>顺序 {{ article.order }}</span></summary><p>{{ article.body }}</p><small>法条 ID：{{ article.articleId }}</small></details></div></section>
  </div>
</template>
