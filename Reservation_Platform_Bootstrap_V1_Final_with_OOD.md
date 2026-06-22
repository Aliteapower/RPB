# Reservation Platform Bootstrap V1

## Codex Governance Startup Task

## 0. 本轮执行契约

文件
##在地新文夹D:\RPB

##提交github地址：https://github.com/Aliteapower/RPB  现面也是空文件

本轮是治理启动轮，不是开发轮。

本轮目标是先治理、后设计、再开发；先业务、后数据、再代码；先规则、后实现。

本轮不得直接进入数据库设计、API 设计、UI 设计或业务开发。

---

## 1. 项目目标

建设一套面向以下业态的多租户预约排队平台：

* 餐厅
* 咖啡馆
* 茶楼
* 火锅店
* 连锁餐饮
* 其他需要预约、排队、排桌、叫号、入座、清台、翻台的门店场景

系统定位：

```text
Reservation
+
Queue
+
Table Management
+
Seating Flow
```

本系统不是简单订位系统。

核心业务链路：

```text
预约
↓
取号
↓
排桌
↓
叫号
↓
签到
↓
入座
↓
清台
↓
翻台
```

---

## 2. 第一原则

本项目从 0 开始。

禁止直接建表。
禁止直接写 API。
禁止直接开发页面。
禁止直接做技术实现。

项目整体顺序必须是：

```text
Business Governance
↓
Data Governance
↓
Architecture Review
↓
OOD & Reuse Governance
↓
Technical Baseline
↓
Reservation Skill Governance
↓
Database Design
↓
API Design
↓
UI Design
↓
Development
```

但本轮只执行治理、架构、OOD 复用、技术基线和 Skill 治理任务，不进入 Database Design、API Design、UI Design、Development。

---

## 3. 本轮阶段终点

本轮只执行：

1. Business Governance
2. Data Governance
3. Architecture Review
4. OOD & Reuse Governance
5. Technical Baseline
6. Reservation Skill Governance

本轮不得进入：

* Database Design
* API Design
* UI Design
* Development

可以在治理文档中描述未来数据库、API、UI 的原则和边界，但不得创建具体表结构、Migration、接口代码、页面代码或业务实现。

---

## 4. 本轮允许修改的文件

仅允许创建或修改以下 Markdown 文档：

```text
docs/governance/BUSINESS_GLOSSARY.md
docs/governance/BUSINESS_RULES.md
docs/governance/DATA_STANDARD.md
docs/governance/DATA_CHECKLIST.md
docs/architecture/ARCHITECTURE.md
docs/skills/reservation-system/SKILL_OVERVIEW.md
docs/skills/reservation-system/SKILL.md
```

除上述 Markdown 文档外，不得修改任何其他文件。

---

## 5. 绝对禁止

本轮不得创建或修改：

* 数据库表
* ERD 对应的可执行 Schema
* SQL 文件
* Flyway Migration
* Repository
* Entity
* Model
* Controller
* Service
* API
* Vue 页面
* Vue 组件
* 配置文件
* 依赖文件
* Docker 文件
* CI/CD 文件
* 部署文件
* 测试代码
* 示例业务代码
* 源数据

不得执行：

* 自动修复
* 自动重构
* 顺手整理代码
* 顺手格式化非目标文件
* 顺手补测试
* 顺手改配置
* 顺手清理目录
* 顺手创建脚手架

如果修改了上述禁止内容，本轮直接判定为失败。

---

## 6. 执行方式

第一步，只读扫描现有项目结构和已有文档。

只读扫描范围包括：

* 项目目录结构
* 已有 docs 目录
* 已有 skills 目录
* 已有 architecture 文档
* 已有 governance 文档
* 已有 README 或 AGENTS 类协作规则文档

只读扫描期间不得修改任何文件。

第二步，根据本任务生成治理文档。

第三步，完成前检查实际修改文件范围。

第四步，输出最终验收报告。

---

## 7. 本轮交互规则

本任务中已经明确写出的业务规则，均视为 Product Owner 当前确认口径。

Codex 不得重复询问已经有明确答案的问题。

Codex 只需要针对以下情况追加问题：

1. 当前项目已有文档与本任务规则冲突
2. 某个规则影响后续数据库或 API 设计，但本任务没有给出明确答案
3. 某个业务对象的生命周期无法根据本任务推导
4. 多租户、国际化、状态机、幂等或审计边界存在缺口

除此之外，不得以“需要确认”为理由阻塞治理文档生成。

规则状态必须区分为：

```text
Confirmed：用户明确确认
Defaulted：暂时采用任务默认值
Open：仍待确认
Open Conflict：现有文档与本任务规则冲突
```

本任务中写明为 Product Owner 已确认的规则，必须标记为 Confirmed，不得标记为 Defaulted、Open 或 Assumed。

---

## 8. 第一阶段：Business Governance

### 8.1 目标

统一业务语言，建立业务边界。

必须生成：

```text
docs/governance/BUSINESS_GLOSSARY.md
docs/governance/BUSINESS_RULES.md
```

---

### 8.2 必须识别的核心业务对象

必须覆盖以下对象：

```text
Platform
Tenant
Store
Area
Table
TableGroup
Customer
Reservation
QueueTicket
WalkIn
CheckIn
Seating
Cleaning
Turnover
```

每个业务对象不能只给名词解释，必须描述：

* 定义
* 职责
* 所属层级
* 上游对象
* 下游对象
* 生命周期
* 边界
* 不负责的内容
* 多租户归属
* 关键业务约束

---

### 8.3 核心对象初始定义

#### Platform

平台级系统主体。

负责：

* 租户管理
* 平台配置
* 平台角色
* 平台级审计
* 平台级开放能力
* 平台级行业模板

不负责具体门店的预约、排队和入座操作。

---

#### Tenant

租户，通常对应一个品牌、公司或经营主体。

负责：

* 品牌级配置
* 多门店管理
* 租户级用户
* 租户级权限
* 租户级业务规则
* 跨门店客户资料

Tenant 是数据隔离的核心边界。

---

#### Store

门店，属于 Tenant。

负责：

* 预约运营
* 排队运营
* 桌位运营
* 区域管理
* 营业时间
* 门店时区
* 门店语言和日期格式
* 门店货币配置

大部分运营业务数据必须落到 Store。

---

#### Area

门店区域。

例如：

* 大厅
* 包间
* 户外
* 吧台
* 二楼
* VIP 区

Area 属于 Store。

Area 用于桌位分区、排桌优先级和运营管理。

---

#### Table

桌位。

属于 Store 和 Area。

Table 必须支持：

* 区域
* 容量
* 状态
* 预约
* 排队
* WalkIn
* 入座
* 清台
* 固定组合桌
* 临时组合桌

Table 是门店现场资源，不是单纯静态配置。

---

#### TableGroup

组合桌。

分为：

```text
Fixed TableGroup
Temporary TableGroup
```

Fixed TableGroup 是长期配置。

Temporary TableGroup 是单次服务资源。

组合桌必须防止：

* 循环引用
* 重复占用
* 同一桌位同时出现在多个有效组合中
* 临时组合未释放导致资源占用错误

---

#### Customer

客户。

Customer 唯一性范围默认是 Tenant。

同一 Tenant 下，不同 Store 可以共享 Customer。

不同 Tenant 之间不得共享 Customer 身份，避免隐私和数据边界问题。

手机号不是必填字段。

系统必须支持：

* anonymous customer
* walk-in guest
* 老板朋友
* 临时客户
* 无手机号客户

---

#### Reservation

预约。

Reservation 表示客户提前预约某个门店、某个日期、某个时间段、某个人数容量资源。

V1 中 Reservation 默认锁定：

```text
Store + 日期 + 时间段 + 人数容量
```

Reservation 不默认锁死具体桌位。

可以支持预分配桌位，但最终排桌可以在客户到店前或到店时完成。

---

#### QueueTicket

排队票 / 取号记录。

QueueTicket 表示客户到店后等待资源释放。

QueueTicket 和 Reservation 是独立业务对象。

Reservation 不必然生成 QueueTicket。

只有当预约客户到店后无可用桌位，或 WalkIn 无可用桌位时，才生成 QueueTicket。

---

#### WalkIn

现场到店客户。

WalkIn 可以直接入座，也可以进入 Queue。

如果有空桌，WalkIn 可以不取号直接入座。

如果无空桌，WalkIn 才需要生成 QueueTicket。

---

#### CheckIn

签到 / 到店确认。

V1 中 CheckIn 是业务事件，不是主业务实体。

CheckIn 会触发业务状态变化。

例如：

```text
Reservation: confirmed → arrived
```

未来如果需要详细到店记录，可以扩展为独立实体或事件日志。

---

#### Seating

入座。

Seating 是业务事件，同时也是桌位占用记录。

Seating 表示客户已经被安排到具体桌位或桌位组合。

Seating 会影响：

* Reservation 状态
* QueueTicket 状态
* Table 状态
* 桌位锁
* 翻台统计
* 审计日志

---

#### Cleaning

清台。

Cleaning 是桌位状态流程。

Cleaning 表示客人离桌后，桌位从 occupied 到 available 之间的清理过程。

Cleaning 不是营销动作，也不是支付流程。

---

#### Turnover

翻台。

Turnover 是业务结果 / 运营指标 / 可沉淀事件结果。

Turnover 来源于：

```text
Seating
+
Completed
+
Cleaning
```

Turnover 用于衡量桌位周转效率。

---

## 9. Product Owner 已确认的关键业务边界

以下 11 项业务边界已由 Product Owner 明确确认。

Codex 不需要重复提问，除非在只读扫描中发现现有文档与以下规则存在明确冲突。

这些规则在本轮治理文档中必须标记为：

```text
Confirmed
```

不得标记为：

```text
Defaulted
Open
Assumed
```

如发现冲突，必须在最终报告中列入：

```text
Open Conflict
```

不得自行覆盖、修复或发明新规则。

---

### 9.1 Reservation 是否必然生成 QueueTicket？

结论：

```text
Reservation 不必然生成 QueueTicket。
```

Reservation 和 QueueTicket 是两个独立业务对象。

默认规则：

```text
Reservation = 提前预约资源
QueueTicket = 到店后等待资源释放
```

预约成功后，只生成 Reservation。

只有当预约客户到店后，当前无可用桌位，才可生成 QueueTicket。

---

### 9.2 已预约客户到店后是直接 CheckIn，还是进入 Queue？

结论：

```text
预约客户到店后必须先 CheckIn，不是直接进入 Queue。
```

流程：

```text
Reservation confirmed
↓
客户到店
↓
CheckIn
↓
系统判断桌位
↓
有桌：Seating
无桌：进入 Queue
```

---

### 9.3 WalkIn 是否可以不取号直接入座？

结论：

```text
WalkIn 可以不取号直接入座。
```

WalkIn 是现场到店客户。

如果有空桌，可以直接入座，不强制取号。

流程：

```text
WalkIn
↓
有空桌
↓
Seating
```

如果无空桌，才生成 QueueTicket。

---

### 9.4 CheckIn 是业务实体、业务事件还是状态变化？

结论：

```text
V1 中 CheckIn 是业务事件，不是主业务实体。
```

CheckIn 定义为业务事件，同时会触发状态变化。

V1 不把 CheckIn 做成复杂主实体。

默认：

```text
CheckIn = 客户到店确认事件
```

它会触发：

```text
Reservation: confirmed → arrived
```

未来如果要做详细到店记录，可以再扩展为独立实体或事件日志。

---

### 9.5 Seating、Cleaning、Turnover 是实体、流程还是事件记录？

结论：

```text
Seating 需要记录。
Cleaning 是状态流程。
Turnover 是由 Seating + Completed + Cleaning 计算或沉淀出来的业务结果。
```

具体口径：

| 对象       | 定义                                   |
| -------- | ------------------------------------ |
| Seating  | 客户正式入座事件，产生桌位占用                      |
| Cleaning | 客人离桌后，桌位从 occupied 到 available 的中间流程 |
| Turnover | 一轮从入座到清台完成的翻台记录 / 指标                 |

默认定义：

```text
Seating = 业务事件 + 占桌记录
Cleaning = 桌位状态流程
Turnover = 业务指标 / 事件结果
```

---

### 9.6 Reservation 锁定的是具体桌位、桌位组合、容量还是时间段资源？

结论：

```text
Reservation 默认锁定时间段容量资源，可选预分配桌位。
```

V1 默认锁定时间段容量资源，不强制提前锁死具体桌位。

原因：

餐厅现场变化多，太早锁具体桌位会导致运营僵硬。

推荐规则：

```text
Reservation 锁定的是：
Store + 日期 + 时间段 + 人数容量
```

可以预分配桌位，但不是强制锁死。

到店前或到店时再最终排桌。

---

### 9.7 Customer 的唯一性范围是 Platform、Tenant 还是 Store？

结论：

```text
Customer 唯一性范围 = Tenant。
```

同一个客户在同一个品牌 / 租户下应尽量合并。

不同租户之间不共享客户身份，避免隐私和数据边界问题。

补充：

```text
同一 tenant 下，不同 store 可共享 Customer。
```

---

### 9.8 无手机号客户如何识别和查找？

结论：

```text
手机号不是必填字段。
无手机号客户使用临时客户编号 + 场景信息查找。
```

允许无手机号客户。

无手机号客户可以通过以下方式识别：

* 姓名
* 昵称
* 到店时间
* 人数
* 备注
* 桌号
* 临时编号
* QueueTicket number
* Reservation code

系统需要支持：

* anonymous customer
* walk-in guest
* 老板朋友
* 临时客户

---

### 9.9 固定组合桌与临时组合桌的生命周期？

结论：

```text
固定组合桌是配置。
临时组合桌是单次服务资源。
```

#### 固定组合桌

长期配置，由门店维护。

例如：

```text
T1 + T2 = 可组合 8 人桌
```

生命周期：

```text
created
active
inactive
deleted
```

#### 临时组合桌

只在一次服务过程中存在。

例如：

```text
今晚临时把 A3 + A4 拼给 10 人客户
```

生命周期：

```text
created
locked
occupied
released
ended
```

---

### 9.10 过号重新加入后保留原号码还是生成新号码？

结论：

```text
过号重新加入保留原号码，但不默认插队。
```

默认保留原号码，但状态标记为 rejoined。

原因：

现场叫号体验更自然，客户也容易理解。

流程：

```text
waiting
↓
called
↓
skipped
↓
rejoined
↓
waiting / called
```

排序上不能默认插队。

推荐规则：

```text
保留原号码
重新加入当前队列
排序规则由门店配置
默认排到同组别队尾
```

---

### 9.11 tenant_id 和 store_id 在不同层级数据中的必填规则？

结论：

```text
tenant_id 根据租户边界决定。
store_id 根据是否属于具体门店运营决定。
不能机械要求所有表都有 store_id。
```

不得简单规定所有数据都必须同时包含 tenant_id 和 store_id。

应按数据层级区分：

| 数据层级       | tenant_id | store_id | 示例                                       |
| ---------- | --------: | -------: | ---------------------------------------- |
| Platform 级 |         否 |        否 | 系统角色、全局配置、行业模板                           |
| Tenant 级   |         是 |        否 | 品牌配置、客户、权限、预约规则                          |
| Store 级    |         是 |        是 | 门店、区域、桌位、营业时间                            |
| 运营业务数据     |         是 |        是 | Reservation、QueueTicket、Seating、Cleaning |
| 跨门店共享数据    |         是 |       可空 | Customer、会员档案、品牌级规则                      |
| 审计日志       |         是 |       可空 | 平台审计、租户审计、门店审计                           |

必须区分：

* Platform 级数据
* Tenant 级数据
* Store 级数据
* 跨门店共享数据
* 门店运营数据

---

## 10. 第二阶段：Data Governance

### 10.1 目标

建立项目数据标准。

必须生成：

```text
docs/governance/DATA_STANDARD.md
docs/governance/DATA_CHECKLIST.md
```

---

### 10.2 必须建立的数据治理规则

必须覆盖：

* 唯一性规则
* 完整性规则
* 引用规则
* 删除规则
* 状态机规则
* 审计规则
* 多租户归属规则
* 国际化数据规则
* 时间存储规则
* 电话号码规则
* 幂等规则
* 并发控制规则
* 软删除规则

---

### 10.3 唯一性规则

必须定义以下唯一性边界：

* Platform 级唯一
* Tenant 级唯一
* Store 级唯一
* Tenant + Store 级唯一
* 时间段内唯一
* 同一桌位资源占用唯一
* 同一客户同一时间段重复预约规则
* 同一 Queue group 中排队号唯一

不得默认所有唯一性都是全平台唯一。

---

### 10.4 完整性规则

必须定义：

* 必填字段原则
* 可空字段原则
* 临时客户字段原则
* 无手机号客户规则
* Store 运营数据归属规则
* Reservation 核心字段完整性
* QueueTicket 核心字段完整性
* Seating 核心字段完整性
* Table 状态完整性

---

### 10.5 引用规则

必须定义对象之间的引用关系：

```text
Platform → Tenant
Tenant → Store
Store → Area
Area → Table
Store → Reservation
Store → QueueTicket
Reservation → Customer
QueueTicket → Customer
Seating → Table / TableGroup
Seating → Reservation or QueueTicket or WalkIn
Cleaning → Table / TableGroup
Turnover → Seating / Cleaning
```

必须避免：

* 跨 Tenant 引用
* 非法跨 Store 占用桌位
* TableGroup 循环引用
* 临时组合桌释放后仍被新流程引用
* 已软删除资源被新业务引用

---

### 10.6 删除规则

默认采用软删除。

必须区分：

* 可删除
* 可停用
* 可取消
* 可归档
* 不可物理删除

关键业务数据不得物理删除。

以下数据必须考虑审计和软删除：

* Reservation
* QueueTicket
* Seating
* Cleaning
* Table
* TableGroup
* Customer
* Store
* Tenant

---

### 10.7 状态机规则

状态机不能只罗列状态，必须描述：

* 初始状态
* 合法转换
* 非法转换
* 转换触发者
* 转换前置条件
* 转换后的业务影响
* 是否需要审计
* 并发与幂等要求

---

### 10.8 审计规则

必须审计：

* 预约创建
* 预约确认
* 预约取消
* 预约 no_show
* 客户 CheckIn
* 取号
* 叫号
* 过号
* 重新加入队列
* 入座
* 换桌
* 拼桌
* 清台
* 桌位释放
* 手工覆盖系统推荐
* 关键配置变更
* 权限变更
* 第三方调用

审计日志必须包含：

* 操作人
* 操作角色
* tenant scope
* store scope
* 操作时间
* 操作前状态
* 操作后状态
* 操作来源
* 幂等 Key
* 关联业务对象
* 失败原因

---

### 10.9 禁止事项

Data Governance 阶段禁止：

* 自动修复
* 修改源数据
* 修改数据库
* 生成 Migration
* 生成 DDL
* 创建测试数据
* 创建 Mock 业务数据
* 创建实体代码

---

## 11. 第三阶段：Architecture Review

### 11.1 目标

确认系统边界。

必须生成：

```text
docs/architecture/ARCHITECTURE.md
```

---

### 11.2 ARCHITECTURE.md 必须输出

必须覆盖：

* 模块划分
* 上下游关系
* 开放接口边界
* 第三方集成边界
* 多租户边界
* 权限边界
* 国际化边界
* 数据治理边界
* 状态机边界
* 并发与锁边界
* 审计边界
* OOD & Reuse 边界
* 可复用基础能力边界
* 不做什么

ARCHITECTURE.md 可以描述模块和接口边界，但不得输出：

* 具体数据库字段
* DDL
* Migration
* 完整 API 路径
* Controller 设计
* Service 设计
* Repository 设计
* 页面代码结构
* 代码级实现
* 具体 class 设计
* 具体 Rule / Policy / Validator 代码

---

### 11.3 建议模块划分

必须至少评估以下模块：

```text
Identity & RBAC
Tenant Management
Store Management
Area & Table Management
Reservation Management
Queue Management
CheckIn Management
Seating Management
Cleaning & Turnover
Customer Management
Notification Boundary
Integration Boundary
Webhook Boundary
Audit Log
I18n & Locale
Operational Dashboard Boundary
```

---

### 11.4 多租户架构

固定层级：

```text
Platform
 ↓
Tenant
 ↓
Store
 ↓
Area
 ↓
Table
```

所有业务数据必须根据数据层级考虑：

* tenant_id
* store_id
* 是否跨门店共享
* 是否属于门店运营
* 是否属于平台配置
* 是否属于租户配置

不得简单使用“所有表都必须同时包含 tenant_id 和 store_id”。

---

### 11.5 开放接口边界

未来必须支持：

* POS
* 会员系统
* 小程序
* WhatsApp
* 第三方预约平台

必须预留开放能力边界：

```text
Customer API
Reservation API
Queue API
Table API
Seating API
Webhook
```

本轮只描述边界，不设计具体 API 路径，不写接口代码。

---

### 11.6 第三方集成边界

必须说明以下系统只作为未来集成边界，不纳入 V1 开发：

* POS
* Payment
* Membership
* Marketing
* WhatsApp
* WeChat Mini Program
* Google Maps
* Accounting
* Loyalty Points
* Delivery Platform

---

## 12. OOD & Reuse Governance

### 12.1 目标

本项目必须遵循 OOD 思路。

这里的 OOD 指：

```text
Object-Oriented Domain Design
```

不是单纯创建很多 class，也不是为了模式而模式。

目标是：

```text
业务对象清晰
职责边界清晰
状态流转清晰
规则可复用
代码块可复用
避免重复实现
避免过程式堆代码
```

后续任何开发不得以页面、接口或单个功能为中心组织代码，必须以业务对象、状态机、规则对象和可复用能力为中心组织代码。

---

### 12.2 OOD 第一原则

业务对象先于代码存在。

Codex 在后续任何开发轮中，不得直接从页面或 API 反推代码结构。

必须从以下对象出发：

```text
Platform
Tenant
Store
Area
Table
TableGroup
Customer
Reservation
QueueTicket
WalkIn
CheckIn
Seating
Cleaning
Turnover
```

每个对象必须有清晰职责。

不得出现：

```text
一个 Service 管所有业务
一个 Controller 塞所有流程
一个 DTO 混多个场景
一个状态字段承担多个业务含义
一个方法同时处理预约、排队、入座、清台
```

---

### 12.3 对象职责边界

后续代码设计必须遵守以下职责边界：

| 对象 | 负责 | 不负责 |
|---|---|---|
| Reservation | 预约资源、预约状态、到店前规则 | 排队号码、叫号逻辑 |
| QueueTicket | 排队、叫号、过号、重新加入 | 提前预约资源 |
| WalkIn | 现场客户到店场景 | 预约确认 |
| CheckIn | 到店确认事件 | 桌位占用 |
| Seating | 入座与桌位占用 | 预约创建、取号 |
| Cleaning | 清台状态流转 | 翻台统计本身 |
| Turnover | 翻台指标与结果 | 现场排桌动作 |
| Table | 单桌资源状态 | 客户身份 |
| TableGroup | 组合桌资源 | 单桌基础定义 |
| Customer | 客户识别与归属 | 预约流程控制 |

---

### 12.4 代码复用原则

后续开发中必须优先抽象可复用代码块。

可复用对象包括：

```text
状态机转换校验
多租户访问校验
Store 时区转换
i18n key 生成
E.164 电话校验
幂等 Key 校验
审计日志记录
软删除过滤
桌位锁
Queue 排序规则
Reservation 时间冲突校验
Table 可用性判断
TableGroup 有效性校验
```

不得在多个 Controller、Service 或页面中重复实现这些逻辑。

---

### 12.5 Rule Object 原则

业务规则不得散落在 Controller 或页面中。

以下规则必须沉淀为可复用 Rule / Policy / Validator：

```text
ReservationAvailabilityRule
QueueCallingRule
QueueRejoinRule
TableAssignmentRule
TableLockRule
TableGroupValidationRule
CustomerIdentityRule
StoreLocaleRule
TenantScopeRule
IdempotencyRule
AuditRule
```

命名可在后续技术设计中调整，但原则必须保留：

```text
规则独立
规则可测试
规则可复用
规则可替换
规则不散落
```

---

### 12.6 状态机复用原则

Reservation、QueueTicket、Table 的状态机必须集中定义。

不得在多个地方手写状态判断。

禁止：

```text
if status == "confirmed" then ...
if status == "waiting" then ...
if table.status == "occupied" then ...
```

散落在不同业务代码中。

必须统一沉淀为：

```text
State Machine
Transition Policy
Status Validator
```

后续开发中，所有状态转换必须经过统一入口。

---

### 12.7 多租户复用原则

tenant_id 和 store_id 校验必须复用。

不得在每个接口中手写重复判断。

必须统一沉淀为：

```text
TenantContext
StoreContext
ScopeGuard
TenantAccessPolicy
StoreAccessPolicy
```

原则：

```text
先判断 tenant scope
再判断 store scope
再执行业务动作
```

任何跨 Tenant 数据访问都必须禁止。

任何 Store 级运营数据都必须校验 store scope。

---

### 12.8 国际化复用原则

时间、日期、货币、语言文案不得散落在页面或接口中。

必须复用统一能力：

```text
StoreLocaleResolver
TimeZoneConverter
DateTimeFormatter
CurrencyFormatter
I18nMessageResolver
```

原则：

```text
数据库存 UTC
接口传 ISO8601
展示按 Store locale
文案走 i18n key
```

---

### 12.9 桌位分配复用原则

排桌逻辑不得写死在页面或单个接口中。

必须沉淀为可复用的 Table Assignment Policy。

排桌规则统一为：

```text
时间不冲突
容量满足
容量浪费最少
优先同区域
大桌保留大人数
最后才组合桌
```

未来不管来源是：

```text
Reservation
QueueTicket
WalkIn
Manual Override
Integration API
```

都必须复用同一套 Table Assignment Policy。

---

### 12.10 防重复实现规则

后续任何开发轮开始前，Codex 必须先检查是否已有可复用能力。

开发前必须回答：

```text
1. 是否已有相似对象？
2. 是否已有相似状态机？
3. 是否已有相似 Rule / Policy / Validator？
4. 是否已有相似 Service 能复用？
5. 是否已有通用多租户校验？
6. 是否已有通用审计能力？
7. 是否已有通用国际化能力？
8. 是否可以扩展现有能力，而不是新建重复实现？
```

如果已有能力，不得重复创建。

如果确实需要新建，必须说明：

```text
为什么不能复用
新对象职责是什么
与旧对象边界是什么
是否会造成重复逻辑
未来如何合并或扩展
```

---

### 12.11 OOD 失败条件

如果后续代码出现以下情况，视为 OOD 失败：

```text
ReservationService 同时处理排队、叫号、入座、清台
QueueService 直接修改预约核心状态
Controller 中写大量业务判断
前端页面中写业务状态机
多个地方重复判断 tenant_id / store_id
多个地方重复判断状态转换
多个地方重复写时间格式转换
多个地方重复写桌位分配逻辑
多个地方重复写审计日志
TableGroup 与 Table 占用规则散落
```

---

### 12.12 本轮文档要求补充

本轮治理文档必须为后续 OOD 留出边界。

BUSINESS_GLOSSARY.md 中必须明确：

```text
对象职责
对象边界
对象不负责什么
对象之间不能混用的概念
```

BUSINESS_RULES.md 中必须明确：

```text
哪些规则未来应沉淀为 Rule / Policy / Validator
```

ARCHITECTURE.md 中必须明确：

```text
哪些能力应作为可复用基础能力
哪些能力不得散落在 Controller / Service / Page 中
```

SKILL.md 中必须明确：

```text
执行 reservation-system 相关任务时，必须先检查已有对象、规则、状态机和复用能力。
```

---

## 13. 第四阶段：Technical Baseline

### 12.1 Frontend

固定技术路线：

```text
Vue 3
TypeScript
Vite
Pinia
Vue Router
```

---

### 12.2 Backend

固定技术路线：

```text
Java 21
Spring Boot 3
Spring Security
Validation
Flyway
```

---

### 12.3 Database

固定：

```text
PostgreSQL
```

本轮不得创建数据库结构。

---

### 12.4 Cache

固定：

```text
Redis
```

Redis 用于：

* 桌位锁
* 排队状态
* 幂等控制
* 热点缓存
* 临时状态保护
* 叫号状态同步

本轮只描述用途，不写 Redis 实现。

---

### 12.5 API

固定：

```text
REST API
OpenAPI
Webhook
```

本轮只描述原则，不设计具体 API。

---

### 12.6 Auth

固定：

```text
JWT
RBAC
```

角色固定为：

```text
platform_admin
tenant_admin
store_manager
store_staff
customer
integration_app
```

---

## 14. 国际化标准 P0

国际化必须第一天确定。

不得后补。

---

### 13.1 时间存储

统一使用：

```text
UTC
ISO8601
```

示例：

```text
2026-06-20T11:00:00Z
```

---

### 13.2 时间显示

时间显示由 Store 决定。

Store 必须配置：

```text
timezone
locale
date_format
time_format
currency
```

---

### 13.3 新加坡默认规则

新加坡默认配置：

```text
timezone: Asia/Singapore
locale: en-SG
date_format: DD-MM-YYYY
time_format: 24H
currency: SGD
```

显示示例：

```text
20-06-2026
19:00
```

---

### 13.4 电话号码

统一使用：

```text
E.164
```

示例：

```text
+6591234567
```

手机号不是客户必填字段。

无手机号客户必须被支持。

---

### 13.5 多语言

禁止硬编码文案。

统一使用 i18n key。

示例：

```text
reservation.created
reservation.confirmed
reservation.cancelled
reservation.no_show
queue.created
queue.called
queue.skipped
queue.rejoined
queue.seated
table.locked
table.occupied
table.cleaning
table.available
```

---

## 15. Reservation Skill Governance

### 14.1 必须建立 Skill

必须建立：

```text
docs/skills/reservation-system/
```

包含：

```text
docs/skills/reservation-system/SKILL_OVERVIEW.md
docs/skills/reservation-system/SKILL.md
```

---

### 14.2 Skill 必须回答

Reservation Skill 必须回答：

* 为什么存在
* 解决什么问题
* 不解决什么问题
* 核心对象
* 业务边界
* 技术边界
* 触发词
* 执行流程
* 输入
* 输出
* 禁止事项
* 验收标准

---

### 14.3 Reservation Skill 范围

负责：

* 预约
* 取号
* 排桌
* 叫号
* 签到
* 入座
* 清台
* 翻台

不负责：

* POS
* 支付
* 营销
* 会员积分
* 财务
* 库存
* 外卖
* 供应链
* 发票
* 会计

---

### 14.4 Skill 触发词

必须包含以下触发词：

```text
reservation
booking
queue
walk-in
table
table management
seating
check-in
cleaning
turnover
排队
预约
订位
取号
叫号
入座
清台
翻台
拼桌
组合桌
```

---

### 14.5 Skill 防爆炸原则

禁止创建以下 Skill：

```text
reservation-api-skill
reservation-backend-skill
reservation-frontend-skill
queue-skill
mobile-skill
table-skill
seating-skill
```

统一进入：

```text
reservation-system
```

原因：

Reservation、Queue、Table、Seating、Cleaning、Turnover 属于同一业务闭环，不能拆成多个互相割裂的 Skill。

---

## 16. 核心状态机

### 15.1 Reservation 状态机

Reservation 状态：

```text
draft
confirmed
arrived
seated
completed
cancelled
no_show
```

必须描述：

* 初始状态
* 合法转换
* 非法转换
* 触发者
* 前置条件
* 后置影响
* 审计要求
* 幂等要求
* 并发要求

默认理解：

```text
draft = 草稿
confirmed = 已确认预约
arrived = 已到店 / 已 CheckIn
seated = 已入座
completed = 已完成
cancelled = 已取消
no_show = 未到店
```

关键规则：

* confirmed 后客户到店，先进入 arrived
* arrived 后根据桌位情况进入 seated 或 Queue
* seated 后才可能 completed
* cancelled 和 no_show 是终止状态
* completed 是正常终止状态

---

### 15.2 Queue 状态机

Queue 状态：

```text
waiting
called
skipped
rejoined
seated
cancelled
expired
```

必须描述：

* 初始状态
* 合法转换
* 非法转换
* 触发者
* 前置条件
* 后置影响
* 审计要求
* 幂等要求
* 并发要求

默认理解：

```text
waiting = 等待中
called = 已叫号
skipped = 已过号
rejoined = 过号后重新加入
seated = 已入座
cancelled = 已取消
expired = 已过期
```

关键规则：

* called 后未响应可进入 skipped
* skipped 可 rejoined
* rejoined 保留原号码，但不默认插队
* seated、cancelled、expired 是终止状态

---

### 15.3 Table 状态机

Table 状态必须至少覆盖：

```text
available
locked
reserved
occupied
cleaning
inactive
```

默认理解：

```text
available = 可用
locked = 临时锁定
reserved = 已被预约资源占用或预分配
occupied = 已入座占用
cleaning = 清台中
inactive = 停用
```

必须说明：

* locked 的过期规则
* reserved 与 occupied 的区别
* cleaning 完成后才能 available
* inactive 不能被预约、排队、入座使用
* 临时组合桌释放时必须释放相关 Table

---

## 17. 桌位规则

桌位必须支持：

* 区域
* 容量
* 状态
* 预约
* 排队
* WalkIn
* 入座
* 清台
* 翻台

组合桌必须支持：

* 固定组合
* 临时组合

禁止：

* 循环引用
* 重复占用
* 同一时间同一桌位被多个有效业务占用
* 临时组合桌结束后继续占用桌位
* 固定组合桌停用后继续被推荐

---

## 18. 排桌规则

排桌推荐顺序：

```text
1. 时间不冲突
2. 容量满足
3. 容量浪费最少
4. 优先同区域
5. 大桌保留给大人数
6. 最后才使用组合桌
```

V1 只沉淀规则，不实现算法。

必须说明：

* 推荐结果可以被店员人工覆盖
* 人工覆盖必须审计
* 系统推荐不能绕过桌位锁
* 系统推荐不能造成重复占用
* 组合桌必须最后考虑
* 大桌必须优先保留给大人数

---

## 19. P0 风险控制

必须设计治理规则，但本轮不得实现。

必须覆盖：

* 预约保留时长
* 叫号保留时长
* 预计用餐时长
* 桌位锁
* 幂等 Key
* 并发控制
* 审计日志
* 软删除
* 取消原因
* no_show 原因
* 手工覆盖原因
* 临时组合桌释放
* 跨门店数据隔离
* 第三方调用幂等
* Webhook 重试边界

---

## 20. 手机优先原则

系统面向门店现场操作，必须手机优先。

目标：

```text
3 秒建单
5 秒排桌
1 秒找客
```

门店端页面限制：

```text
首页
预约
取号
列表
桌位
详情
```

V1 禁止复杂桌位图。

---

## 21. V1 禁止内容

第一版禁止：

* 复杂拖拽桌位图
* 原生 App
* AI 推荐
* 支付
* 营销
* 会员积分
* 财务
* 微服务
* Kubernetes
* 外卖
* 库存
* 供应链
* 复杂 BI
* 多渠道广告投放
* 优惠券
* 充值卡
* POS 深度集成

以上能力只能作为未来边界，不得进入本轮治理以外的开发实现。

---

## 22. 文档输出要求

### 21.1 BUSINESS_GLOSSARY.md

必须包含：

* 核心业务对象
* 对象定义
* 对象职责
* 上下游关系
* 生命周期
* 边界
* 不负责内容
* 多租户归属
* 示例
* 禁止混用的概念

必须重点区分：

```text
Reservation ≠ QueueTicket
WalkIn ≠ QueueTicket
CheckIn ≠ Seating
Seating ≠ Reservation
Cleaning ≠ Turnover
Table ≠ TableGroup
Customer ≠ Member
```

---

### 21.2 BUSINESS_RULES.md

必须包含：

* 预约规则
* 排队规则
* WalkIn 规则
* CheckIn 规则
* Seating 规则
* Cleaning 规则
* Turnover 规则
* Table 规则
* TableGroup 规则
* Customer 规则
* 多租户规则
* 国际化规则
* 状态机规则
* 风险控制规则
* Confirmed / Defaulted / Open / Open Conflict 分类

本任务中 Product Owner 已确认的 11 项规则必须标记为 Confirmed。

---

### 21.3 DATA_STANDARD.md

必须包含：

* 数据层级标准
* tenant_id / store_id 规则
* 唯一性规则
* 完整性规则
* 引用规则
* 删除规则
* 审计规则
* 状态机规则
* 国际化数据规则
* 时间格式规则
* 电话号码规则
* 幂等规则
* 并发规则
* 软删除规则

不得包含具体 DDL。

---

### 21.4 DATA_CHECKLIST.md

必须包含可反复使用的数据检查清单。

检查项至少覆盖：

* 是否存在跨 Tenant 引用
* 是否错误要求所有数据都有 store_id
* 是否存在无手机号客户不支持问题
* 是否存在 Reservation 和 Queue 混用问题
* 是否存在 WalkIn 和 QueueTicket 混用问题
* 是否存在 CheckIn 和 Seating 混用问题
* 是否存在组合桌循环引用风险
* 是否存在临时组合桌释放规则缺失
* 是否存在状态机非法转换
* 是否存在无审计的关键操作
* 是否存在硬编码语言文案
* 是否存在本地时间直接存储
* 是否存在电话号码非 E.164 标准
* 是否存在桌位重复占用风险
* 是否存在幂等 Key 缺失风险

---

### 21.5 ARCHITECTURE.md

必须包含：

* 系统定位
* 模块划分
* 模块边界
* 上下游关系
* 多租户架构
* 国际化架构
* Auth / RBAC 边界
* Redis 使用边界
* PostgreSQL 使用边界
* REST / OpenAPI / Webhook 边界
* 第三方集成边界
* 不做什么
* 后续阶段建议

不得包含具体数据库字段、DDL、完整 API 路径或代码实现。

---

### 21.6 SKILL_OVERVIEW.md

必须包含：

* Skill 名称
* 为什么存在
* 解决什么问题
* 不解决什么问题
* 核心业务闭环
* 适用场景
* 不适用场景
* 与其他系统边界
* 防爆炸原则

---

### 21.7 SKILL.md

必须包含：

* Skill 目的
* 触发词
* 输入
* 输出
* 执行流程
* 核心对象
* 状态机
* 业务规则
* 数据治理规则
* 国际化规则
* 禁止事项
* 验收标准
* OOD 与复用检查
* 下一步入口

---

## 23. 本轮成功标准

本轮成功不以是否完成数据库、API 或页面为标准。

本轮成功标准是：

1. 业务语言是否统一
2. 业务边界是否清楚
3. 数据治理规则是否成文
4. 多租户规则是否明确
5. 国际化规则是否明确
6. 状态机规则是否明确
7. OOD 与代码复用边界是否明确
8. 技术路线是否固定
9. Reservation Skill 是否建立
10. 是否严格避免进入开发阶段

只要出现业务代码、数据库、Migration、API、UI 修改，本轮即失败。

---

## 24. 完成前自检

结束前必须检查实际变更范围。

最终报告必须列出：

1. 已建立的治理文档
2. 已确认的业务对象
3. Confirmed 业务规则
4. Defaulted 业务规则
5. Open 业务问题
6. Open Conflict 冲突项
7. 国际化规则
8. OOD & Reuse Governance
9. 技术路线
10. Reservation Skill 是否创建
11. 是否修改业务代码
12. 是否修改数据库或 Migration
13. 是否实现 API
14. 是否实现 UI
15. 实际修改文件清单
16. 下一步建议

如果实际修改文件中出现非允许文件，本轮必须判定为失败。

---

## 25. 最终报告固定格式

最终必须按以下格式输出：

```text
## Governance Startup Result

### 1. Created / Updated Documents
- ...

### 2. Confirmed Business Objects
- ...

### 3. Confirmed Business Rules
- ...

### 4. Defaulted Business Rules
- ...

### 5. Open Questions
- ...

### 6. Open Conflicts
- ...

### 7. I18n Rules
- ...

### 8. Technical Baseline
- Frontend:
- Backend:
- Database:
- Cache:
- API:
- Auth:

### 9. Reservation Skill
Created: Yes / No

### 10. Change Safety Check
Business code changed: No
Database changed: No
Migration created: No
API implemented: No
UI implemented: No

### 11. Actual Modified Files
- ...

### 13. Next Step Recommendation
- ...
```

最终必须明确声明：

```text
Business code changed: No
Database changed: No
Migration created: No
API implemented: No
UI implemented: No
```

如果任何一项不是 No，本轮失败。

---

## 26. 下一轮入口

本轮完成后，下一轮才允许进入：

```text
Database Design
```

下一轮 Database Design 必须基于本轮产出的：

```text
BUSINESS_GLOSSARY.md
BUSINESS_RULES.md
DATA_STANDARD.md
DATA_CHECKLIST.md
ARCHITECTURE.md
SKILL_OVERVIEW.md
SKILL.md
```

下一轮仍不得直接跳过治理文档。

下一轮开始前必须先读取本轮治理文档，再提出数据库设计方案。

本轮结束。
