<script setup lang="ts">
import type { LawAuditHistory } from '../../types/history'
import { formatTaskDateTime } from '../../types/task'
import { formatAuditValue } from './historyPresentation'

defineProps<{ detail: LawAuditHistory }>()
</script>

<template>
  <div class="history-detail-stack">
    <section class="panel history-detail-card">
      <div class="history-detail-heading"><div><span class="history-kicker">法律独立审计</span><h1>{{ detail.auditType === 'STRUCTURE' ? '法律结构变更' : '法律基础信息变更' }}</h1></div><span class="history-readonly-badge">只读</span></div>
      <dl class="history-definition-grid"><div><dt>审计 ID</dt><dd>{{ detail.auditId }}</dd></div><div><dt>操作者 ID</dt><dd>{{ detail.operatorId }}</dd></div><div><dt>操作时间</dt><dd>{{ formatTaskDateTime(detail.operatedAt) }}</dd></div><div><dt>审计类型</dt><dd>{{ detail.auditType }}</dd></div></dl>
    </section>
    <section class="history-audit-grid"><div class="panel history-detail-card"><h2>变更前</h2><pre>{{ formatAuditValue(detail.before) }}</pre></div><div class="panel history-detail-card"><h2>变更后</h2><pre>{{ formatAuditValue(detail.after) }}</pre></div></section>
  </div>
</template>
