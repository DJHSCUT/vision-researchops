<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink to="/projects" class="brand" aria-label="Vision ResearchOps 首页">
        <span class="brand-mark" aria-hidden="true">v<span>·</span></span>
        <span><strong>Vision ResearchOps</strong><small>研究工作管理</small></span>
      </RouterLink>
      <div class="user-placeholder"><span class="workspace-label">研究工作空间</span><span class="avatar" aria-label="用户头像">研</span></div>
    </header>
    <div class="workspace">
      <aside class="sidebar">
        <p class="nav-label">工作空间</p>
        <nav aria-label="主导航">
          <RouterLink to="/projects" class="nav-item" active-class="is-active">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg>
            研究项目
          </RouterLink>
        </nav>
        <div class="sidebar-note"><span class="tiny-dot"></span> 专注研究，有序推进。</div>
      </aside>
      <main class="main-content"><div class="content-container"><slot /></div></main>
    </div>
  </div>
</template>
