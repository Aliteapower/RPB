# 班次人数点击查看员工名单：本地验证

2026-10-29，D:\RPB。本次只增加读取名单及交互，不部署。

## 功能与契约

周/月表每日班次人数大于0时可点击，弹窗仅列对应日期/班次；班次汇总也可点击，按当前已应用区间显示各日期员工。列表列为工作日期、班次快照名称、员工编号、姓名，显示区间去重人数和人次，50条独立分页。沿用已应用岗位/姓名条件，不采用未提交的筛选草稿。弹窗只读，查看期间暂停日历静默同步，关闭/Escape保留原周次、筛选和表格。

新增GET /schedule-staffing/employees，参数from/to、shiftId、positionId、search、limit/offset。最多31天、limit1..100、offset非负。名单与统计共用staffingScope/staffingParams，限定tenant/store/date和筛选，仅scheduled且未删除；按员工+日期去重，不受排班表员工分页影响。按日期、员工编号、员工ID稳定排序。单次名单查询用只读REPEATABLE_READ，计数及数据使用同一快照。无迁移和业务写入。

## API / 权限审查

Controller仅调用BulkSchedulePlanService，沿用WorkforceAccess.enter/manage及AppGate。普通员工不能读取管理名单，未授权门店403；其他门店/租户shiftId不会扩大范围，只得到当前店的空结果。返回姓名、编号、日期、班次及员工ID，无电话、邮箱或工资字段。private/no-store；匿名401、权限403、参数400。本地安全链只增加精确GET路径，PUT仍拒绝。

## TDD 与验证结果

前置数据库测试因旧实现没有名单DTO/方法编译失败；两个前端入口测试在旧UI失败。实现后：

- 前端68项通过。新覆盖每日/汇总入口、门店及日期班次参数、沿用已应用筛选、独立50条分页、关闭保留日历、查看期间同步暂停、权限失败清空上一页、空名单可关闭，原63项回归保留。
- WorkforceBulkSchedulePostgresTest 25项通过。52名员工、53个员工日期组合跨两页完整返回；同员工同日同班多段排班只一行；单日人数与统计一致；取消/删除排除，岗位/姓名及字面通配符过滤，其他门店/租户为空，员工权限/AppGate、参数范围限制通过。原大批量与并发测试保留。
- WorkforceBulkScheduleApiTest 4项、WorkforceBulkLocalRuntimeSecurityTest 1项通过，合计30项后端相关测试。验证认证、缓存头、日期/必填参数、本地GET允许及PUT拒绝。
- vue-tsc/Vite构建、隔离后端打包通过。

证据：target/workforce-staffing-detail-20261029/frontend.log、backend.log、postgres.xml、api-tests.xml、security-tests.xml、build.log。

## 实际API及页面

先核对target/local-postgres-current.txt的D:\RPB运行所有权、PG61644；使用原workforce_browser_6b3d0ca974744b0c9b681036f8462dd6，本次无迁移。核实旧进程/JAR归属、备份后仅重启本地8096。Windows端口释放短暂延迟后复查无监听再重试成功；未停止其他环境进程。

12项实际接口检查通过：周名单3人次/1人，对应当日名单1人；管理员/经理一致；员工403、匿名401、不可访问店403；他店同shiftId为空；limit101/错误日期400；limit1的两页分别返回不同日期；响应仅含声明字段。

独立后台测试页经理身份实测：10月29日每日人数打开单日名单；汇总打开10月26日至11月1日列表，按29/30/31日列出同一员工，显示1人、3人次。关闭后原周次2026-10-26和汇总不变；后台页已关闭，不打断用户主页面。截图employees-day.png、employees-period.png在证据目录。

原25条排班逐字段一致，无新增排班；员工/班次/工作门店/工资/付款/账号摘要不变；原打卡、出勤、补卡、请假摘要不变。API和页面验证未产生业务写入。证据data-before.json/data-after.json、clock-before.json/clock-after.json、api.json。

## Code / UI Review

无未解决P0/P1：模块职责清楚，列表DTO显式、SQL有租户与门店约束，名单和统计复用相同条件。只读事务一致快照，分页无静默截断；加载/空/错误/权限状态可见，切页先清空旧名单，关闭后的晚到请求不会重新填入。独立StaffingEmployeesDialog复用原native dialog及请求归属保护，键盘入口/关闭可用，0人格不误导为可点操作。表格内部横向滚动、长内容沿用弹窗纵向滚动。未修改主浏览器视口或增加窄屏截图。

## 限制及回退

名单反映打开/翻页时最新有效排班，可能与较早的统计数字暂时不同；关闭后原日历继续按已有15秒机制同步。每页50条，可连续分页，不等于只有当前员工页的名单。无提交、推送、生产迁移或部署。回退配套前后端构建即可移除新入口/API，本次无数据回退需求；原运行JAR已备份到证据目录/backend-before.jar。
