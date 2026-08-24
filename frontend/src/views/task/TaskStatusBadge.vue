<script setup lang="ts">
import { computed } from 'vue'

import type { TaskState } from '../../types/task'
import { TASK_STATE_LABELS } from '../../types/task'

const props = defineProps<{ state: TaskState }>()

const stateClass = computed(() => `task-status--${props.state.toLowerCase().replaceAll('_', '-')}`)
</script>

<template>
  <span class="task-status" :class="stateClass">{{ TASK_STATE_LABELS[state] }}</span>
</template>

<style scoped>
.task-status { display: inline-flex; border-radius: 12px; padding: 3px 9px; font-size: 12px; white-space: nowrap; }
.task-status--pending-annotation { background: #edf3fc; color: #2868c7; }
.task-status--annotating { background: #e8f6f3; color: #137c67; }
.task-status--pending-review, .task-status--pending-rereview { background: #fff3df; color: #b26713; }
.task-status--partially-rejected { background: #fff0f0; color: #bd3d3d; }
.task-status--approved { background: #e9f7ef; color: #18794e; }
.task-status--canceled { background: #f1f2f4; color: #687386; }
</style>
