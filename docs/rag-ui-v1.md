# RAG V1 第三阶段：AI 助手双模式

## 页面与数据流

沿用 `/ai` 路由、白灰蓝布局及 Element Plus 输入区。默认“实验数据查询”，调用 `POST /api/ai/chat`，请求仍为 `{ message }`。切换“科研知识问答”后调用 `POST /dev/rag/ask`，发送 `{ question: trimmedQuestion, topK: 3 }`，最多 2000 字符；Agent 保留 4000 字符。Enter 发送、Shift+Enter 换行及中文输入法组合输入行为保留。

两种模式分别保存页面内的消息、草稿和加载状态。异步请求捕获提交时的模式，响应返回原消息列表。每个模式等待时禁止重复提交，失败消息支持原位重试。刷新或离开并重新进入页面后记录清空，没有 localStorage、服务端聊天记忆或自动路由。

复用 Axios 实例与 ApiResponse 解包。RAG 请求通过单次请求的 `baseURL: ''` 避免错误拼成 `/api/dev/rag/ask`；AI 请求超时均为 180 秒。新增 `safeAiErrors` 选项只用于 AI 请求，抑制原始服务端消息的全局 toast，页面用固定安全文案显示 400、403、404、服务故障及超时。其他业务 API 的错误提示行为保留。

## 回答与来源

以实际 `RagAskResponse` 为准处理 `answer/sources/grounded/insufficientEvidence/retrievedCount/retrievedSources/evidenceStatus`。Source 还包含 `isDemo` 和 `usedInContext`。

- `sources` 是后端实际校验的引用来源；保留其原始 citationId，不重新编号，不从召回列表生成引用。卡片显示标题、DEMO、相对路径、Chunk 与三位小数得分。
- `retrievedSources` 在独立“检索详情”中折叠显示，以“检索编号”标识，并说明是否提供给模型；它不是回答引用清单。
- 已校验的 `[n]` 可点击展开对应来源并聚焦卡片；代码、链接文字内的编号不转换为引用按钮。来源路径仅为文本，不拼下载 URL。
- `insufficientEvidence=true` 显示“当前知识库没有足够的相关资料支持可靠回答”，隐藏实际引用列表，仍可查看有效召回详情。
- 非法相对路径、缺失元数据、重复编号或与召回不一致的成功引用响应不被认证；响应异常作为服务错误显示，不冒充知识不足。
- 503 等 HTTP/网络故障显示独立错误和重试，不生成资料不足结果，不展示后端异常或密钥。显示文本中常见 Windows、UNC、file URI 和 Unix 本机路径会被隐藏。

编号校验只确认引用目标存在、对应关系有效，不能证明所有自然语言论述都受证据支持。页面说明保留此边界和 DEMO 语义。

## Markdown 安全边界

新增唯一运行时依赖 `markdown-it 15.0.2`。采用 `html:false`、`breaks:true`，关闭 linkify/typographer；不使用渲染插件、富文本编辑器或语法高亮执行器。原始 HTML 被转义，默认渲染器转义文本、代码和属性；唯一自定义 HTML 为已验证整数编号生成的引用按钮。外部图片不加载，只显示其转义替代文本。

仅允许无用户名/密码的完整 HTTP/HTTPS 链接，添加 `target="_blank" rel="noopener noreferrer"`。javascript/vbscript/data/file、协议相对和项目相对链接不会成为可点击链接。支持标题、段落、粗体、代码块、列表、表格及换行；宽表格/代码块内部滚动，标题与路径可换行。

安全策略依据 [markdown-it 官方安全说明](https://github.com/markdown-it/markdown-it/blob/master/docs/safety.md)：关闭 HTML 的解析策略不需要另加 HTML 允许插件。本次有针对性的 DOM 测试覆盖脚本、事件属性、SVG、iframe、伪造引用及危险链接。

## 本机代理边界

Vite 保持 `127.0.0.1:5173`，后端目标为 `http://127.0.0.1:8080`。只增加精确匹配 `/dev/rag/ask`（及查询字符串）的代理，不代理导入或其他 `/dev` 路由；不改路径、不启用 xfwd，不剥除身份头绕过后端检查。

开发 middleware 拒绝非 loopback 连接、非本机 Host、跨源 Origin 和 Forwarded/X-Forwarded-For/X-Real-IP 请求。实际拒绝转发头请求 HTTP 403。构建后的 RAG 按钮禁用，API 封装也拒绝调用；`preview.proxy={}` 显式阻止预览服务器继承开发代理。未增加生产部署代理、认证绕过、外部绑定或公开 API。未来部署需要另外设计认证与权限受控的业务接口。

## 文件清单

修改：

- `frontend/src/views/AiAssistantView.vue`：模式、分离的临时状态、示例、限制、重试。
- `frontend/src/components/AiChatMessage.vue`：Markdown、证据状态、引用/检索详情。
- `frontend/src/api/ai.js`：AI 安全错误处理选项，原 API/请求语义不变。
- `frontend/src/utils/request.js`：单次 AI 请求的安全错误选项。
- `frontend/vite.config.js`：本机精确代理、预览隔离及测试配置。
- `frontend/package.json`、`frontend/package-lock.json`：Markdown 依赖、测试依赖和 npm test。
- `frontend/README.md`：本机启动和测试说明。

新增：

- `frontend/src/api/rag.js`：RAG 开发 API。
- `frontend/src/utils/rag.js`：DTO、元数据、来源映射及安全显示检查。
- `frontend/src/utils/markdown.js`：安全 Markdown 和引用按钮。
- `frontend/src/components/RagSources.vue`：来源卡片。
- `frontend/dev/ragProxy.js`：可信本机代理守卫。
- `frontend/tests/api.test.js`、`assistant.test.js`、`markdown.test.js`、`rag.test.js`、`ragProxy.test.js`、`fixtures.js`：离线测试与测试夹具。
- `docs/rag-ui-v1.md` 和 `docs/screenshots/rag-v1-ui/*.jpg`：交付记录及真实浏览器截图。

## 实际验收结果（2026-10-13）

此前前端没有 npm test 脚本，本次新增 5 个测试文件，共 **76 项全部通过**。覆盖 Agent 原请求、RAG 请求与包装、引用编号/对应关系、资料不足、异常响应、HTTP 400/503、重试、模式间未完成响应、IME、重复提交、刷新清空、恶意 Markdown、本机代理、生产隔离和长内容容器。测试使用 Mock 传输/API，不评价真实 DeepSeek 的回答质量。后端既有 632 项测试本轮未重新执行；未修改后端源码。

`npm run build` 成功（1736 modules），`git diff --check` 成功。依赖安装 audit 报告 0 vulnerabilities。最初沙箱内的 DNS/realpath EPERM 阻塞已通过宿主机授权执行解决，最终测试与构建均实际完成。

真实浏览器通过本机 Vite 访问已运行的 Spring Boot，未拦截或伪造 HTTP 响应：

1. Agent：R-4 返回 PSNR **28.1 dB**、SSIM **0.912**，粗体和列表正常。
2. RAG：询问视角覆盖不足与重建缺失，返回 DEMO 回答；答案和卡片的引用编号均为 **1、2、3**。来源为遮挡文档 Chunk 0（0.859），多视角文档 Chunk 0（0.823）、Chunk 1（0.735）。展示的来源由实际响应 `sources` 提供，没有生成额外引用。
3. 无关问题：巧克力蛋糕返回资料不足；最后一条回答无实际引用卡片，检索详情仍独立保留。
4. 实际空问题请求返回 **HTTP 400**；带 X-Forwarded-For 的开发代理请求返回 **HTTP 403**，没有放宽后端本机限制。
5. 1366 宽桌面与 390 宽窄屏均无页面横向溢出；切换后 Agent/RAG 记录没有交叉；刷新清空消息。

实际通过现有后端调用了 DeepSeek/Ollama/Qdrant；没有导入、更新、删除或重建向量数据。只读核对现有 Collection 仍为 **10 Points**。未修改后端、数据库、模型配置、Compose，未执行 Git commit。

截图均为真实 API 场景，不是 Mock：

- `screenshots/rag-v1-ui/agent-r4-real.jpg`：Agent R-4 指标。
- `screenshots/rag-v1-ui/rag-answer-real.jpg`：视角覆盖不足回答正文。
- `screenshots/rag-v1-ui/rag-sources-real.jpg`：上述回答的引用卡片。
- `screenshots/rag-v1-ui/insufficient-evidence-real.jpg`：巧克力蛋糕资料不足。
- `screenshots/rag-v1-ui/mobile-real.jpg`：390 宽窄屏的真实问答记录。

## 人工复核

保持现有后端 `ai,rag` Profiles 且 `researchops.rag.ask.enabled=true`；后端应只绑定本机，沿用既有 API Key/模型配置。不要重复导入 DEMO 或修改 Collection。Vite 已在本轮启动，若 5173 正在运行，可直接打开页面，无需再次启动。

```powershell
Set-Location 'C:\Documents\Projects\vision-researchops\frontend'
npm test
npm run build
# 仅在 5173 尚未运行时：
npm run dev
```

打开 `http://127.0.0.1:5173/ai`：

1. 默认 Agent 输入“R-4 的 PSNR 和 SSIM 是多少？”，检查 Network 为 `/api/ai/chat`。
2. 切换 RAG 输入“为什么 3DGS 视角覆盖不足会导致重建缺失？”，检查请求为 `/dev/rag/ask`、question 已 trim、topK=3。
3. 对比 Network 响应 `data.sources` 和卡片：编号、标题、路径、Chunk、得分及 DEMO 一致；只有实际 sources 成为引用链接。展开检索详情，对比 `retrievedSources`，不将其全部当作引用。
4. 输入“如何在家制作巧克力蛋糕？”，应显示资料不足，且无实际引用。
5. 输入草稿并反复切换；等待中的响应应回到提交时模式；Enter/Shift+Enter 和输入法正常；刷新后回到默认空白 Agent 页面。
6. 可控服务异常：在自己启动的本机后端终端按 Ctrl+C，页面提问应显示服务不可用和重试；用原有启动命令恢复后点击重试。若要检查真实 503，可由人工在不修改数据的情况下暂时停止 Ollama 后做 RAG 提问，再恢复 Ollama。不要删除容器/数据卷或 Collection。本轮未自动停止用户服务，HTTP 503 的前端处理以离线测试验证。
7. `npm run build` 后的预览只允许 Agent 功能，RAG 按钮应禁用；不要新增生产 `/dev` 转发。

本轮完成后停止扩展，等待人工验收。
