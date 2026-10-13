# RAG V1 第二阶段：有来源问答交付与人工验收

2026-10-13。基于实际 Git 提交 `af6273a` 开发，开始时工作区干净。本轮只新增独立后端问答能力，不接入 `/api/ai/chat`。

## 文件清单与数据流

唯一修改的既有文件：`src/main/resources/application-rag.yml`，增加独立 ask 开关、阈值、长度限制和项目根目录配置。

新增实现位于 `src/main/java/com/djh/researchops/rag/`：

| 文件 | 用途 |
|---|---|
| RagAskRequest.java | question/topK DTO、trim、严格 JSON 类型与参数校验 |
| RagAskResponse.java | 答案、引用、召回来源、证据状态 DTO |
| RagAskProperties.java | 独立配置与范围检查 |
| RagAskConfiguration.java | 双 Profile 与显式开关，复用现有 ChatModel |
| RagAskController.java | POST /dev/rag/ask、本机直连限制、JSON 400 |
| RagAskService.java | 检索→Context→模型→引用校验→响应 |
| RagPromptContextBuilder.java | 来源安全映射、相关度筛选、JSON Context 与长度上限 |
| RagCitationValidator.java | 引用语法及 Context 目标校验 |

新增测试：RagAskServiceTests、RagPromptAndCitationTests、RagAskControllerTests、RagAskProfileTests，以及测试辅助类 RagAskTestSupport。新增 `scripts/verify-rag-ask.ps1` 和本文档。

未修改 RagService、KnowledgeStore、QdrantKnowledgeStore、RagConfiguration、RagProperties、RagDiagnostics、AiChatService、五个 Tools、原有 REST URL、DEMO 文档、MySQL 表、前端、Docker Compose 或 Maven 依赖。现有入库指纹、Point ID、去重、补齐、回读逻辑保持原样。

数据流：

`POST /dev/rag/ask → 请求校验 → 现有 RagService.search → Ollama 1024 维向量 → 现有 QdrantVectorStore → namespace/DEMO 元数据校验 → 分数及来源筛选 → 受限 JSON Context → 现有 DeepSeek ChatModel → 引用校验 → ApiResponse<RagAskResponse>`

没有重新实现 Embedding 或 Qdrant 客户端，没有重新引入 `isDemo == true` 布尔 EQ 查询。

## 请求与响应

请求：

```json
{"question":"3DGS视角覆盖不足为什么会导致局部重建缺失？","topK":3}
```

question 必填，先 strip，再限制为 1–2000 个 Java 字符；空白无效。topK 为整数 1–5，省略或 null 默认 3。字符串数字、浮点数、布尔、溢出数值和损坏 JSON 返回安全的 ApiResponse HTTP 400；严格反序列化仅作用于本 DTO，不改变原有业务接口的 Jackson 配置。

data 字段：

| 字段 | 含义 |
|---|---|
| answer | 校验通过的模型文本，附 DEMO 声明；不通过则返回固定资料不足说明 |
| sources | 回答实际出现的合法引用目标；按第一次提及顺序去重 |
| grounded | 有合格 Context 且编号格式与目标映射通过校验 |
| insufficientEvidence | 当前没有可可靠返回的问答结果；不用于表示外部服务宕机 |
| retrievedCount | 原始实际召回数量，包括低分或之后剔除的片段 |
| retrievedSources | 元数据及来源可安全追溯的召回来源，不宣称已被回答引用 |
| evidenceStatus | NO_RETRIEVAL / NO_QUALIFIED_EVIDENCE / MODEL_INSUFFICIENT_EVIDENCE / INVALID_MODEL_OUTPUT / CITATION_VALIDATION_FAILED / CITATIONS_VALIDATED |

每个 Source 包含 citationId、title、source、chunkIndex、score、isDemo、usedInContext。source 形如 `docs/rag-demo/04-occlusion-and-missing-regions.md`；score 为 Cosine 相似度，不是概率。低分片段可出现在 retrievedSources，但 usedInContext=false，且不能出现在 sources。损坏或不可安全映射的来源不会公开，故 retrievedSources 数量可能小于 retrievedCount。

## DeepSeek 共存与开发环境隔离

`@Profile("ai & rag & !prod & !production")` 使用 AND 表达式，两个 Profile 顺序都支持。还要求原 RAG enabled=true 和新增 `researchops.rag.ask.enabled=true`；新增开关默认 false。单独 rag 继续只做检索，单独 ai 继续原 Tools，普通模式不创建问答 Bean。prod/production 下不创建问答 Bean。

问答注入现有单一 ChatModel，不创建另一套 DeepSeek 自动配置或 ChatModel/ChatClient Bean。直接构造 SystemMessage/UserMessage 调用该 ChatModel，避免模板处理误解释 JSON 花括号。实际 Spring AI 2.0.1 OpenAiChatModel 要求 OpenAiChatOptions；从现有 getDefaultOptions().mutate() 复制，保留已有模型名、连接和凭据设置，单次请求设 temperature=0、输出上限，并清空 toolCallbacks/toolContext、设置 toolChoice=none，不修改共享默认配置。原有 AiChatService 仍按原逻辑附加五个 Tools。

接口只允许 loopback 直连，拒绝非本机和 Forwarded / X-Forwarded-For / X-Real-IP 请求。它是可信本机开发接口，不是认证机制；不要通过反向代理暴露，不要在生产启用。手工启动时同时绑定 server.address=127.0.0.1。

## Prompt、长度与路径

固定 system 规则要求：仅按 evidence 作答、保持 DEMO 教学语义、关键结论用单独 `[编号]`、不编造论文/实验/来源、资料无关或不足则输出 INSUFFICIENT_EVIDENCE。问题和文档仅放入 user 消息的 DATA_JSON，JSON 转义隔离引号/换行，不允许文档提升为系统消息；没有任何工具或命令执行能力。基本边界测试验证消息结构与后端执行能力，不能据此宣称真实模型对所有 Prompt Injection 都绝对免疫。

默认 Context 整段 user JSON（包括 question、元数据、转义开销）最多 6000 个 Unicode code point；每个 excerpt 最多 1200 个，不切断 Emoji 代理对。超出预算的片段不进入 Context，不发送全部召回内容。模型输出上限 1024 tokens、返回模型文本上限 6000 字符；过长输出、绝对文件路径或工具调用结果不作为可靠答案返回。

来源只接受项目根目录和配置 DEMO 目录内真实存在的 Markdown；真实路径规范化可拦截越界/软链接。只返回相对路径，title/source 长度及类型也验证。Context 不包含 Qdrant 元数据中的绝对 source URI；API key、数据库密码和任意 SDK 原始错误消息不会加入 Context 或日志。

项目根目录保持为 String，再使用 Path.of 解析。真实 Web 联调曾发现 Path 类型的 `.` 被 Spring 解析为 Tomcat 临时 docbase，现已修复，并加入 WebApplicationContextRunner 回归测试。默认必须从项目目录启动；从别处启动时显式设置 ask.project-root 和原 rag.demo-directory。

## 引用机制和边界

按本次检索排名分配 `[1]` 等编号，重复 Chunk 去重；编号仅在本次请求内稳定。过滤后可能有编号间隔。模型只能引用已进入 Context 的编号，不能引用虽然召回但未进入 Context 的低分/超预算片段。

支持单独 `[1][2]` 格式；不存在编号、`[01]`、`[1,2]`、未闭合括号或全角括号等均拒绝。任何一个引用非法即整条回答不通过，不保留“部分可信”的错误模型文本，也不标注 sources。来源字段全部取自本次真实检索映射，不采用模型编写的文档名、路径或 chunkIndex。

**编号校验只能证明引用目标存在、映射正确并且进入了 Context，不能证明每个自然语言论述都被证据充分支持。grounded=true 不等于逐句事实验证。** 人工仍须对照原始片段检查因果解释、限定条件、DEMO 语义以及引用位置；本阶段没有额外事实验证模型。

## 阈值和错误处理

默认 min-score=0.60，是本 DEMO 的初始配置，不能作为通用正确阈值。2026-10-13 真实只读检索样本：

| 问题 | Top-3 分数 | 0.60 下进入 Context |
|---|---|---:|
| 3DGS视角覆盖不足为什么会导致局部重建缺失？ | 0.822845 / 0.796488 / 0.716886 | 3 |
| COLMAP的平移向量是不是世界坐标中的相机位置？ | 0.743502 / 0.565164 / 0.483696 | 1 |
| 明天北京天气怎么样？ | 0.217730 / 0.181341 / 0.177388 | 0 |
| 怎样烤巧克力蛋糕？ | 0.235352 / 0.231809 / 0.221962 | 0 |

这是四个问题的观察，不是系统性相关性评估；低于阈值的内容也可能有一定相关性。扩充知识库后应重新收集相关/无关问题并调整 `RAG_ASK_MIN_SCORE` 或 `--researchops.rag.ask.min-score`。

| 情形 | 处理 |
|---|---|
| 空召回 / 全低分 / 没有合格可追溯来源 / Context 放不下 | HTTP 200，insufficientEvidence=true、grounded=false、sources=[]，不调用 DeepSeek |
| 模型明确资料不足 / 没有合法引用 / 引用错误 / 输出不安全 | HTTP 200，安全拒答、sources=[]，保留 evidenceStatus 与召回信息 |
| 现有检索返回损坏的原始 Document 元数据 | 保留原 RagService 的 HTTP 502 与安全日志；不调用 DeepSeek，不当作知识不足 |
| Qdrant / Ollama 故障或 Collection 不兼容 | 保留现有检索的 503/502/409 等错误，不转换成知识不足 |
| DeepSeek 调用异常 | HTTP 503，data=null，安全日志 stage=rag-answer-generation |
| 模型协议响应为空或缺失 Generation | HTTP 502，data=null |
| 参数无效 / 非本机或代理请求 / 问答未开启 | HTTP 400 / 403 / 404 |

## 验证结果与外部调用

最终执行：

```powershell
mvn -o -B '-Dtest=*,!VisionResearchopsApplicationTests' test
mvn -o -B -DskipTests package
```

**632 项离线测试，0 失败、0 错误、0 skipped**：原有 543 项全部通过，新增 89 项（Controller 25、Service 27、Prompt/Citation 25、Profile/SDK/Web Context 12）。按既有约定排除会写真实 MySQL 的历史集成测试类，不将它记为通过。模型和数据库使用 Mock 或本机固定响应假服务，覆盖全部请求参数、正常问答、编号/来源、低分/空结果、异常元数据、DEMO、角色隔离、工具清空、服务故障、Profile 与原五 Tools 回归。测试通过后 Maven package / Boot repackage 成功，生成原项目可执行 JAR。

真实只读调用仅访问本机 Ollama / Qdrant。另有一次组合检查使用真实检索与本机假模型端：相关问题 HTTP 200、grounded=true、retrievedCount=3、sources=1、相对路径正确；蛋糕问题 HTTP 200、insufficientEvidence=true、sources=[]，没有追加模型调用。临时随机端口 Spring Boot 与假模型端均已关闭，排除 DataSource，未替换用户 8080 服务。**这不是 DeepSeek 真实表现验证。**

Points 前后均为 10；未导入、upsert、删除或重建 Collection。没有调用真实 DeepSeek，没有修改 MySQL/前端/Compose，没有执行 Git commit。

证据：`target/rag-ask-offline-test.log`、`target/rag-ask-package.log`、`target/rag-ask-real-threshold.log`、`target/rag-ask-real-retrieval-fake-model.log`。PowerShell 脚本使用 UTF-8 BOM，已通过语法检查，并在 PowerShell 7.6.5 中用本机 Mock 验证 UTF-8 请求/响应和 HTTP 错误 JSON 解析；采用 Windows PowerShell 5.1 兼容写法，但本次执行环境未提供可确认的 5.1 运行结果。脚本主流程由用户人工运行，正常问答及 Tools 回归会调用配置的真实 DeepSeek。

## Windows PowerShell 人工验收

1. 在已有 DB_PASSWORD、LLM_API_KEY 本机环境下，从项目根目录启动新 JAR。先自行停止旧后端；无需重新导入。不要把真实凭据写入脚本或命令历史。

```powershell
Set-Location C:\Documents\Projects\vision-researchops
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=ai,rag --researchops.rag.ask.enabled=true --server.address=127.0.0.1
```

2. 另开 Windows PowerShell，执行验收脚本。替换为真实已有编号；默认 P-1/T-1/R-1 若不存在，Tools 应明确报告未找到，不能据此验证业务数据正确性。若本机已有脚本执行策略限制，可在当前命令使用进程级 Bypass；不改变系统策略。

```powershell
Set-Location C:\Documents\Projects\vision-researchops
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-rag-ask.ps1 -RunToolRegression -ProjectCode P-1 -TaskCode T-1 -RunCode R-1
```

脚本包含：正常 RAG 提问、实际 sources 与 retrievedSources 区分、相对路径/DEMO/引用编号匹配检查、蛋糕无关问题，以及五个原 AI Tools 提问。请求编码为 UTF-8 字节，响应按 RawContentStream UTF-8 解码，避免旧 Windows PowerShell 自动解码造成乱码。请在后台 ToolInvocationLog 核对 queryProjectTasks、queryTaskRuns、queryRunMetrics、queryRunLogs、queryRunArtifacts，并对照真实业务数据；HTTP 200 本身不证明模型路由和事实正确。

只检查正常问答/来源/无关问题时，去掉 -RunToolRegression。可改 BaseUrl，但本接口仅接受本机直连，不能使用代理转发。

3. 可控故障测试：不停止真实 Docker 服务，不修改 Points。分别在三个新终端启动以下临时实例（使用假模型凭据，地址在本机）。所有实例均显式使用正常的 0.60 阈值，避免先被阈值拒答掩盖模型连接测试。

```powershell
# 终端 A：Qdrant 连接故障；不会继续调用模型
Set-Location C:\Documents\Projects\vision-researchops
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=ai,rag --researchops.rag.ask.enabled=true --researchops.rag.ask.min-score=0.60 --server.address=127.0.0.1 --server.port=18081 --researchops.rag.qdrant-port=1 --researchops.rag.qdrant-timeout=3s --spring.ai.openai.api-key=local-fault-test-only --spring.ai.openai.base-url=http://127.0.0.1:1 --spring.ai.openai.timeout=3s
```

```powershell
# 终端 B：Ollama 故障，真实 Qdrant 只做读取
Set-Location C:\Documents\Projects\vision-researchops
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=ai,rag --researchops.rag.ask.enabled=true --researchops.rag.ask.min-score=0.60 --server.address=127.0.0.1 --server.port=18082 --researchops.rag.ollama-base-url=http://127.0.0.1:1 --researchops.rag.ollama-timeout=3s --spring.ai.openai.api-key=local-fault-test-only --spring.ai.openai.base-url=http://127.0.0.1:1 --spring.ai.openai.timeout=3s
```

```powershell
# 终端 C：模型端故障，Embedding/检索正常，只尝试本机关闭端口
Set-Location C:\Documents\Projects\vision-researchops
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=ai,rag --researchops.rag.ask.enabled=true --researchops.rag.ask.min-score=0.60 --server.address=127.0.0.1 --server.port=18083 --spring.ai.openai.api-key=local-fault-test-only --spring.ai.openai.base-url=http://127.0.0.1:1 --spring.ai.openai.timeout=3s
```

确认本机端口 1 没有服务。主实例 8080 仍保持正常配置，在验收终端执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-rag-ask.ps1 -RunFailureChecks
```

该命令先执行正常和无关问题，再检查三个临时实例。它们应返回 API code=503、data=null，不能返回知识不足 200。通过阶段日志区分 Qdrant、embedding 和 rag-answer-generation。测试后在各临时终端 Ctrl+C 关闭，仅停止本次临时 Java 实例。

4. 数据与开关复核：

```powershell
(Invoke-RestMethod 'http://127.0.0.1:6333/collections/researchops_knowledge_v1').result.points_count
# 预期仍为10；不要为本轮测试调用 /demo/import。
# 重启使用 --researchops.rag.ask.enabled=false 时 /dev/rag/ask 为404，检索/原AI仍按各自Profile工作。
```

完成后停止扩展，等待现有环境的真实 DeepSeek 回答、引用位置、DEMO 语义与五 Tools 的人工验收。
