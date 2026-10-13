<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { sendAiMessage } from '../api/ai'
import { askRag } from '../api/rag'
import { aiErrorMessage } from '../utils/rag'
import AiChatMessage from '../components/AiChatMessage.vue'

const mode = ref('agent')
const ragEnabled = import.meta.env.DEV
const sessions = reactive({
  agent: { messages: [], draft: '', sending: false },
  rag: { messages: [], draft: '', sending: false },
})
const session = computed(() => sessions[mode.value])
const messages = computed(() => session.value.messages)
const draft = computed({ get: () => session.value.draft, set: value => { session.value.draft = value } })
const sending = computed(() => session.value.sending)
const composing = ref(false)
const messageContainer = ref(null)
let nextId = 0
const suggestions = computed(() => mode.value === 'agent'
  ? ['R-4 的 PSNR 和 SSIM 是多少？', 'R-4 目前有哪些实验指标？', '有哪些正在进行的实验任务？']
  : ['为什么 3DGS 视角覆盖不足会导致重建缺失？', 'PSNR 和 SSIM 分别衡量什么？', '如何整理科研实验日志？'])
const maxLength = computed(() => mode.value === 'rag' ? 2000 : 4000)
const canSend = computed(() => !sending.value && !!draft.value.trim() && draft.value.length <= maxLength.value)
watch(mode, () => { composing.value = false; scrollToLatest() })

async function scrollToLatest() {
  await nextTick()
  const container = messageContainer.value
  if (container) container.scrollTop = container.scrollHeight
}

async function requestAnswer(requestMode, content, pending) {
  const target = sessions[requestMode]
  target.sending = true
  pending.status = 'pending'
  pending.content = ''
  pending.rag = undefined
  await scrollToLatest()
  try {
    if (requestMode === 'rag') {
      pending.rag = await askRag(content, 3)
      pending.content = pending.rag.answer
    } else pending.content = await sendAiMessage(content)
    pending.status = 'complete'
  } catch (failure) {
    pending.content = aiErrorMessage(failure, requestMode)
    pending.status = 'error'
  } finally {
    target.sending = false
    await scrollToLatest()
  }
}

function send() {
  if (!canSend.value) return
  const requestMode = mode.value
  const target = sessions[requestMode]
  const content = target.draft.trim()
  target.messages.push({ id: ++nextId, role: 'user', content, createdAt: new Date().toISOString() })
  target.messages.push({ id: ++nextId, role: 'assistant', content: '', question: content,
    createdAt: new Date().toISOString(), status: 'pending' })
  target.draft = ''
  return requestAnswer(requestMode, content, target.messages.at(-1))
}

function retry(message) {
  if (sending.value || message.status !== 'error') return
  return requestAnswer(mode.value, message.question, message)
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
        <p class="page-subtitle">查询实验记录，或依据知识库资料进行有来源的科研问答。</p>
        <div class="mode-switch" role="group" aria-label="助手工作模式">
          <button type="button" :aria-pressed="mode === 'agent'" @click="mode = 'agent'">实验数据查询</button>
          <button type="button" :aria-pressed="mode === 'rag'" :disabled="!ragEnabled" @click="mode = 'rag'">科研知识问答</button>
        </div>
        <p v-if="!ragEnabled" class="mode-note">科研知识问答仅在可信本机开发环境启用。</p>
      </div>
    </div>

    <div class="chat-workspace">
      <div ref="messageContainer" class="chat-messages" role="log" aria-label="问答消息" aria-live="polite" :aria-busy="sending" tabindex="0">
        <div v-if="!messages.length" class="chat-welcome">
          <span class="welcome-mark" aria-hidden="true">AI</span>
          <h2>ResearchOps AI</h2>
          <p>{{ mode === 'agent' ? '查询项目、任务、实验指标与日志。' : '依据知识库资料回答，并展示经过校验的引用来源。当前资料为 DEMO 教学示例。' }}</p>
          <div class="suggestions">
            <button v-for="question in suggestions" :key="question" type="button" @click="askSuggestion(question)">
              {{ question }}<span aria-hidden="true">↗</span>
            </button>
          </div>
        </div>
        <div v-else class="message-list">
          <AiChatMessage v-for="message in messages" :key="message.id" :message="message" :retry-disabled="sending" @retry="retry(message)" />
        </div>
      </div>

      <form class="chat-composer" @submit.prevent="send">
        <ElInput v-model="draft" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }"
          :maxlength="maxLength" resize="none" aria-label="你的问题"
          :placeholder="mode === 'agent' ? '例如：R-4 的 PSNR 和 SSIM 是多少？' : '例如：为什么 3DGS 视角覆盖不足会导致重建缺失？'"
          @keydown="handleKeydown" @compositionstart="composing = true" @compositionend="composing = false" />
        <div class="composer-footer">
          <span class="input-hint">Enter 发送 · Shift + Enter 换行</span>
          <div class="composer-actions">
            <span class="character-count" :class="{ 'at-limit': draft.length >= maxLength }" aria-live="polite">{{ draft.length }} / {{ maxLength }}</span>
            <ElButton native-type="submit" type="primary" :disabled="!canSend" :loading="sending">发送</ElButton>
          </div>
        </div>
        <p class="chat-note">消息仅在当前页面临时保留，刷新即清空；每次提问独立处理。{{ mode === 'agent' ? 'AI 回答仅供参考，请结合实验记录核实。' : '引用校验只确认来源对应，不保证每项论述均受证据支持。DEMO 资料不代表真实实验结论。' }}</p>
      </form>
    </div>
  </section>
</template>

<style scoped>
.ai-assistant { max-width: 980px; margin: 0 auto; min-width: 0; }
.ai-heading { margin-bottom: 28px; }
.ai-heading h1 { font-size: 36px; letter-spacing: -1.2px; }
.mode-switch { display: inline-flex; flex-wrap: wrap; gap: 4px; padding: 4px; margin-top: 18px; background: #eef1f5; border: 1px solid #e5e9ef; border-radius: 11px; }
.mode-switch button { padding: 9px 16px; border: 0; border-radius: 8px; color: #737d8c; background: transparent; font-size: 12px; cursor: pointer; }
.mode-switch button[aria-pressed="true"] { background: #fff; color: #2861af; box-shadow: 0 1px 4px #24262b08; }
.mode-switch button:disabled { opacity: .5; cursor: not-allowed; }
.mode-note { color: #858b95; font-size: 11px; margin-top: 10px; }
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
