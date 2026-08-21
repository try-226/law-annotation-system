<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="basic-layout">
    <header class="app-header">
      <RouterLink class="brand" to="/laws">法律条文标注系统</RouterLink>
      <nav>
        <RouterLink to="/laws">法律管理</RouterLink>
        <RouterLink to="/laws/import">导入法律</RouterLink>
      </nav>
    </header>
    <main><RouterView /></main>
  </div>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: #f4f6f9;
  color: #1f2937;
}
.app-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 5vw;
  background: #173f70;
  color: #fff;
}
.brand { font-size: 18px; font-weight: 700; }
nav { display: flex; gap: 24px; }
a { color: inherit; text-decoration: none; }
nav a.router-link-active { color: #f5d37a; }
main { min-height: calc(100vh - 64px); }
</style>
