<script setup lang="ts">
import { RouterView } from 'vue-router'
import FeedbackToast from './components/FeedbackToast.vue'
</script>

<template>
  <RouterView />
  <FeedbackToast />
</template>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: Inter, "Microsoft YaHei", system-ui, sans-serif; }
button, input, textarea, select { font: inherit; }
</style>
