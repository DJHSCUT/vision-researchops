# RAG 检索 HTTP 503：证据、修复与验收

## 已确定的根因

2026-10-12 在本机只读复现了原检索错误。链路为：

`RagDevController.search → RagService.search → QdrantKnowledgeStore.search → QdrantVectorStore.doSimilaritySearch → QdrantFilterExpressionConverter.buildEqCondition`

RagService 的原过滤表达式包含 `ragNamespace == 'researchops-rag-demo-v1' && isDemo == true`。实际 Spring AI **2.0.1** 的 Qdrant EQ 转换器只接受 String / Number，不接受 Boolean。它在转换 `isDemo == true` 时抛出：

```text
java.lang.RuntimeException: java.lang.IllegalArgumentException:
Invalid value type for EQ. Can either be a string or Number
    at ...QdrantVectorStore.doSimilaritySearch(QdrantVectorStore.java:276)
Caused by: java.lang.IllegalArgumentException:
Invalid value type for EQ. Can either be a string or Number
    at ...QdrantFilterExpressionConverter.buildEqCondition(QdrantFilterExpressionConverter.java:102)
    at ...QdrantVectorStore.doSimilaritySearch(QdrantVectorStore.java:256)
```

QdrantKnowledgeStore 原先捕获 RuntimeException 后调用 `unavailable()`，丢弃异常和 cause，生成统一的 Qdrant 503；GlobalExceptionHandler 再把 BusinessException 的 code/message 输出给 HTTP 客户端。因此“请检查 gRPC 6334”是包装文字，不是连接失败证据。RagDevController 本身没有产生该异常。

该转换失败发生在发送查询 RPC 和调用查询 Embedding 之前。FixedDimensionEmbeddingModel.dimensions() 只是返回常量，不访问 Ollama；回归测试允许这一观测调用，并验证没有 embed() 和 Qdrant RPC。

## IPv6、SDK 与真实运行配置

本次诊断 shell 中 QDRANT_HOST、QDRANT_GRPC_PORT、RESEARCHOPS_RAG_QDRANT_HOST/PORT/USE_TLS 未设置。新启动的临时 RAG Spring Boot 实例从实际配置文件绑定得到：

```text
host=127.0.0.1 port=6334 tls=false
collection=researchops_knowledge_v1 timeout=PT15S
```

这些是本次诊断实例的实际值；不能据此推断用户此前启动的 8080 进程的环境。修复后的启动 INFO 日志直接输出最终绑定值，包含 YAML、环境变量或命令行覆盖后的结果，不输出凭据。

Qdrant REST 读取确认版本 **1.19.2**，Collection green、10 Points、1024 / Cosine。Spring AI 2.0.1 确实使用 Java SDK 1.18.0 的旧 Search RPC；本次只保留字符串 namespace 过滤后，旧 Search RPC 成功返回 3 条。原生 Query RPC 对照也返回 3 条。因此本环境没有复现 UNIMPLEMENTED 或查询 RPC 版本故障，不升级依赖、不迁移查询接口。

本次根因与 IPv6 无关：原异常是过滤转换器的本地 Java 异常，且 IPv4 下复现。没有继续调整 Host，也保留用户改成 127.0.0.1 的 YAML 默认值。

## 最小改动

- `RagService.java`：查询只使用专用字符串 namespace 过滤；结果映射严格要求 isDemo=true，并检查 documentId/source/title、非负整数 chunkIndex、内容和可选 score。元数据缺失或格式错误返回安全的 502，不生成字段值，不默默当作空结果。
- `QdrantKnowledgeStore.java`：增加带 cause 的安全诊断；检索错误按实际 gRPC 状态区分 503、502 和内部 500。Collection/导入路径的业务逻辑和对外错误契约保留，仅补充阶段与操作日志。
- `FixedDimensionEmbeddingModel.java`：调用失败与向量校验失败记录 Embedding 阶段，保留原公开消息和 503 契约；模型、向量内容和入库行为不变。
- `RagConfiguration.java`：记录最终 Qdrant Host、端口、TLS、Collection 与 timeout；传入 Collection 名用于 Embedding 日志。
- 新增 `RagDiagnostics.java`：统一安全 ERROR 日志，记录阶段、操作、异常类型、根因类型和 gRPC code，输出完整帧、Caused by 和 Suppressed。
- 新增 `RagSearchDiagnosticsTests.java`：17 个针对性离线用例；更新 RagServiceTests 中旧布尔过滤断言，保留原有用例和结果验证。
- 更新 `docs/rag-v1.md` 的检索过滤说明；新增本文档。

RagDevController、RagProperties、application-rag.yml、Maven 依赖和正式业务 API 未修改。未修改 Markdown 加载、指纹、稳定 ID、预检、补齐或回读流程；未修改 DEMO 文档、MySQL、五个 Agent Tool 或 AiChatService。没有删除或重建 Collection。

## 日志与 HTTP 语义

ERROR 日志示例结构：

```text
RAG failure stage=vector-store-search collection=researchops_knowledge_v1
operation=similaritySearch exceptionType=java.lang.RuntimeException
rootType=io.grpc.StatusRuntimeException grpcStatus=UNIMPLEMENTED
java.lang.RuntimeException: [SDK/request message omitted]
    at ...
Caused by: java.util.concurrent.ExecutionException: [SDK/request message omitted]
    at ...
Caused by: io.grpc.StatusRuntimeException: grpcStatus=UNIMPLEMENTED
    at ...
```

上面是格式示例，UNIMPLEMENTED 为离线测试情形，并不是本机真实故障。

任意 SDK 异常消息可能包含响应正文、请求内容或认证信息，因此不直接记录原消息。保留全部异常类型、原始 stack frames、cause 链和 suppressed；只保留确定安全的常量诊断、元数据字段名及 gRPC code。日志不记录 query、完整 Chunk、密钥、gRPC metadata/trailers。BusinessException 内保留原 cause 便于调试，HTTP 响应仍只输出安全 code/message/data。

| 情形 | 阶段 / HTTP |
|---|---|
| 输入问题/topK 无效 | 400，原验证规则 |
| Collection 缺失或维度/距离不兼容 | 409，原契约 |
| Collection 预检 RPC 失败 | qdrant-client / 503，日志给出具体 operation 和 gRPC code |
| Ollama 调用失败 | embedding / 503，保留 Ollama 提示 |
| 查询向量维度/数量/非有限值异常 | embedding-validation / 503，停止查询 |
| Search UNAVAILABLE / DEADLINE_EXCEEDED / RESOURCE_EXHAUSTED / CANCELLED | vector-store-search / 503 |
| Search INVALID_ARGUMENT / UNIMPLEMENTED 等实际 gRPC 故障 | vector-store-search / 502，避免归因于用户输入或网络 |
| Search 内部 Java / SDK 解码异常 | vector-store-search / 500，堆栈区分过滤、编码、payload 解码等位置 |
| 结果元数据缺失、类型错误、isDemo 不为 true | result-mapping / 502 |
| 正常空结果 | 200 + 空数组 |

## 测试与实际外部调用

执行：

```powershell
mvn -o -B '-Dtest=*,!VisionResearchopsApplicationTests' test
mvn -o -B -DskipTests package
```

全量离线 **543 个通过，Failures=0、Errors=0、Skipped=0**，包含原有 526 个和新增 17 个。仍排除会写真实 MySQL 的历史集成测试类。新增测试直接运行真实 Spring AI 2.0.1 转换器、VectorStore 和 payload 解码器，QdrantClient 与 EmbeddingModel 为 mock，验证 Top-K、空结果、4 种 gRPC 状态、查询 Embedding/维度异常、缺失/错误元数据、HTTP 语义、完整 cause/stack 以及日志脱敏。Maven package 和 Boot repackage 成功。

初次针对性测试中，“无任何 EmbeddingModel 交互”的断言碰到了观测框架读取 dimensions()；已改为准确断言“不调用 embed()”，完整 543 项验证随后通过。

经正常本机权限执行了**真实只读外部调用**：Ollama 查询向量、Qdrant 配置读取、旧 Search / 原生 Query 对照，以及临时 Spring Boot 实例上的 GET /dev/rag/search。临时实例只加载 RAG 和 HTTP 异常处理，随机绑定回环端口，排除 DataSource，不加载业务服务；验证结束已正常关闭，没有替换或停止用户的 8080 服务。

真实 HTTP 返回 **200，3 个 Chunk**，分数/标题：

| score | title |
|---:|---|
| 0.7649128437042236 | DEMO：3DGS 多视角数据采集与重叠率 |
| 0.747886598110199 | DEMO：遮挡与 3DGS 重建缺失问题 |
| 0.6536805629730225 | DEMO：3DGS 多视角数据采集与重叠率 |

真实查询前后 Points 数均为 **10**。没有调用导入、upsert、删除或 Collection 创建接口，没有调用真实 DeepSeek，没有写 MySQL，没有执行 Git commit。

证据文件（target 为忽略目录，保留本次诊断产物）：

- `target/rag-real-probe-before.log`：原布尔过滤异常及去掉布尔过滤后的旧 RPC 成功结果。
- `target/rag-real-http-after.log`：实际绑定配置、真实 HTTP 200 / 3 hits、10→10 Points、临时实例关闭记录。
- `target/rag-search-offline-test.log`：543 项离线测试成功汇总。
- `target/rag-search-package.log`：打包成功。

## 最少人工验收命令

先手动停止当前旧后端，使用新 JAR 启动。已有 DB_PASSWORD 配置沿用；不需要重新导入文档。启动命令显式指定已经验证的 Host/端口/TLS，优先于环境覆盖；也可以去掉这三个参数，依照启动 INFO 日志核对自己的环境。

```powershell
Set-Location C:\Documents\Projects\vision-researchops
java -jar target/vision-researchops-0.0.1-SNAPSHOT.jar --spring.profiles.active=rag --researchops.rag.qdrant-host=127.0.0.1 --researchops.rag.qdrant-port=6334 --researchops.rag.qdrant-use-tls=false 2>&1 | Tee-Object -FilePath target/rag-search-runtime.log
```

另一个终端执行：

```powershell
$q = [uri]::EscapeDataString('3DGS 视角覆盖不足会造成什么问题？')
$r = Invoke-RestMethod "http://localhost:8080/dev/rag/search?query=$q&topK=3"
$r.data | Format-List title,source,chunkIndex,isDemo,score,content
(Invoke-RestMethod 'http://127.0.0.1:6333/collections/researchops_knowledge_v1').result.points_count
```

期望 200、最多 3 个相关原文 Chunk，Points 仍为 10。如果还有错误，读取这一次的新日志：

```powershell
Get-Content target/rag-search-runtime.log -Tail 180
```

根据 `stage`、`operation`、`rootType` 和 `grpcStatus` 定位后续实际异常，不再由统一 503 文案猜测网络原因。本次已完成修复和验证，停止扩展，等待用户现有 8080 服务上的人工验收。
