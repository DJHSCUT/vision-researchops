import { createApp } from 'vue'
import { provideGlobalConfig } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElSkeleton } from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
provideGlobalConfig({ locale: zhCn }, app, true)
// Register only the components used in this first version.
for (const component of [ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElSkeleton]) {
  app.component(component.name, component)
}
app.use(router).mount('#app')
