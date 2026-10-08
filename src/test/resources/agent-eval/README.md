# Agent Evaluation V1

这是离线、确定性的执行评测，不是 DeepSeek 工具选择准确率评测。

运行全部无需数据库的测试：

```powershell
mvn '-Dtest=*,!VisionResearchopsApplicationTests' test
```

仅运行评测及观测测试：

```powershell
mvn '-Dtest=AgentEvaluationTests,AgentEvalAssertionsTests,ToolObservabilityTests' test
```

报告生成在 `target/agent-eval/summary.md` 和 `summary.json`，JUnit 明细在 `target/surefire-reports/`。
报告给出总数、通过/失败数，每例预期/实际序列、六项指标、额外调用数、每次成功/失败及失败原因。
首次参数错误和后续重试分别计入轨迹；预期允许的重试不会被误计为额外调用。

## 用例维护

`cases.json` 每个用例包含：

- `id`：稳定的用例编号，用于 JUnit 展示和报告。
- `question`：固定测试问题，不参与生产业务路由。
- `fixture`：`default`、`empty_project`、`no_psnr` 或 `task_tie`，选择 Mock Service 数据。
- `script`：脚本模型发出的模型响应序列。`$latestTask` / `$latestRun` 根据收到的真实 Tool Result 的 createdAt 动态选择业务编号，时间相同时保留返回顺序。
- `expectedCalls`：独立断言契约，包括工具、参数、success 和可选 JSON Pointer `resultChecks`。
- `answerContains` / `answerForbidden`：检查必要编号、数值、状态、错误含义和禁止结果，不要求自然语言全文完全相等。

新增用例需要同时维护模型脚本和独立期望。脚本不能证明真实模型会作同样选择。
实际轨迹来自真实 Tool 方法执行的 Mockito spy，不是把脚本直接复制到报告；每轮结果必须由 Spring AI 自动回传。
`AgentEvalAssertionsTests` 用错误工具、参数、顺序、多余调用、遗漏调用、错误结果及错误答案的变异轨迹验证评测器能够发现偏差。

## 六个指标的 V1 定义

- Tool Selection Accuracy：预期与实际工具及次数的多重集合是否相等；当前是每用例的确定性通过标记，不是 DeepSeek 的统计准确率。
- Tool Argument Correctness：逐次比较实际调用参数；null 也是显式契约。
- Tool Sequence Correctness：完整有序工具列表相等。
- Unnecessary Tool Calls：实际工具次数超过期望次数的数量；非预期工具也计入。
- Error Handling：每次 success 及指定结果字段符合契约，停止/重试路径由完整序列验证。
- Task Completion：答案具有必要事实且不含禁止结果；这不是通用语义或语言质量评分。

只监听 `127.0.0.1`，使用假凭据和 Mock Service，不连接 MySQL 或真实 DeepSeek。
真实 LLM 评测需另行人工授权，并将实际模型轨迹与本地契约比较；本轮未提供自动付费调用入口。

## 日志与请求关联

生产日志使用 `com.djh.researchops.util.ToolInvocationLog` logger，INFO 每次调用一条：

```text
requestId=<uuid> callId=<uuid> tool=queryProjectTasks code=P-1 status=null result=SUCCESS count=2 durationMs=12 errorType=null
requestId=<same-uuid> callId=<other-uuid> tool=queryProjectTasks code=P-1 status=null result=INVALID_ARGUMENT count=0 durationMs=0 errorType=INVALID_STATUS
```

AiChatService 在当前同步 ChatClient 调用范围内创建 MDC requestId；所有自动多步调用复用该编号，结束或异常时恢复原 MDC。
每次 Tool 调用独立 callId；直接调用 Tool 而未经过 AiChatService 时 requestId 为 `-`。
此实现不承诺未来异步/跨线程调用的 MDC 自动传播。

结果分类为 SUCCESS、INVALID_ARGUMENT、NOT_FOUND、ERROR。空集合是 SUCCESS/count=0；未预期异常保留原抛出行为，日志只记异常类型，count=null。
编号先规范化，筛选值仅白名单合法值可写日志；非法原始编号/筛选值、Prompt、返回集合、异常消息和内部数据库 ID 均不记录。
非法筛选日志显示 null，并通过 INVALID_STATUS/INVALID_LEVEL/INVALID_TYPE 区分原因，不能将其解读为成功的无条件查询。
durationMs 使用单调时钟，测量参数规范化之后的 Tool 校验和业务查询耗时，不是整个模型请求耗时。
