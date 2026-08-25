<script setup lang="ts">
import AppModal from '../../components/AppModal.vue'
import type { SubmissionLocator } from '../../types/annotation'

defineProps<{ open: boolean; busy?: boolean; locators: SubmissionLocator[]; error?: string }>()
const emit = defineEmits<{ confirm: []; locate: [locator: SubmissionLocator]; close: [] }>()
</script>

<template>
  <AppModal :open="open" title="提交整部任务" :busy="busy" width="650px" @close="emit('close')">
    <div class="annotation-submit-copy">
      <p v-if="!locators.length">提交时会先保存当前修改，再由后端重新校验整体信息和全部法条。提交成功后不可撤回。</p>
      <div v-else class="annotation-submit-warning"><strong>整部校验未通过：还有 {{ locators.length }} 项未完成。</strong><span>点击错误项定位并补充保存。</span></div>
      <p v-if="error" class="field-error">{{ error }}</p>
      <div v-if="locators.length" class="annotation-locator-list">
        <button v-for="locator in locators" :key="locator.path" type="button" @click="emit('locate', locator)"><span>{{ locator.message }}</span><strong>去填写 →</strong></button>
      </div>
    </div>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">暂不提交</button>
      <button v-if="!locators.length" class="button button--primary" type="button" :disabled="busy" @click="emit('confirm')"><span v-if="busy" class="spinner" />确认提交</button>
      <button v-else class="button button--primary" type="button" :disabled="busy" @click="emit('locate', locators[0])">定位第一项</button>
    </template>
  </AppModal>
</template>
