# RAG V1 第一阶段：实现说明与人工验收

本阶段实现 Markdown → Chunk → Ollama Embedding → Qdrant → Top-K。检索直接返回原始片段、来源和分数，不生成回答。五份知识文件均为 DEMO 教学材料，不是真实实验结果、论文原文或官方规范。

本文的 526 项测试与外部调用记录描述第一阶段离线交付。后续真实联调发现的布尔过滤兼容问题、最小修复、543 项回归测试和真实检索结果见 [检索 503 排查报告](rag-search-503-diagnosis.md)。

## 文件与模块边界

修改：

- `pom.xml`：增加两个由现有 Spring AI BOM 2.0.1 管理的核心模块。
- `src/test/java/com/djh/researchops/AiProfileTests.java`：增加两种 Profile 顺序的 DeepSeek/Ollama 隔离测试。

新增：

- `src/main/resources/application-rag.yml`：RAG 专用可关闭配置。
- `src/main/java/com/djh/researchops/rag/RagProperties.java`：连接/切分配置与固定模型、维度。
- `RagConfiguration.java`：显式构建 Ollama Embedding 和 Qdrant VectorStore。
- `MarkdownTextSplitter.java`：继承 Spring AI TextSplitter 的中文边界切分器。
- `DemoDocumentLoader.java`：UTF-8 Markdown 加载、元数据、指纹和确定性 ID。
- `FixedDimensionEmbeddingModel.java`：每次调用固定模型，检查每条向量。
- `KnowledgeStore.java`、`QdrantKnowledgeStore.java`：存储接口与安全 Collection 检查、分页去重查询。
- `RagService.java`：显式导入、重复处理、回读确认、只读检索。
- `RagDevController.java`：开发接口。
- `docs/rag-demo/01-multiview-coverage.md`：采集和覆盖。
- `docs/rag-demo/02-image-metrics.md`：PSNR / SSIM。
- `docs/rag-demo/03-colmap-camera-pose.md`：COLMAP / 相机位姿。
- `docs/rag-demo/04-occlusion-and-missing-regions.md`：遮挡 / 缺失。
- `docs/rag-demo/05-experiment-log.md`：实验日志建议。
- `src/test/java/com/djh/researchops/rag/`：六个测试类，覆盖加载/切分/元数据、导入、Embedding、Qdrant、Profile、HTTP 映射。
- 本文档。

已有 AiChatService、五个 Tool、业务 REST URL、MySQL 表、前端和 `infra/compose.yaml` 均未修改。项目根配置实际为 `application.yaml`，不是 `application.yml`；该文件和 `application-ai.yml` 保持原样。本次没有加入 Chat Memory、SSE 或 Agent 框架。

## 依赖、配置与 ChatModel 隔离

新增 Maven 依赖：`org.springframework.ai:spring-ai-ollama` 和 `org.springframework.ai:spring-ai-qdrant-store`，均解析为 2.0.1。Qdrant 原生 Java Client 1.18.0 由 store 模块传递引入。

选择核心模块并显式配置 Bean，没有加入 `spring-ai-starter-model-ollama` 或 Qdrant Starter，因此没有新增 Ollama Chat 自动配置。已有 `ai` Profile 仍用 `spring.ai.model.chat=openai`，DeepSeek 仍由 OpenAiChatModel 调用。`rag` Profile 不覆盖 Chat 开关；`ai,rag` 与 `rag,ai` 均只有一个 ChatModel。RAG 只注入自己的 `ragEmbeddingModel`，不接入现有 ChatClient。

RAG 核心模块不读取 Starter 的 `spring.ai.ollama.*` / `spring.ai.vectorstore.qdrant.*` 属性。本项目使用 `researchops.rag.*` 并由 RagConfiguration 显式绑定；不要通过设置 `spring.ai.model.chat=ollama` 开启 RAG。固定模型名在 RagProperties.MODEL 中，不接受每请求模型覆盖。没有自动拉取模型。

基础配置与官方 API 已按 [Spring AI 2.0.1 Ollama 文档](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html) 和 [Qdrant 手动配置文档](https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html) 核对，并使用实际依赖编译和本机假 Ollama 响应验证 `/api/embed`。

普通启动不创建 RAG Bean。需人工启用 `rag` Profile，且 `researchops.rag.enabled=true`。设置 `RAG_ENABLED=false` 可以在保留 Profile 时关闭全部 RAG Bean 和接口。启动不导入、不初始化 Collection，也不探测 Ollama。Qdrant Client 的默认启动健康检查已显式关闭，Collection 校验在用户发起导入或检索时执行。

## 文档与切分

读取配置目录顶层的 `.md` 文件，按路径排序。使用 UTF-8，统一 BOM 与 CRLF，要求一级标题包含 DEMO，避免把未标注文档误当成 DEMO 导入。原文保留 Markdown，不调用生成模型。

采用 Spring AI Document 和 TextSplitter 基类；默认最大 500 个 Unicode code point，重叠 80 个。中文通常不能按空格切词，code point 计数也不会把 Emoji 的代理对切断。尽量在窗口后半段的换行或中文句末断开，找不到边界则按上限切分。重叠保留跨边界的语境，同时控制重复文本比例；500 字符是长度上限，不是 token 上限。该策略适用于本阶段的小型 Markdown，长表格或代码块的结构专用切分不在本阶段范围内。

每个 Chunk 包含 `documentId`、`source`（绝对 file URI）、`title`、从 0 开始的 `chunkIndex`、`isDemo=true`；另有 `chunkCount`、`ragNamespace`、`embeddingModel`、`importFingerprint`。

源文档 ID = namespace + source 的确定性 UUID；Point ID = documentId + chunkIndex 的确定性 UUID。指纹是规范化完整文档、切分器版本、大小、重叠和模型名的 SHA-256。元数据保存在 Qdrant payload，不参与 Embedding 文本。路径属于 source 身份的一部分：在同一路径下重复/重启导入保持稳定；搬迁目录需作为显式数据迁移处理。

## Collection、持久化和导入

- Collection：`researchops_knowledge_v1`。
- 单一未命名向量：1024 维，Cosine。
- Embedding：固定 `qwen3-embedding:0.6b`，`truncate=false`；每条返回向量必须为 1024 维且有限、非零。查询向量与文档向量都验证。
- Spring AI VectorStore 使用 gRPC `localhost:6334`；REST / Dashboard 是 `localhost:6333`。
- VectorStore 的 `initializeSchema=false`；显式导入时仅在目标 Collection 不存在时创建。检索不会创建 Collection。
- Collection 已存在时每次校验未命名向量、维度和距离。不兼容返回 HTTP 409，不删除、重建或修改 Collection。
- 沿用 `infra/compose.yaml` 的 `qdrant_storage:/qdrant/storage` 持久化卷，不创建另一个卷，不运行删卷命令。

只有 `POST /dev/rag/demo/import` 会导入，没有启动导入。先分页读取各源文档的现存 Chunk 并核对指纹；同内容、同切分配置的已有 ID 跳过，只写缺失 ID。所有源文件完成兼容性预检后才写 Points，写入后回读所有预期 ID 与指纹，校验成功才返回完成统计。

如果失败发生在部分写入后，接口返回 503，不宣称完成；再次导入可补齐，确定性 ID 的 upsert 不产生新的随机重复 Points。同进程导入串行执行；同版本跨实例重复请求也复用相同 ID。不同版本并发导入不作为本阶段的迁移方案，应由人工统一版本后导入。

如果原文内容或切分参数变化，返回 409，防止覆盖已有片段或遗留缩短文档的旧尾部。V1 没有更新/删除/自动同步能力；需要新 Collection 或单独规划迁移。检索在 Qdrant 中限定本 DEMO namespace，返回映射时严格验证 isDemo=true。Spring AI 2.0.1 的 Qdrant EQ 转换器不支持 Boolean，因此不在过滤表达式中使用 isDemo == true；同一 Collection 的其他 namespace 不参与查询。

## 离线验证

历史 `VisionResearchopsApplicationTests.testResearchProjectMapper` 是连接真实 MySQL 并插入记录的集成测试，不属于离线测试。本次保持该测试原样，以下命令只排除该类，执行其余全部测试：

```powershell
Set-Location C:\Documents\Projects\vision-researchops
mvn -o -B '-Dtest=*,!VisionResearchopsApplicationTests' test
mvn -o -B -DskipTests package
```

`-o` 要求依赖已缓存。新依赖首次需要联网下载；下载 Maven 依赖不等于调用真实模型。离线测试只使用 Mockito、内存存储和本机固定响应假服务。AI Tool Calling 的原有测试仍然使用本机模型假响应。测试先完整通过，随后打包跳过重复执行测试。

2026-10-12 验证结果：全量离线 526 个用例，Failures=0、Errors=0、Skipped=0；47 个新增 RAG 用例，加 2 个新增 Profile 顺序用例，原有离线 477 个用例全部通过。历史真实 MySQL 集成测试类按上面的命令排除，不记为通过或 skipped。Maven package / Spring Boot repackage 均 BUILD SUCCESS，生成 `target/vision-researchops-0.0.1-SNAPSHOT.jar`。

测试明细：DemoDocumentLoaderTests 11、FixedDimensionEmbeddingModelTests 8、QdrantKnowledgeStoreTests 8、RagDevControllerTests 3、RagProfileTests 5、RagServiceTests 12；AiProfileTests 共 21（本次增加 2）。最终日志为 `target/rag-offline-test-approved.log` 和 `target/rag-package.log`。target 中可能保留历史运行报告，核对本次汇总请使用这两个日志。

执行环境记录：沙箱内 Mockito 无法打开 JVM attach 管道，已终止该次运行；在正常本机权限下用相同 `-o` 命令完成全部离线测试。早期 Profile 测试曾触发 Qdrant SDK 默认健康/版本探测，日志显示未能取得服务版本；已关闭并补充启动无连接测试，最终离线运行没有该探测。本次没有调用真实 Ollama / DeepSeek，没有向真实 Qdrant 写入数据，也没有写 MySQL。联网操作仅用于官方文档查询与 Maven 依赖下载。

## 人工联调：PowerShell

以下步骤由人工执行，会实际访问本机 Ollama / Qdrant。后端沿用原来的 MySQL 配置，需已有 DB_PASSWORD 环境变量。

1. 在项目根目录检查服务和真实向量。

```powershell
Set-Location C:\Documents\Projects\vision-researchops
# 如果 Qdrant 没有运行，可使用现有 Compose 启动（不删除任何数据）
docker compose -f infra/compose.yaml up -d
Invoke-RestMethod 'http://localhost:6333/collections'
Invoke-RestMethod 'http://localhost:11434/api/tags'

$embedBody = @{ model = 'qwen3-embedding:0.6b'; input = @('3DGS 视角覆盖不足'); truncate = $false } | ConvertTo-Json
$embedding = Invoke-RestMethod 'http://localhost:11434/api/embed' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($embedBody))
if ($embedding.embeddings[0].Count -ne 1024) { throw '向量维度不是 1024，停止验收' }
$embedding.embeddings[0].Count
```

2. 新开终端启动后端，仅启用 RAG；启动成功本身不证明服务已连接。后续成功导入证明 Spring Boot 的 Embedding 客户端与服务实际交互成功。

```powershell
Set-Location C:\Documents\Projects\vision-researchops
# 沿用已有 DB_PASSWORD，不在命令里写密码。
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=rag
```

默认文档目录相对于启动工作目录。若从别处启动，可传 `--researchops.rag.demo-directory=C:/Documents/Projects/vision-researchops/docs/rag-demo`。

3. 返回第一个终端，显式导入并查询 Points。

```powershell
$base = 'http://localhost:8080'
$first = Invoke-RestMethod "$base/dev/rag/demo/import" -Method Post
$first | ConvertTo-Json -Depth 10
if ($first.code -ne 200) { throw '导入未成功' }
if ($first.data.documentCount -ne 5) { throw '文档数不是 5，请检查目录' }

$collection = 'http://localhost:6333/collections/researchops_knowledge_v1'
$info = Invoke-RestMethod $collection
$info.result.config.params.vectors | ConvertTo-Json
if ($info.result.config.params.vectors.size -ne 1024 -or $info.result.config.params.vectors.distance -ne 'Cosine') { throw 'Collection 不兼容' }

$points = Invoke-RestMethod "$collection/points/scroll" -Method Post -ContentType 'application/json' -Body '{"limit":100,"with_payload":true,"with_vector":true}'
$demoPoints = @($points.result.points | Where-Object { $_.payload.ragNamespace -eq 'researchops-rag-demo-v1' })
$demoPoints | ForEach-Object {
    if ($_.vector.Count -ne 1024) { throw 'Qdrant 存储向量维度不正确' }
    [pscustomobject]@{ id = $_.id; title = $_.payload.title; source = $_.payload.source; chunkIndex = $_.payload.chunkIndex; dimensions = $_.vector.Count }
} | Format-Table -Wrap
```

本 DEMO 当前规模小于 100 Points；若 Collection 含大量其他数据，可在 scroll 请求加入 namespace 过滤并按 next_page_offset 翻页。已有同一 DEMO 内容时首轮 addedCount 也可能为 0，应结合 skippedCount 与存储记录判断。

4. 浏览器打开 [Qdrant Dashboard](http://localhost:6333/dashboard)，进入 Collection，确认 1024 / Cosine、Points 和 payload。检查片段的 source、title、chunkIndex、isDemo、embeddingModel。

5. 自然语言检索：

```powershell
$query = [uri]::EscapeDataString('3DGS 视角覆盖不足会造成什么问题？')
$hits = Invoke-RestMethod "$base/dev/rag/search?query=$query&topK=3"
$hits.data | Select-Object title, source, chunkIndex, isDemo, score, content | Format-List
```

期望返回有关多视角覆盖或遮挡/缺失的原文片段，其中包含空洞、重建缺失、伪影等描述。实际排序和分数必须以真实模型输出为准，不预先伪造。score 是 Qdrant 的 Cosine 相似度，不是概率或答案置信度。Top-K 不超过 20；空检索返回空数组。

6. 再次导入，确认没有无故增加 Points。

```powershell
$beforeCount = (Invoke-RestMethod "$collection/points/count" -Method Post -ContentType 'application/json' -Body '{"exact":true}').result.count
$second = Invoke-RestMethod "$base/dev/rag/demo/import" -Method Post
$afterCount = (Invoke-RestMethod "$collection/points/count" -Method Post -ContentType 'application/json' -Body '{"exact":true}').result.count
$second.data | Format-List
if ($second.data.addedCount -ne 0 -or $second.data.skippedCount -ne $second.data.chunkCount -or $beforeCount -ne $afterCount) { throw '重复导入检查未通过' }
```

在没有其他并发写入的验收环境比较全 Collection count。可重启后端后再次执行相同导入，以确认去重依赖 Qdrant 持久状态而非进程内计数。

7. 验证与 DeepSeek 共存：停止仅 rag 的后端，使用原有 LLM_API_KEY 配置启动 `--spring.profiles.active=ai,rag`，也可交换顺序。重新执行检索；RAG 请求不会调用 DeepSeek。离线 `AiProfileTests` 验证两种顺序均只有 OpenAiChatModel，同时原有 Tool Calling / Evaluation 测试继续通过。本阶段不发送真实 AI Chat 请求；真实 DeepSeek 回归可在后续由人工按原有流程单独验收。

```powershell
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=ai,rag
```

## 故障含义

- Ollama 停止、模型未安装、接口错误或超时：503，提示检查 Ollama 和固定模型，不返回导入完成。
- 向量数量错误、非 1024 维、NaN / Infinity 或零向量：503，停止写入。
- Qdrant 不可用、gRPC 端口错误、写入或回读失败：503，提示检查 6334，重试可补齐。
- Collection 维度/距离/命名方式不兼容：409，已有数据保留。
- Collection 尚不存在但调用检索：409，提示先显式导入。
- 文档或切分版本变化：409，提示迁移，不覆盖已有文档。
- `query` 为空、超过 2000 字符，或 topK 不在 1–20：400。
- 未启用 rag Profile 或 RAG_ENABLED=false：开发接口不存在（404）。

本阶段没有执行 Git commit。真实入库、相关性和 Dashboard 检查仍需人工验收，不能由离线测试结果推断已完成真实入库。
