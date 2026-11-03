import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import hasPerm, { roleDirective } from './directives/permission'
import './assets/main.css'

const app = createApp(App)

// 全局注册 Element Plus 所有图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册自定义权限指令：v-has-perm="'ticket:add'"
app.directive('has-perm', hasPerm)
app.directive('has-role', roleDirective)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')