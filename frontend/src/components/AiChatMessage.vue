<script setup>
import { computed, ref } from 'vue'
import { renderMarkdown } from '../utils/markdown'
import RagSources from './RagSources.vue'

const props = defineProps({ message: { type: Object, required: true }, retryDisabled: Boolean })
defineEmits(['retry'])
const citedDetails = ref(null)
const article = ref(null)
const markdown = computed(() => renderMarkdown(props.message.content, props.message.rag?.sources || []))

function openCitation(event) {
  const id = event.target.closest?.('[data-citation-id]')?.dataset.citationId
  if (!id || !props.message.rag?.sources.some(source => String(source.citationId) === id)) return
  if (citedDetails.value) citedDetails.value.open = true
  const card = article.value?.querySelector(`[data-source-id="${id}"]`)
  card?.scrollIntoView({ behavior: 'auto', block: 'nearest' })
  card?.focus({ preventScroll: true })
}
</script>

<template>
  <article ref="article" class="chat-message" :class="{ 'is-user': message.role === 'user', 'is-error': message.status === 'error' }">
    <span v-if="message.role === 'assistant'" class="ai-mark" aria-hidden="true">AI</span>
    <div class="message-body">
      <span class="message-author">{{ message.role === 'user' ? '你' : 'ResearchOps AI' }}</span>
      <div class="message-bubble">
        <p v-if="message.status === 'pending'" class="thinking" role="status">
          ResearchOps AI 正在思考<span class="thinking-dots" aria-hidden="true">…</span>
        </p>
        <template v-else-if="message.status === 'error'">
          <p role="alert">{{ message.content }}</p>
          <button type="button" class="retry-button" :disabled="retryDisabled" @click="$emit('retry')">重试</button>
        </template>
        <p v-else-if="message.role === 'user'">{{ message.content }}</p>
        <template v-else>
          <p v-if="message.rag?.insufficientEvidence" class="evidence-note" role="status">资料不足 · 暂无可靠回答</p>
          <p v-else-if="message.rag?.grounded" class="evidence-note is-grounded">引用来源已校验</p>
          <!-- Only escaped markdown-it output with HTML disabled reaches v-html. -->
          <div class="markdown-body" v-html="markdown" @click="openCitation" />
          <details v-if="message.rag?.sources.length" ref="citedDetails" class="source-details" open>
            <summary>引用来源 · {{ message.rag.sources.length }}</summary>
            <RagSources :sources="message.rag.sources" cited />
          </details>
          <details v-if="message.rag" class="source-details retrieval-details">
            <summary>检索详情 · 召回 {{ message.rag.retrievedCount }} 个片段</summary>
            <p class="retrieval-note">召回片段不代表回答实际引用；仅“引用来源”中的编号可作为回答来源。</p>
            <RagSources v-if="message.rag.retrievedSources.length" :sources="message.rag.retrievedSources" />
            <p v-else class="retrieval-note">没有可展示的有效检索来源。</p>
          </details>
        </template>
      </div>
    </div>
  </article>
</template>

<style scoped>
.chat-message { display: flex; align-items: flex-start; gap: 12px; min-width: 0; }
.chat-message.is-user { justify-content: flex-end; }
.ai-mark { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; margin-top: 22px; border-radius: 10px; background: #edf3fc; color: #2861af; font-size: 11px; font-weight: 650; }
.message-body { min-width: 0; max-width: 84%; }
.message-author { display: block; margin-bottom: 8px; color: #8b919b; font-size: 11px; }
.is-user .message-author { text-align: right; }
.message-bubble { border: 1px solid #eceef2; background: #fff; border-radius: 0 14px 14px 14px; padding: 15px 18px; }
.is-user .message-bubble { background: #edf3fc; border-color: #e1eaf7; border-radius: 14px 0 14px 14px; }
.message-bubble p { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.9; font-size: 14px; color: #4f5969; }
.is-error .message-bubble { background: #fdf8f8; border-color: #f0dede; }
.is-error .message-bubble p { color: #a65353; }
.message-bubble .thinking { color: #8b919b; font-size: 13px; }
.retry-button { color: #2861af; background: #fff; border: 1px solid #dce3ee; padding: 5px 12px; border-radius: 7px; margin-top: 10px; font-size: 12px; cursor: pointer; }
.retry-button:disabled { opacity: .5; cursor: not-allowed; }
.message-bubble .evidence-note { color: #957a49; background: #f8f4eb; border-radius: 7px; padding: 6px 10px; font-size: 11px; margin-bottom: 12px; }
.message-bubble .is-grounded { color: #597b6b; background: #eff5f1; }
.source-details { margin-top: 18px; border-top: 1px solid #eceef2; padding-top: 12px; }
.source-details summary { cursor: pointer; font-size: 12px; color: #657287; line-height: 1.8; }
.message-bubble .retrieval-note { font-size: 11px; color: #949ba6; line-height: 1.7; margin-top: 9px; white-space: normal; }
.markdown-body { color: #4f5969; font-size: 14px; line-height: 1.9; overflow-wrap: anywhere; min-width: 0; }
.markdown-body :deep(p) { white-space: normal; font-size: inherit; line-height: inherit; margin: 10px 0; }
.markdown-body :deep(> :first-child) { margin-top: 0; }
.markdown-body :deep(> :last-child) { margin-bottom: 0; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3), .markdown-body :deep(h4), .markdown-body :deep(h5), .markdown-body :deep(h6) { font-size: 1.1em; line-height: 1.6; letter-spacing: 0; margin: 18px 0 8px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 22px; margin: 10px 0; }
.markdown-body :deep(pre) { max-width: 100%; overflow-x: auto; padding: 12px; border-radius: 8px; background: #f4f6f9; font-size: 12px; line-height: 1.7; }
.markdown-body :deep(code) { font-family: ui-monospace, Consolas, monospace; background: #f4f6f9; border-radius: 3px; }
.markdown-body :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; margin: 12px 0; font-size: 12px; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 7px 10px; border: 1px solid #e5e9ef; min-width: 80px; }
.markdown-body :deep(th) { background: #f6f8fb; }
.markdown-body :deep(blockquote) { border-left: 3px solid #c5d8f2; padding-left: 12px; margin: 10px 0; color: #808b9b; }
.markdown-body :deep(a) { color: #2861af; text-decoration: underline; }
.markdown-body :deep(.citation-link) { border: 0; padding: 0 3px; background: #edf3fc; color: #2861af; border-radius: 4px; cursor: pointer; font-size: .9em; }
.thinking-dots { animation: thinking 1.4s ease-in-out infinite; }
@keyframes thinking { 50% { opacity: .25; } }
@media (prefers-reduced-motion: reduce) { .thinking-dots { animation: none; } }
@media (max-width: 700px) {
  .chat-message { gap: 8px; }
  .message-body { max-width: calc(100% - 40px); }
  .is-user .message-body { max-width: 92%; }
  .message-bubble { padding: 12px 14px; }
  .message-bubble p { font-size: 13px; }
  .markdown-body { font-size: 13px; }
}
</style>
