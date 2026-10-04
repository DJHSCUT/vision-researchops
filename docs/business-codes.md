# 用户业务编号

Project、Task、Run 分别使用 `P-1`、`T-1`、`R-1` 格式，无前导零。编号是持久化业务身份，内部主键和所有 REST URL 保持不变。AI Tool 接收 Task / Run 业务编号，Code 到内部 ID 的转换由 Tool 调用 Service 完成，不需要模型执行解析步骤。

## 部署与历史迁移

1. 备份 `research_project`、`experiment_task`、`experiment_run`。已有序列表时一并备份。
2. 停止所有应用实例、后台写入和导入任务，在维护窗口中迁移。
3. 在 MySQL 8.0 的客户端中人工执行 [迁移 SQL](sql/migrate-business-codes.sql)。需要 ALTER、CREATE ROUTINE 等权限；不要启用遇错继续的 `--force`。
4. 检查 NULL、重复、格式、三个唯一索引与 NOT NULL，以及序列高水位。确认对象数量、关联、名称、状态和历史时间不变。
5. 部署新版后端和前端，恢复服务。未执行迁移的旧数据库无法运行新版 Entity 查询，不能先上线后迁移。

DDL 会隐式提交，整个迁移不能整体回滚。每类数据补齐和序列更新在事务中完成，异常回滚该类数据。修复失败原因后可重跑：已有 Code 不改，序列不会下降，只补齐 NULL；首次迁移按 `id ASC` 从 1 分配。迁移脚本不会删除业务数据，也不会自动运行。

新建空数据库使用 [schema.sql](sql/schema.sql)，不要用它替代现有数据库迁移。

## 分配与不复用

三个创建 Service 注入 `BusinessCodeService`。其三个公开方法经 Spring 代理进入 `REQUIRES_NEW` 事务，`SELECT ... FOR UPDATE` 锁住类型对应的序列行，递增并更新成功后提交，再返回 Code。读取与写入始终处于同一事务，运行时不读取对象表的 COUNT、MAX(id) 或 MAX(code)。

编号事务独立于对象创建：即使调用方外层事务后来回滚，已提交的序列也不会回退。删除对象不访问序列表。唯一索引作为最后一道约束。序列表必须使用 InnoDB，不能删除、重建、手工调低高水位；数据库恢复和导入时必须保留已有 Code，并将序列提升至不低于所有已分配/已公布编号的高水位。缺失或损坏序列会报错，不会自动从 0 重建。

DTO 无 Code 字段，创建由后端赋值，PATCH 只更新现有业务字段。Entity 的 Code 设置 `updateStrategy=NEVER`，MyBatis-Plus 实体更新也不会修改它。此约束不替代管理员直接 SQL 的权限管理。

## 验证范围与人工验收

无数据库测试验证序列委托、事务代理边界、更新失败回滚、独立分配提交、创建/更新/删除/VO/DTO 和已有业务兼容性；未使用真实 MySQL 并发测试，InnoDB 行锁及 UNIQUE 需在人工迁移后的数据库验收。

- 列表/详情显示 `P-x`，任务/运行名称前显示低饱和 `T-x` / `R-x` 标签；搜索项目编号可定位项目。
- 新建项目、任务、运行，刷新或重新打开页面，编号保持一致。
- 删除一个可丢弃的对象再创建，编号应继续递增，不复用删除编号。
- 同时从两个客户端创建同类对象，返回不同 Code；跨类型各自递增。
- 编辑名称/状态，Code 保持不变；REST 路径参数和父子关联仍用内部 ID，AI Tool 使用业务编号。
- Code 缺失时页面显示“编号待补齐”，不从数据库 ID 拼造 Code。

本次代码交付不执行迁移或真实数据创建/删除。上述真实数据库场景须由用户在执行 SQL 后验收。

## AI 业务编号接口

现有四个工具名称不变，参数为：

- `queryTaskRuns(String taskCode, String status)`
- `queryRunMetrics(String runCode)`
- `queryRunLogs(String runCode, String level)`
- `queryRunArtifacts(String runCode, String type)`

`BusinessCodeParser` 统一处理首尾空白、大小写及可省略的连字符：`T1`、`t1`、`t-1` → `T-1`；Run 同理。拒绝空输入、0、负数、非数字、前导零、错误前缀和超出有符号 BIGINT 范围的编号。非法编号或筛选参数返回结构化失败，不执行 Service 查询。

`ExperimentTaskService.getByTaskCode` 和 `ExperimentRunService.getByRunCode` 使用唯一 Code 字段的等值查询。Tool 取得真实内部 ID 后复用原有运行/指标/日志/产物 Service；缺失对象返回包含业务编号的失败结果，未知异常继续抛出。Tool 不依赖 Mapper，不新增 Resolver Tool，也不将数字后缀作为数据库 ID。

AI 专用的 Run、Metric、Log、Artifact Item 仅保留决策所需字段，四个 Tool Result 均不返回内部主键或外键。REST 的原有 VO 保持完整。日志内容继续使用 `content` 字段；产物只返回元数据，不读取文件。

System Prompt 将“Task 1 / 任务 1”解释为 `T-1`，“Run 2 / 运行 2”解释为 `R-2`。用户不需要提供数据库 ID。多步查询先得到运行列表，再根据真实 `createdAt` 最大选择 Run，取 `runCode` 调用指标工具；创建时间相同选列表中先出现的候选。列表保留 Service 的 `id DESC` 排序，因此维持原有内部 ID 较大优先的稳定规则，模型无需看到 ID。

Task 不存在或无 Run 时停止；最新 Run 没有 PSNR 时明确说明，不查询旧 Run、不用 SSIM 替代。单 Run 指标问题只调用指标工具；一般知识问题无需业务 Tool。执行循环仍由 Spring AI 自动处理。

本机脚本模型测试验证真实 Spring AI 回传 Tool Result 和连续执行机制，并检查传给模型的数据不含内部 ID。模型选择代码仅位于测试中，不代表真实 DeepSeek 必然遵循 Prompt；真实模型路由仍需用 `T-1 最近一次运行的 PSNR？`、`R-2 有哪些错误日志？` 等问题联调验收。

### 本轮文件清单

新增 8 个文件：

- `src/main/java/com/djh/researchops/util/BusinessCodeParser.java`
- `src/main/java/com/djh/researchops/vo/RunToolItem.java`
- `src/main/java/com/djh/researchops/vo/MetricToolItem.java`
- `src/main/java/com/djh/researchops/vo/LogToolItem.java`
- `src/main/java/com/djh/researchops/vo/ArtifactToolItem.java`
- `src/test/java/com/djh/researchops/BusinessCodeParserTests.java`
- `src/test/java/com/djh/researchops/BusinessCodeLookupTests.java`
- `src/test/java/com/djh/researchops/BusinessCodeToolTests.java`

修改 19 个文件：

- `src/main/java/com/djh/researchops/service/AiChatService.java`
- `src/main/java/com/djh/researchops/service/ExperimentTaskService.java`
- `src/main/java/com/djh/researchops/service/ExperimentRunService.java`
- `src/main/java/com/djh/researchops/tool/TaskRunTools.java`
- `src/main/java/com/djh/researchops/tool/RunMetricTools.java`
- `src/main/java/com/djh/researchops/tool/RunLogTools.java`
- `src/main/java/com/djh/researchops/tool/RunArtifactTools.java`
- `src/main/java/com/djh/researchops/vo/TaskRunsToolResult.java`
- `src/main/java/com/djh/researchops/vo/RunMetricsToolResult.java`
- `src/main/java/com/djh/researchops/vo/RunLogsToolResult.java`
- `src/main/java/com/djh/researchops/vo/RunArtifactsToolResult.java`
- `src/test/java/com/djh/researchops/AiChatServiceTests.java`
- `src/test/java/com/djh/researchops/AiProfileTests.java`
- `src/test/java/com/djh/researchops/AiToolChainingTests.java`
- `src/test/java/com/djh/researchops/TaskRunToolsTests.java`
- `src/test/java/com/djh/researchops/RunMetricToolsTests.java`
- `src/test/java/com/djh/researchops/RunLogToolsTests.java`
- `src/test/java/com/djh/researchops/RunArtifactToolsTests.java`
- `docs/business-codes.md`
