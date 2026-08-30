# lesson-web-poc：Spring Boot Java 17 后端复刻改造计划

> 历史规划文档，保留用于追溯实施决策；其中的阶段状态、目录示例和“后续接入”描述不是当前项目规范。Python 后端已从当前仓库删除。当前实现与运行方式以仓库根目录 `README.md`、`AGENTS.md` 及 `backend-java/README.md` 为准。

> 目标：在现有项目中并行新增一套 **Java 17 + Spring Boot** 后端，复刻当前 Python FastAPI 后端能力，并保持现有前端接口契约尽量不变。  
> 本阶段重点：**API 1:1、MinerU 解析链路 1:1、DOCX 导出效果 1:1**。  
> 非目标：数据库重构、权限系统、微服务拆分、生产级任务队列、前端重写。

---

# 1. 改造目标

当前项目保留现有 Python 后端作为基准实现，同时新增：

```text
lesson-web-poc/
├── frontend/
├── backend/          # 当前 Python FastAPI，保留
└── backend-java/     # 新增 Java 17 + Spring Boot
```

Java 后端最终需要达到：

1. 前端仅修改后端地址即可切换 Python / Java。
2. 核心 API 请求与响应结构保持一致。
3. MinerU 文件解析流程保持一致。
4. LessonDocument JSON 结构保持一致。
5. HTML → DOCX 下载功能达到与 Python 版本相同的主要效果。
6. Java 版本通过契约测试后，再决定是否正式替换 Python 后端。

---

# 2. 技术选型

## 2.1 Java 与 Spring Boot 版本

本项目必须和现有老项目对齐：

```text
JDK 17
```

建议：

```text
Java 17
Spring Boot 3.3.x 或其他明确支持 Java 17 的 Spring Boot 3.x 稳定版本
Maven
Jackson
Jsoup
docx4j
Spring Web
Spring Validation
```

### Java 17 硬约束

Codex 必须确保：

```text
mvn test
mvn package
mvn spring-boot:run
```

全部可以在 **JDK 17** 下完成。

禁止使用：

```text
Java 21 API
Java 21 语法
Virtual Threads
SequencedCollection
String Templates
其他仅支持 Java 18+ / 21+ 的运行时能力
```

如果某个第三方库最新版要求 Java 21：

```text
优先选择兼容 Java 17 的稳定版本
```

而不是升级 JDK。

Java 17 可以正常使用：

```text
record
sealed class
switch expression
text block
```

---

## 2.2 HTTP Client

MinerU HTTP 调用建议优先使用：

```text
Spring RestClient
```

第一阶段不需要为了异步 HTTP 引入完整 Reactor 复杂度。

如果后续 MinerU 并发量明显增加，再评估：

```text
WebClient
```

---

## 2.3 DOCX

DOCX 导出建议使用：

```text
Jsoup + docx4j
```

不建议把 Apache POI 作为核心 DOCX 生成方案。

当前 Python DOCX 已涉及：

- 标题
- 段落
- 粗体 / 斜体 / 下划线
- 上标 / 下标
- 列表
- 表格
- rowspan / colspan
- 图片
- data URL 图片
- Word 可编辑公式 OMML
- 字体设置
- 中文字体嵌入
- OOXML 级别修改

docx4j 更适合继续处理 OpenXML / OOXML。

---

# 3. 核心原则

## 3.1 不修改 Python 业务逻辑

Python 后端继续作为当前基准实现。

除非发现明确 Bug，否则：

```text
backend/
```

不做重构。

---

## 3.2 不修改前端接口协议

Java 后端优先兼容现有前端，而不是让前端适配 Java。

必须尽量保持：

```text
URL
HTTP Method
Request Body
Response JSON
HTTP Status
Content-Type
下载文件名
```

与 Python 一致。

---

## 3.3 先复刻，再优化

第一阶段禁止主动增加：

```text
Redis
RabbitMQ
Kafka
数据库任务表
微服务拆分
DDD 重构
通用文档平台设计
```

先完成：

```text
Python 功能
    ↓
Java 等价实现
```

---

## 3.4 Python 是 Golden Reference

相同输入分别调用：

```text
input
 ├── Python backend
 └── Java backend
```

比较：

```text
HTTP Status
JSON Schema
关键业务字段
LessonDocument
DOCX XML
DOCX 在 Word/WPS 中的展示
```

不要求：

```text
Python DOCX bytes == Java DOCX bytes
```

只要求业务效果一致。

---

# 4. 第一阶段：建立 backend-java

## Task 1：创建 Spring Boot 项目

新增：

```text
backend-java/
```

建议结构：

```text
backend-java/
├── pom.xml
├── src/main/java/
│   └── com/.../lesson/
│       ├── LessonApplication.java
│       ├── controller/
│       ├── service/
│       ├── model/
│       ├── adapter/
│       ├── client/
│       ├── storage/
│       ├── docx/
│       ├── config/
│       └── exception/
│
├── src/main/resources/
│   ├── application.yml
│   └── fonts/
│
└── src/test/java/
```

### Maven Compiler

必须显式配置 Java 17。

例如：

```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.release>17</maven.compiler.release>
</properties>
```

### 验收

```bash
cd backend-java
mvn test
mvn package
mvn spring-boot:run
```

均在 JDK 17 下正常运行。

并能够访问：

```text
GET /api/health
```

---

# 5. 第二阶段：复制 API Contract

先建立 Controller 与 DTO，不立即实现完整业务。

目标接口：

```text
POST /api/documents/parse

GET /api/documents/{jobId}

POST /api/formulas/validate

POST /api/documents/export-docx

GET /api/assets/{jobId}/{filename}

GET /api/health
```

---

## Task 2：建立 DTO / Model

根据当前 Python 源码建立 Java 对应模型，例如：

```text
ParseJob
LessonDocument
DocumentBlock
TableBlock
ImageBlock
FormulaBlock
FormulaValidationRequest
FormulaValidationResponse
DocxExportRequest
```

具体名称必须以当前 Python 源码为准。

可以优先考虑 Java 17 的：

```java
record
```

如果 Jackson、多态或可变结构不适合，再使用普通 POJO。

---

## Task 3：建立 Contract Test

创建：

```text
src/test/java/.../contract/
```

测试：

- API URL
- HTTP Method
- Request Body
- Response JSON 字段
- 枚举值
- null 行为
- HTTP Status
- Content-Type

### 阶段验收

即使暂时返回 Mock 数据，也要保证：

```text
现有前端可以切换到 Java backend
```

且不会因为接口结构差异报错。

---

# 6. 第三阶段：复刻任务管理

如果当前 Python 使用内存任务状态，Java 第一版保持一致。

实现：

```java
ConcurrentHashMap<String, ParseJob>
```

创建：

```text
ParseJobService
```

职责：

```text
create
update
get
success
fail
```

异步任务建议使用 Java 17 兼容方案：

```text
Spring TaskExecutor
CompletableFuture
```

禁止为了异步任务使用 Java 21 Virtual Threads。

---

# 7. 第四阶段：复刻 MinerU Client

创建：

```text
client/MineruClient.java
```

负责：

```text
获取上传地址
PUT 文件
提交解析任务
轮询 batch
读取任务状态
获取 full_zip_url
下载 ZIP
```

配置放入：

```yaml
mineru:
  base-url:
  token:
  poll-interval:
  timeout:
```

禁止硬编码 token 和地址。

---

## Task 4：ZIP 处理

实现：

```text
MineruResultExtractor
```

负责：

```text
ZIP
 ↓
解压
 ↓
content_list.json
model.json
images / assets
```

使用 Java 17 标准能力：

```text
java.nio.file
ZipInputStream
Jackson
```

### 验收

使用同一份 PDF 分别调用：

```text
Python MinerU
Java MinerU
```

确认：

- 上传成功
- 任务轮询成功
- ZIP 下载成功
- JSON 正常读取
- 图片正常保存

---

# 8. 第五阶段：MinerU → LessonDocument

这是 Java 迁移核心模块之一。

创建：

```text
adapter/MineruLessonDocumentAdapter.java
```

不要重新设计 LessonDocument。

直接按照当前 Python 逻辑逐条迁移，包括：

```text
标题识别
段落识别
图片
表格
公式
bbox
页面信息
对齐方式
HTML
特殊表格修复
```

Python 中如果已有特殊规则：

```text
if xxx:
    修复
```

Java 第一阶段直接等价翻译，不做过度抽象。

---

## Golden Test

准备固定样本：

```text
fixtures/
├── sample01.pdf
├── sample02.pdf
└── sample03.pdf
```

分别生成：

```text
python-result.json
java-result.json
```

重点比较：

```text
document blocks 数量
block 类型
文本
图片引用
HTML
公式
表格结构
```

允许以下字段不同：

```text
jobId
时间字段
临时目录
系统生成路径
```

---

# 9. 第六阶段：资源文件 API

创建：

```text
storage/AssetStorageService
```

第一版继续使用本地目录，例如：

```text
data/jobs/{jobId}/
```

实现：

```text
GET /api/assets/{jobId}/{filename}
```

必须测试：

```text
中文文件名
URL 编码
Content-Type
404
路径穿越
```

必须阻止：

```text
../
```

访问任务目录之外的文件。

---

# 10. 第七阶段：DOCX Export V1

这是整个 Java 复刻中优先级最高的验证项之一。

创建：

```text
docx/HtmlToDocxService.java
```

整体流程：

```text
HTML
 ↓
Jsoup
 ↓
业务 HTML AST / Node
 ↓
docx4j
 ↓
DOCX
```

---

## V1 支持范围

按照当前 Python 功能逐项实现。

### 文本

```text
p
br
strong / b
em / i
u
sup
sub
```

### 标题

```text
h1
h2
h3
...
```

并支持当前项目中的自定义语义，例如：

```text
data-docx-title
lesson-heading
lesson-heading-center
```

具体以源码为准。

### 列表

```text
ul
ol
li
```

### 表格

```text
table
tr
td
th
```

支持：

```text
rowspan
colspan
```

### 图片

支持：

```text
data:image/...;base64
```

以及当前项目已有的图片来源。

---

## V1 暂时不扩展

如果当前 Python 没有使用，不主动开发：

```text
SVG
复杂 CSS
flex
grid
浮动布局
分页模板
页眉页脚编辑器
通用 HTML/CSS 渲染器
```

---

# 11. 第八阶段：Word 可编辑公式

必须保持：

```text
LaTeX
 ↓
MathML
 ↓
OMML
 ↓
Word Equation
```

不能退化成：

```text
LaTeX
 ↓
PNG
```

导出的公式必须可以在 Word 中继续编辑。

---

## 推荐结构

独立创建：

```text
docx/formula/
├── LatexParser
├── MathmlConverter
└── OmmlConverter
```

不要把全部公式处理写进 `HtmlToDocxService`。

---

## 验收

解压 `.docx` 后检查：

```text
word/document.xml
```

必须存在：

```xml
<m:oMath>
```

重点验证：

```text
上标
下标
分数
根号
≤
≥
角度
sin
cos
```

---

# 12. 第九阶段：中文字体

第一步先完成：

```text
Word Run 字体设置
```

确保：

```text
eastAsia
ascii
hAnsi
```

设置一致。

第二步再做：

```text
Embedded Fonts
```

---

## Font V1

先保证：

```text
Noto Sans SC
```

等目标字体正确写入文档样式与 run。

---

## Font V2

再实现：

```text
font subset
font obfuscation
word/fonts/*.odttf
fontTable.xml
fontTable.xml.rels
[Content_Types].xml
settings.xml
```

不要因为字体子集阻塞 DOCX 主流程。

---

# 13. 第十阶段：DOCX Golden Tests

把当前 Python：

```text
test_docx_export_api.py
```

测试意图迁移到 Java。

不要比较 DOCX 二进制。

应该解压后比较关键 OOXML：

```text
word/document.xml
word/styles.xml
word/fontTable.xml
word/numbering.xml
```

---

## 必须测试

### 文本

```text
普通文本
粗体
斜体
下划线
上下标
```

### 表格

```text
rowspan
colspan
文本样式
```

### 图片

```text
media/*
relationships
```

### 公式

```text
m:oMath
```

### 字体

```text
fontTable
embedded font
```

### 文档可用性

人工抽样：

```text
Microsoft Word
WPS
LibreOffice
```

重点还是 Word / WPS。

---

# 14. 第十一阶段：公式校验迁移

这是唯一可以不在第一轮要求 100% 等价的模块。

当前 Python 使用：

```text
SymPy
```

Java 可以评估：

```text
Symja
```

创建：

```text
FormulaValidationService
```

---

## 验证范围

准备与当前 Python 相同的测试：

```text
合法 LaTeX
非法 LaTeX
符号化简
公式等价
函数
分数
指数
根式
三角函数
```

执行：

```text
Python SymPy
vs
Java Symja
```

---

## 决策规则

如果：

```text
≥ 95% 当前业务测试行为一致
```

则正式迁移 Java。

如果差异较大：

```text
Java backend
    ↓
Python Formula Validator
```

暂时保留 Python 公式服务。

不要因为公式验证阻塞 Java 主后端迁移。

---

# 15. 推荐最终目录

```text
backend-java/
└── src/main/java/.../
    ├── controller/
    │   ├── DocumentController
    │   ├── FormulaController
    │   ├── AssetController
    │   └── HealthController
    │
    ├── service/
    │   ├── DocumentParseService
    │   ├── ParseJobService
    │   └── FormulaValidationService
    │
    ├── client/
    │   └── MineruClient
    │
    ├── adapter/
    │   └── MineruLessonDocumentAdapter
    │
    ├── storage/
    │   └── AssetStorageService
    │
    ├── docx/
    │   ├── HtmlToDocxService
    │   ├── DocxTableRenderer
    │   ├── DocxImageRenderer
    │   ├── DocxFormulaRenderer
    │   └── DocxFontService
    │
    ├── model/
    ├── config/
    └── exception/
```

---

# 16. Codex 执行顺序

严格按以下顺序执行：

```text
STEP 1
创建 backend-java Java 17 + Spring Boot 骨架

STEP 2
复制 API Contract + DTO

STEP 3
完成 Health + Mock Controller

STEP 4
完成 ParseJob 内存状态

STEP 5
完成 MinerU Client

STEP 6
完成 ZIP / assets

STEP 7
完成 MinerU → LessonDocument

STEP 8
完成前端切 Java backend 联调

STEP 9
实现 DOCX 基础文本

STEP 10
实现表格

STEP 11
实现图片

STEP 12
实现 OMML 公式

STEP 13
实现字体

STEP 14
迁移 Python DOCX 测试

STEP 15
进行 Python / Java Golden Test

STEP 16
最后处理 SymPy → Symja
```

不要跨阶段一次性重写全部后端。

---

# 17. 每个阶段 Codex 必须执行的工作流

每个 Task 必须：

```text
1. 阅读当前 Python 对应源码
2. 明确现有行为
3. 明确 Java 17 兼容性
4. 写 Java 测试
5. 实现最小代码
6. 执行 mvn test
7. 修复失败
8. 输出本阶段变更摘要
```

禁止：

```text
未阅读当前 Python 实现就自行重新设计
```

---

# 18. 第一轮 Codex 任务

第一轮只执行：

```text
backend-java 初始化
+
Java 17 Maven 配置
+
API Contract
+
DTO
+
Health
+
Mock API
+
Contract Test
```

不要立即实现：

```text
MinerU
DOCX
Symja
```

---

# 19. 第一轮验收标准

必须使用：

```text
JDK 17
```

执行：

```bash
cd backend-java
java -version
mvn test
mvn package
```

全部通过。

并满足：

```text
GET /api/health
```

正常。

所有目标 Controller 已建立。

所有请求 / 响应 DTO 已建立。

Java API 与 Python 当前接口契约建立完整对应关系。

---

# 20. 项目最终验收标准

## Java 17

- [ ] 使用 JDK 17 构建
- [ ] 不依赖 Java 21
- [ ] Maven Compiler release=17
- [ ] 所有第三方依赖兼容 Java 17

## API

- [ ] 前端可以直接切换 Java
- [ ] API 地址一致
- [ ] JSON 结构一致
- [ ] HTTP 状态兼容
- [ ] 错误响应兼容

## MinerU

- [ ] 上传成功
- [ ] 轮询成功
- [ ] ZIP 下载成功
- [ ] JSON 提取成功
- [ ] Asset 提取成功

## LessonDocument

- [ ] Python / Java 关键业务字段一致

## DOCX

- [ ] Microsoft Word 可以正常打开
- [ ] WPS 可以正常打开
- [ ] 标题正常
- [ ] 段落正常
- [ ] 列表正常
- [ ] 表格正常
- [ ] rowspan / colspan 正常
- [ ] 图片正常
- [ ] 公式为可编辑 OMML
- [ ] 中文字体正常

## 工程

- [ ] JDK 17 下 `mvn test` 全部通过
- [ ] JDK 17 下 `mvn package` 成功
- [ ] Java 后端可以独立启动
- [ ] Python 后端仍可以独立启动
- [ ] 前端可以在 Python / Java 两套后端之间切换

---

# 21. 当前阶段明确不做

为了保持任务聚焦，本次 Codex 改造禁止主动增加：

```text
数据库
Redis
MQ
微服务
Kubernetes
复杂缓存
权限系统
OAuth
统一网关
复杂日志平台
监控平台
前端重构
LessonDocument V2
通用 HTML 转 Word 引擎
Java 21 升级
```

---

# 22. 给 Codex 的最终任务描述

```text
请基于当前 lesson-web-poc 仓库进行开发。

目标是在不修改现有 Python 后端主要行为、不重写前端协议的前提下，
新增 backend-java Spring Boot 后端，并逐步实现与当前 Python backend 的功能等价。

【硬性技术约束】

Java 运行环境统一使用 JDK 17。

所有代码、依赖、Maven 配置、测试和构建必须兼容 Java 17。
不得使用 Java 21 API、Java 21 语法、Virtual Threads 或其他 Java 18+ 才可用的能力。
如果第三方库最新版要求 Java 21，请选择兼容 Java 17 的稳定版本，而不是升级 JDK。

【核心优先级】

1. API Contract 一致
2. MinerU 解析链路一致
3. LessonDocument 数据一致
4. DOCX 导出效果一致
5. 最后处理公式校验 SymPy → Java 的兼容问题

Python backend 必须作为 Golden Reference。

不要一次性重写全部功能。
不要主动加入数据库、Redis、MQ、微服务等架构。
每一步必须先阅读对应 Python 源码和现有测试，再设计 Java 等价实现。

【第一轮只完成】

- backend-java Spring Boot 初始化
- JDK 17 / Maven Java 17 配置
- 包结构
- API Controller
- DTO / Model
- Health API
- Mock API
- Contract Test

第一轮结束后执行：

java -version
mvn test
mvn package

必须确认运行环境和构建目标都是 Java 17。

完成第一轮后停止，并输出：

1. 修改文件
2. API 对照关系
3. Java 17 兼容性说明
4. 测试结果
5. 下一阶段建议

在我确认之前，不进入 MinerU 和 DOCX 实现阶段。
```
