import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ArcoVue from '@arco-design/web-vue'
import * as ArcoVueIcon from '@arco-design/web-vue/es/icon'
import '@arco-design/web-vue/dist/arco.css'
import './plugins/echarts'

import App from './App.vue'
import router from './router'

import './assets/styles/variables.css'
import './assets/styles/global.css'
import './assets/styles/transitions.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ArcoVue)

for (const [key, component] of Object.entries(ArcoVueIcon)) {
  app.component(key, component)
}

app.mount('#app')
