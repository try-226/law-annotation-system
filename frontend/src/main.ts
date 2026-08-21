import { createApp } from 'vue'

import App from './App.vue'
import { setUnauthorizedHandler } from './api/request'
import router from './router'
import { clearAuthentication } from './state/auth'
import './assets/main.css'

setUnauthorizedHandler(() => {
  clearAuthentication()
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login' })
  }
})

createApp(App).use(router).mount('#app')
