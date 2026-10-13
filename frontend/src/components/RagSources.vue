<script setup>
defineProps({ sources: { type: Array, required: true }, cited: { type: Boolean, default: false } })
</script>

<template>
  <ul class="source-list" :class="{ 'cited-sources': cited }">
    <li v-for="item in sources" :key="`${item.citationId}-${item.source}-${item.chunkIndex}`"
      :data-source-id="cited ? item.citationId : undefined" :tabindex="cited ? -1 : undefined" class="source-card">
      <div class="source-title">
        <span class="source-number">{{ cited ? `[${item.citationId}]` : `检索编号 ${item.citationId}` }}</span>
        <span v-if="item.isDemo" class="demo-tag">DEMO</span>
        <strong>{{ item.title }}</strong>
      </div>
      <p class="source-path">{{ item.source }}</p>
      <p class="source-meta">Chunk {{ item.chunkIndex }} · Score {{ item.score.toFixed(3) }}
        <span v-if="!cited"> · {{ item.usedInContext ? '已提供给模型' : '未纳入回答上下文' }}</span>
      </p>
    </li>
  </ul>
</template>

<style scoped>
.source-list { list-style: none; padding: 0; margin: 10px 0 0; display: grid; gap: 8px; }
.source-card { padding: 12px 14px; background: #f8f9fb; border: 1px solid #e8ebf0; border-radius: 9px; min-width: 0; overflow-wrap: anywhere; }
.source-card:focus { outline: 2px solid #9cbce9; outline-offset: 2px; }
.source-title { display: flex; align-items: baseline; flex-wrap: wrap; gap: 7px; font-size: 12px; line-height: 1.7; color: #586474; }
.source-title strong { font-weight: 550; min-width: 0; }
.source-number { color: #2861af; font-size: 11px; }
.demo-tag { color: #8b784e; background: #f2eee5; border-radius: 4px; padding: 1px 5px; font-size: 9px; }
.source-path { font-family: ui-monospace, Consolas, monospace; color: #738094; margin: 6px 0; font-size: 11px; line-height: 1.6; }
.source-meta { font-size: 10px; line-height: 1.7; color: #9299a4; }
</style>
