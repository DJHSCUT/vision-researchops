<script setup>
defineProps({ message: { type: Object, required: true } })
</script>

<template>
  <article class="chat-message" :class="{ 'is-user': message.role === 'user', 'is-error': message.status === 'error' }">
    <span v-if="message.role === 'assistant'" class="ai-mark" aria-hidden="true">AI</span>
    <div class="message-body">
      <span class="message-author">{{ message.role === 'user' ? '你' : 'ResearchOps AI' }}</span>
      <div class="message-bubble">
        <p v-if="message.status === 'pending'" class="thinking" role="status">
          ResearchOps AI 正在思考<span class="thinking-dots" aria-hidden="true">…</span>
        </p>
        <p v-else :role="message.status === 'error' ? 'alert' : undefined">{{ message.content }}</p>
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
.thinking-dots { animation: thinking 1.4s ease-in-out infinite; }
@keyframes thinking { 50% { opacity: .25; } }
@media (prefers-reduced-motion: reduce) { .thinking-dots { animation: none; } }
@media (max-width: 700px) {
  .chat-message { gap: 8px; }
  .message-body { max-width: calc(100% - 40px); }
  .is-user .message-body { max-width: 92%; }
  .message-bubble { padding: 12px 14px; }
  .message-bubble p { font-size: 13px; }
}
</style>
