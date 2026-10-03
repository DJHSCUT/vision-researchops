<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink to="/projects" class="brand" aria-label="Vision ResearchOps 首页">
        <span class="brand-mark" aria-hidden="true">v<span>·</span></span>
        <span><strong>Vision ResearchOps</strong><small>研究工作管理</small></span>
      </RouterLink>
      <div class="user-placeholder"><span class="workspace-label">研究工作空间</span><span class="avatar" aria-label="用户头像">研</span></div>
    </header>
    <nav class="mobile-navigation" aria-label="窄屏主导航">
      <RouterLink to="/projects" active-class="is-active">研究项目</RouterLink>
      <RouterLink to="/ai" active-class="is-active">AI 助手</RouterLink>
    </nav>
    <div class="workspace">
      <aside class="sidebar">
        <p class="nav-label">工作空间</p>
        <nav aria-label="主导航">
          <RouterLink to="/projects" class="nav-item" active-class="is-active">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg>
            研究项目
          </RouterLink>
          <RouterLink to="/ai" class="nav-item" active-class="is-active">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-5 3v-3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z"/><path d="m12 7 1.2 2.8L16 11l-2.8 1.2L12 15l-1.2-2.8L8 11l2.8-1.2Z"/></svg>
            AI 助手
          </RouterLink>
        </nav>
        <div class="sidebar-note"><span class="tiny-dot"></span> 专注研究，有序推进。</div>
      </aside>
      <main class="main-content"><div class="content-container"><slot /></div></main>
    </div>
  </div>
</template>

<style scoped>
.mobile-navigation { display: none; }
@media (max-width: 700px) {
  .mobile-navigation { display: flex; gap: 8px; padding: 10px 20px; background: #fff; border-bottom: 1px solid #eceef1; }
  .mobile-navigation a { padding: 8px 12px; border-radius: 8px; color: #858b95; font-size: 12px; }
  .mobile-navigation a.is-active { background: #f0f4fa; color: #2861af; }
}
</style>
