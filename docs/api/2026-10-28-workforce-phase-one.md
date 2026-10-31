# Workforce 第一阶段 API 与数据库合同

基址 `/api/v1/stores/{storeId}/workforce`。租户从已认证 CurrentActor 获取，不信任客户端 tenantId。全部端点要求可访问路径门店和 workforce AppGate；新产品无 legacy 订阅回退。

|方法与路径|用途|
|---|---|
|GET /home|当前门店的能力、可管理门店、公司及可绑定账号选项；普通员工只返回本人基础资料|
|GET /employees|当前店员工，分页 limit=1..100 / offset>=0；部分范围记录脱敏且不可编辑|
|GET /employees/{id}|员工详情，路径店必须是员工工作店，受范围约束|
|PUT /employees/{id}|客户端 UUID，新建 expectedVersion=0；基本资料、发薪公司、主店、工作店和可选账号绑定/创建|
|GET /companies|总部可维护的发薪公司配置|
|PUT /companies/{id}|expectedVersion、registrationNo、payDay(1..28)、enabled；不修改原公司资料|
|GET /administrators|总部查看当前店显式委派|
|PUT /administrators/{accountId}|expectedVersion、employeeManage、payrollCompanyId(可空)；工资开关只保存授权，不提供工资业务|

员工写入字段：expectedVersion、employeeNo、name、phone、email、jobTitle、hireDate、leaveDate、status(active/left)、companyId、companyEffectiveDate、mainStoreId、storeIds、accountId、username/password(仅首次创建账号)。第一阶段公司及门店变更当天生效；公司归属历史、工作门店历史单独追加。绑定既有普通员工账号不改密码/原权限/原门店，只增加 workforce.view；工作店必须是账号已有门店的子集。仅打卡新账号只赋 workforce.view、store_staff 和指定工作门店，不走旧建号默认权限。

写请求头 X-RPB-Workforce:1，拒绝 Sec-Fetch-Site:cross-site/same-site。响应 Cache-Control:private,no-store。401 WORKFORCE_UNAUTHENTICATED；403 WORKFORCE_FORBIDDEN / AppGate 原因码；404 WORKFORCE_NOT_FOUND；400 WORKFORCE_INVALID；409 WORKFORCE_CONFLICT；500 WORKFORCE_PERSISTENCE_ERROR。PUT 基于 expectedVersion，成功返回新版本；员工创建重复 UUID 且非敏感字段一致时返回原结果，差异返回409；无任何明文密码入库或审计。其他更新相同重试需重新加载版本，409不能当作成功。

V088 仅新增 workforce 公司配置、员工、发薪归属历史、工作分配历史、管理员委派表。tenant/company/store/account 复合 FK 隔离；员工编号租户内不重复；账号只关联一个员工；版本、日期、主店所属工作范围由应用和约束共同验证；无删除接口。关键写入与现有 AuditLog 同事务。

数据库评审：不更改旧表业务字段，新增权限仅 workforce；产品注册不授予订阅，价格 disabled。回滚应用时保留新增表，不删除已有资料。验证使用 pointer 指向 PG 独立 schema。
