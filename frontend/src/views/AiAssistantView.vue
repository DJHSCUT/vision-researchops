<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sendAiMessage } from '../api/ai'
import AiChatMessage from '../components/AiChatMessage.vue'

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const composing = ref(false)
const messageContainer = ref(null)
let nextId = 0
const suggestions = ['Run 1 的 PSNR 是多少？', 'Run 1 目前有哪些实验指标？', 'PSNR 是什么？']
const canSend = computed(() => !sending.value && !!draft.value.trim() && draft.value.length <= 4000)

async function scrollToLatest() {
  await nextTick()
  const container = messageContainer.value
  if (container) container.scrollTop = container.scrollHeight
}

async function send() {
  if (!canSend.value) return
  const content = draft.value.trim()
  sending.value = true
  messages.value.push({ id: ++nextId, role: 'user', content, createdAt: new Date().toISOString() })
  const answer = {
    id: ++nextId, role: 'assistant', content: '', createdAt: new Date().toISOString(), status: 'pending',
  }
  messages.value.push(answer)
  const pending = messages.value.at(-1)
  draft.value = ''
  await scrollToLatest()
  try {
    pending.content = await sendAiMessage(content)
    pending.status = 'complete'
  } catch (failure) {
    pending.content = failure.response?.status === 503
      ? 'AI 服务暂时不可用，请稍后重试。'
      : failure.userMessage || 'AI 服务暂时不可用，请稍后重试。'
    pending.status = 'error'
    // HTTP 错误由共享 Axios 层提示，避免重复弹出；这里只提示内容异常。
    if (!failure.response && !failure.isAxiosError) ElMessage.error(pending.content)
  } finally {
    sending.value = false
    await scrollToLatest()
  }
}

function handleKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || composing.value || event.keyCode === 229) return
  event.preventDefault()
  send()
}

function askSuggestion(question) {
  if (sending.value) return
  draft.value = question
  send()
}
</script>

<template>
  <section class="ai-assistant" aria-labelledby="ai-heading">
    <div class="page-heading ai-heading">
      <div>
        <p class="eyebrow">科研工作空间</p>
        <h1 id="ai-heading">AI 科研助手<span class="heading-dot">.</span></h1>
        <p class="page-subtitle">基于 Vision ResearchOps 实验数据与 AI 能力，为科研实验提供查询和分析支持。</p>
      </div>
    </div>

    <div class="chat-workspace">
      <div ref="messageContainer" class="chat-messages" role="log" aria-label="问答消息" aria-live="polite" :aria-busy="sending" tabindex="0">
        <div v-if="!messages.length" class="chat-welcome">
          <span class="welcome-mark" aria-hidden="true">AI</span>
          <h2>ResearchOps AI</h2>
          <p>可以查询实验指标，也可以咨询三维视觉和科研实验相关问题。</p>
          <div class="suggestions">
            <button v-for="question in suggestions" :key="question" type="button" @click="askSuggestion(question)">
              {{ question }}<span aria-hidden="true">↗</span>
            </button>
          </div>
        </div>
        <div v-else class="message-list">
          <AiChatMessage v-for="message in messages" :key="message.id" :message="message" />
        </div>
      </div>

      <form class="chat-composer" @submit.prevent="send">
        <ElInput v-model="draft" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }"
          :maxlength="4000" resize="none" aria-label="你的问题"
          placeholder="请输入你的问题，例如“Run 1 的 PSNR 是多少？”"
          @keydown="handleKeydown" @compositionstart="composing = true" @compositionend="composing = false" />
        <div class="composer-footer">
          <span class="input-hint">Enter 发送 · Shift + Enter 换行</span>
          <div class="composer-actions">
            <span class="character-count" :class="{ 'at-limit': draft.length >= 4000 }" aria-live="polite">{{ draft.length }} / 4000</span>
            <ElButton native-type="submit" type="primary" :disabled="!canSend" :loading="sending">发送</ElButton>
          </div>
        </div>
        <p class="chat-note">每次提问独立处理，不会记住之前的对话。AI 回答仅供参考，请结合实验记录核实。</p>
      </form>
    </div>
  </section>
</template>

<style scoped>
.ai-assistant { max-width: 980px; margin: 0 auto; min-width: 0; }
.ai-heading { margin-bottom: 28px; }
.ai-heading h1 { font-size: 36px; letter-spacing: -1.2px; }
.chat-workspace { display: flex; flex-direction: column; height: clamp(480px, calc(100dvh - 300px), 820px); min-width: 0; overflow: hidden; background: #fafbfc; border: 1px solid #e8ebf0; border-radius: 18px; box-shadow: 0 4px 24px #24262b03; }
.chat-messages { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 30px; }
.message-list { display: grid; gap: 26px; }
.chat-welcome { min-height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; padding: 24px 0; }
.welcome-mark { display: grid; place-items: center; width: 54px; height: 54px; border-radius: 16px; color: #2861af; background: #edf3fc; font-size: 19px; font-weight: 600; margin-bottom: 22px; }
.chat-welcome h2 { font-size: 25px; letter-spacing: -.6px; }
.chat-welcome p { max-width: 440px; font-size: 13px; line-height: 1.9; color: #8b919b; margin: 14px 0 24px; }
.suggestions { display: grid; gap: 9px; width: min(100%, 360px); }
.suggestions button { display: flex; justify-content: space-between; gap: 12px; text-align: left; background: #fff; border: 1px solid #e8ebf0; border-radius: 10px; padding: 12px 15px; font-size: 12px; color: #667080; cursor: pointer; }
.suggestions button:hover { background: #f0f5fc; border-color: #c5d8f2; color: #2861af; }
.suggestions button span { color: #99a3b1; }
.chat-composer { flex-shrink: 0; padding: 20px 24px 16px; background: #fff; border-top: 1px solid #eceef2; }
.chat-composer :deep(.el-textarea__inner) { background: #f8f9fb; font-size: 13px; }
.composer-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; }
.input-hint, .character-count { font-size: 10px; color: #99a0ab; }
.composer-actions { display: flex; align-items: center; gap: 14px; }
.at-limit { color: #a17b3a; }
.chat-note { margin-top: 13px; color: #99a0ab; font-size: 10px; line-height: 1.7; }
@media (max-width: 700px) {
  .ai-heading h1 { font-size: 30px; }
  .ai-heading .page-subtitle { font-size: 12px; }
  .chat-workspace { height: clamp(440px, calc(100dvh - 290px), 720px); border-radius: 14px; }
  .chat-messages { padding: 20px 14px; }
  .chat-composer { padding: 16px 14px 12px; }
  .composer-footer { flex-wrap: wrap; }
  .composer-actions { margin-left: auto; gap: 10px; }
  .input-hint { font-size: 9px; }
  .chat-welcome h2 { font-size: 22px; }
}
</style>
