param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$ProjectCode = 'P-1',
    [string]$TaskCode = 'T-1',
    [string]$RunCode = 'R-1',
    [switch]$RunToolRegression,
    [switch]$RunFailureChecks
)

# Manual acceptance only: normal ask / Tools may call REAL DeepSeek using the backend's configured key.
# This script never imports documents, writes Qdrant, changes services, or prints credentials.
$ErrorActionPreference = 'Stop'

function Invoke-JsonUtf8 {
    param([string]$Uri, [string]$Method = 'GET', [object]$Body = $null)
    $requestArgs = @{ Uri = $Uri; Method = $Method; UseBasicParsing = $true; TimeoutSec = 125; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $requestArgs.ContentType = 'application/json; charset=utf-8'
        $requestArgs.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress))
    }
    try {
        $http = Invoke-WebRequest @requestArgs
        $text = [Text.Encoding]::UTF8.GetString($http.RawContentStream.ToArray())
        return ($text | ConvertFrom-Json)
    } catch {
        $failure = $_
        $response = $failure.Exception.Response
        if ($response -is [Net.HttpWebResponse]) {
            try {
                $reader = [System.IO.StreamReader]::new($response.GetResponseStream(), [Text.Encoding]::UTF8)
                try { $errorText = $reader.ReadToEnd() } finally { $reader.Dispose() }
                if (-not [string]::IsNullOrWhiteSpace($errorText)) { return ($errorText | ConvertFrom-Json) }
            } catch { }
        }
        # PowerShell 7 supplies the response body in ErrorDetails; UTF-8 is already decoded there.
        if ($failure.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($failure.ErrorDetails.Message)) {
            try { return ($failure.ErrorDetails.Message | ConvertFrom-Json) } catch { }
        }
        throw $failure
    }
}

Write-Host '正常 RAG 提问（可能调用真实 DeepSeek）'
$normal = Invoke-JsonUtf8 "$BaseUrl/dev/rag/ask" 'POST' @{ question = '3DGS视角覆盖不足为什么会导致局部重建缺失？'; topK = 3 }
$normal | ConvertTo-Json -Depth 10
if ($normal.code -ne 200) { throw "正常请求未成功，API code=$($normal.code)" }
if (-not $normal.data.grounded -or $normal.data.insufficientEvidence) { throw '模型回答未通过证据/引用校验，请检查 evidenceStatus；这不等于检索服务故障。' }
if (@($normal.data.sources).Count -eq 0) { throw 'grounded=true 时不能缺少实际引用来源' }

Write-Host '来源字段和实际引用检查'
$normal.data.sources | Format-Table citationId, title, source, chunkIndex, score, isDemo -Wrap
$normal.data.retrievedSources | Format-Table citationId, title, source, score, usedInContext -Wrap
foreach ($citation in $normal.data.sources) {
    if ($citation.source -match '(?i)^(?:file:|[a-z]:[\\/]|[/\\])') { throw '来源包含绝对路径' }
    if (-not $citation.isDemo -or -not $citation.usedInContext) { throw '来源未保留 DEMO 或 Context 标识' }
    if (-not $normal.data.answer.Contains("[$($citation.citationId)]")) { throw 'sources 中有回答未引用的编号' }
    $target = @($normal.data.retrievedSources | Where-Object { $_.citationId -eq $citation.citationId -and $_.usedInContext })
    if ($target.Count -ne 1 -or $target[0].source -ne $citation.source -or $target[0].title -ne $citation.title -or $target[0].chunkIndex -ne $citation.chunkIndex) {
        throw '引用来源与本次 Context 映射不一致'
    }
}

Write-Host '与 DEMO 知识库无关的问题'
$unrelated = Invoke-JsonUtf8 "$BaseUrl/dev/rag/ask" 'POST' @{ question = '怎样烤巧克力蛋糕？'; topK = 3 }
$unrelated | ConvertTo-Json -Depth 10
if ($unrelated.code -ne 200 -or $unrelated.data.grounded -or -not $unrelated.data.insufficientEvidence -or @($unrelated.data.sources).Count -ne 0) {
    throw '无关问题没有得到明确知识不足结果，请检查阈值、检索分数与模型输出'
}

if ($RunToolRegression) {
    Write-Host '原有五个 Tools 回归（调用真实 DeepSeek；请使用已存在的业务编号）'
    $questions = @(
        "$ProjectCode 有哪些实验任务？",
        "$TaskCode 有哪些运行？",
        "$RunCode 的 PSNR 和 SSIM 指标是多少？",
        "$RunCode 有哪些 ERROR 日志？",
        "$RunCode 有哪些实验产物？"
    )
    foreach ($question in $questions) {
        Write-Host $question
        $reply = Invoke-JsonUtf8 "$BaseUrl/api/ai/chat" 'POST' @{ message = $question }
        $reply | ConvertTo-Json -Depth 10
        if ($reply.code -ne 200) { throw "原有 AI Chat 请求失败，API code=$($reply.code)" }
    }
    Write-Host '请在服务端 ToolInvocationLog 核对对应五个 query* 调用，并对照实际业务数据；HTTP 200 本身不证明模型使用了正确 Tool。'
}

if ($RunFailureChecks) {
    Write-Host '可控故障检查：需先按 docs/rag-ask-v1.md 启动 18081/18082/18083 三个临时实例'
    foreach ($port in @(18081, 18082, 18083)) {
        $reply = Invoke-JsonUtf8 "http://127.0.0.1:$port/dev/rag/ask" 'POST' @{ question = '3DGS视角覆盖不足为什么会导致局部重建缺失？'; topK = 3 }
        Write-Host "故障实例 $port"
        $reply | ConvertTo-Json -Depth 10
        if ($reply.code -ne 503 -or $null -ne $reply.data) { throw "故障实例 $port 应返回503，不能返回知识不足200" }
    }
}

Write-Host '本轮人工检查已执行；请结合模型回答内容、引用原文与后台日志完成语义验收。'
