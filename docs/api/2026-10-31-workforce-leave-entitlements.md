# 年度个人假期额度与余额 API（本地实现，未发布）

路径基于 `/api/v1/stores/{storeId}/workforce`。适用于 clock、default_normal、contract、free 四种方式，不涉及工资金额。

## 查询

`GET /leave-entitlements?year=2026&employeeId=<可省略>&companyId=<可省略>`

省略 employeeId 查询本人；省略 companyId 使用入口门店公司。返回 `{employeeId,year,canManage,balances:[]}`。

每项余额：`typeId,typeName,sourceStoreId,companyId,policy,state,usedSeconds,reservedSeconds,used,reserved,remaining,unknownReasons`。

- `policy` 无配置为 null；有配置时包含下文写入字段及 `id,employeeId,version`。
- `state` 为 `KNOWN` 或 `UNKNOWN`。缺年度额度、缺明确安排或休息窗口、跨工作日重叠无法归属时返回 UNKNOWN，`remaining:null`，绝不以零伪装。
- 秒数为计量原值；HOURS 除以 3600，DAYS 除以该年度版本 `dayMinutes*60`；只在最终展示单位保留 6 位小数。剩余 = grant + carryIn + adjustment − 净已用 − 净预占。允许负余额。
- 已批准及销假待审计已用；待审预占；拒绝、撤回、完成销假不占用。已用优先于重叠预占；新旧来源时间并集避免重复计数。
- 查询整年度全部门店来源，按开班 `workDate` 归年，跨夜班次保留开班日归属；不会按当月查询截断年度余额。
- 新来源 `workforce_attendance_exceptions` 与旧 `workforce_leaves` 的原排班关联都读取；复用 AttendanceDailyService 精确计量，不臆造 8 小时一天或未知旧记录的余额。取消排班后无法恢复有效计量时可返回未知。
- 假别仍使用现有门店配置的 typeId；同名假别不会擅自合并。sourceStoreId 标明配置归属，写政策须从该门店入口操作。
- GET 不写任何额度、考勤或审计记录。

## 登记年度政策

`PUT /employees/{employeeId}/leave-entitlements/{commandId}`

```json
{"expectedVersion":0,"companyId":"UUID","typeId":"UUID","year":2026,"grant":"14.5","carryIn":"2","adjustment":"-0.5","unit":"DAYS","dayMinutes":450,"reason":"明确年度发放及结转来源"}
```

grant/carryIn 非负；adjustment 可正负；最多 12 位整数和 6 位小数；unit 仅 HOURS/DAYS。DAYS 必填 1–1440 的 dayMinutes，不设默认值；HOURS 不依赖天长。reason 必填，最多 500 字。年度 1900–9998。政策覆盖该员工、公司、假别、年度的完整额度，非累加命令；修订需最新 expectedVersion，初次为 0，过期版本 409。

返回最新 Policy。每次追加独立版本、保留旧版本和原因；commandId 在租户内唯一。同一用户、员工、门店、完整相同请求重放返回原响应；改载荷或用户重放 409。共享来源锁与员工锁保护并发，审计失败事务回滚。

## 权限与业务边界

- 员工只能读取本人，现有请假申请和审批入口保持其角色规则。
- 读取他人及登记政策需 employee_manage，覆盖该员工所有关联门店；payroll-only 不具备发放权限。所有入口经过 AppGate、租户、门店、公司和员工范围检查。
- 不自动按工龄、入职月数或法定规则计算年度发放；carryIn 由管理员核实登记，不自动搬运前一年剩余。未配置自动政策不声称已配置。
- 本模块不因额度不足自动拒假、不转换为无薪、不改工资。无薪假也可无额度政策并显示未知；审批保持独立。
- 年度政策单位和折算修改会重算该年度展示余额，既有政策版本和原始请假不改写。

## 验证范围

独立 PostgreSQL 合成 schema（端口读取 `target/local-postgres-current.txt`）；年度版本、重放、权限、四方式净计量、待审重叠、销假释放、跨店一致性、未知计量、审计回滚及 AppGate；本地安全链仅放行精确 GET/PUT 路径。Node 真实 Vue 渲染检查只读视图、明确天长及同命令失败重试。未完成登录后桌面/手机视觉验收，未生产发布。


## 合并后的权益来源优先级（2026-10-31）

同一雇主/员工/年度只采用一个权益来源：存在该年度有效的已采用合规合同则正式 `leave accounts` 优先，旧手工 grant 留存审计但不叠加；该年度禁止另写手工额度，409 `LEAVE_CANONICAL_ACCOUNT_REQUIRED`。按显式假别映射查询正式账户，不按名称猜测。缺映射/账户仍为 UNKNOWN。

`Balance` 新增可选 `accountUnit: HOURS | DAYS`。正式账户行 `policy=null`、`unknownReasons` 含来源标记 `CANONICAL_LEAVE_ACCOUNT`；`used/reserved/remaining` 直接来自正式余额引擎（包含折现与同池病假限制），来源标记本身不代表UNKNOWN。单位优先读accountUnit，不能因policy为空猜小时。正式行不得提供手工授额按钮，应引导至合同假期账户维护。正式权益存在审核问题时为UNKNOWN，并返回具体原因；`usedSeconds/reservedSeconds` 保持null，避免按当前合同重译历史已用天数。

未采用合规合同者仍可使用显式手工年度额度；两类请假新增申请均在员工锁内验证净可用余额，已有批准销假仍按最终批准才释放。无手工政策显示未知，保留旧无额度请假流程，不暗示无限余额。
