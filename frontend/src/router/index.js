import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    { path: '/projects', component: () => import('../views/ProjectsView.vue') },
    { path: '/projects/:id', component: () => import('../views/ProjectDetailView.vue') },
    { path: '/ai', name: 'AI 助手', component: () => import('../views/AiAssistantView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
