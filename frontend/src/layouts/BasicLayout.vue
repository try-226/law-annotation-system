<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <main class="basic-layout">
    <RouterView />
  </main>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
}
</style>
