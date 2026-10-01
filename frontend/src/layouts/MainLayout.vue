<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink to="/projects" class="brand" aria-label="Vision ResearchOps home">
        <span class="brand-mark" aria-hidden="true">v<span>·</span></span>
        <span><strong>Vision ResearchOps</strong><small>Research Workflow Intelligence</small></span>
      </RouterLink>
      <div class="user-placeholder"><span class="workspace-label">Research workspace</span><span class="avatar" aria-label="User placeholder">R</span></div>
    </header>
    <div class="workspace">
      <aside class="sidebar">
        <p class="nav-label">WORKSPACE</p>
        <nav aria-label="Main navigation">
          <RouterLink to="/projects" class="nav-item" active-class="is-active">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg>
            Projects
          </RouterLink>
        </nav>
        <div class="sidebar-note"><span class="tiny-dot"></span> A space for focused research.</div>
      </aside>
      <main class="main-content"><div class="content-container"><slot /></div></main>
    </div>
  </div>
</template>
