# 合同/假期/折现 API 与持久化契约

Base `/api/v1/stores/{storeId}/workforce`，继承 WorkforceEndpointSupport 写防护及 no-store。所有实体按 tenant/company/employee 归属；404 不泄露跨租户实体；409 版本/余额/重复消费冲突；400 无效规则、日期、用途；403 权限及本人审批。沿用shared source-write锁→员工锁→账户锁，工资捕获exclusive source锁。

- GET `/compliance-reference`：总部载入带来源摘要的参考草稿，不写库、不自动发布。
- GET/PUT `/compliance-policies[/{id}]`：总部管理规则主档版本；Write expectedVersion/name/effectiveFrom/effectiveTo/rules/sources/publish，发布需引用资料和核对日期，不可覆盖已发布版本，变更新增version；只读GET历史versions。
- GET/PUT `/employees/{employeeId}/employment-contracts[/{id}]`：员工管理全店范围；独立新生效版本，含policyId/policyVersion及合同工时、服务起算、工作类别和依据。金额不在员工管理读取接口返回。
- GET `/leave-accounts?employeeId=&year=`：本人/有权员工管理；账户汇总/来源/流水、不返回薪资。POST式命令沿用 PUT `.../{commandId}`：启用、期初/调整、年结；expectedVersion及原因必填，重复同命令同请求返回原响应，异请求冲突。
- GET/PUT `/leave-type-policies[/{typeId}]`：绑定假期类型到annual/sick/hospital/company/unpaid；不按名字猜旧数据。
- 原`leaves/{id}`和leave-batch/group入口不变，在同一事务预占/转使用/释放/冲回，旧无绑定类型保持兼容，已启用政策的带薪未绑定类型阻断。
- GET/PUT `/leave-encashments[/{id}]`：工资公司权限，选择employee/year/units/settlementMonth/reason/contractEvidence及余额来源，预览净/毛金额。commands/{id}提交/他人审批/取消；批准不付款；正式计薪核销，source唯一跨普通/分段/补发消费。员工只能看本人申请进度，工资金额按本人已发布工资单。
- 工时新政策采用人扩展核准决定，不替换原始打卡；salary capture中包含contract/policy/leave/encash来源版本，未解决超时阻断；未采用人旧算法兼容。

V116起仅增加政策/版本/合同、账户/不可变流水/申请占用、类型绑定、折现/命令/消费结构，复合tenant外键、账户唯一tenant/company/employee/year/kind、命令tenant/id唯一、消费tenant/source唯一，来源日期与有效期check，常用范围索引。老表不回填、不修改旧迁移。新字段作为可选来源证据兼容旧JSON；正式历史不重写。

模型算子有版本边界：可编辑规则参数与来源文本，不能用任意JSON/表达式执行代码。未知模型阻断。无新的产品权限/外部服务；本地allowlist只加这些确切workforce资源，不开放通用API。并发、幂等、批量原子性、跨租户、App Gate及旧工资不变为必须测试项。

## 本批补充接口（实现状态）

- GET `/compliance-reference`：服务端官方参考参数／中文摘要／官方网址／核对日期。不是有效政策。
- GET `/compliance-policies`、`/{id}/versions`；PUT `/{id}`：expectedVersion、名称、有效日期、publish、typed rules及sources，新增不可变版本。
- GET `/employees/{employeeId}/employment-contracts`：items、可采用的已发布政策、current companyId及suggestedServiceStart。全员工序列版本与当前雇主默认资料分开，不从旧雇主复制资历。
- PUT `/employees/{employeeId}/employment-contracts/{commandId}`：新生效版本，commandId幂等。
- GET `/leave-accounts?employeeId=&year=`；PUT `/leave-accounts/{enable|refresh|review|adjust|year-end}/{commandId}`：沿用员工权限、来源写锁、员工锁及不可变命令回执；写操作仅限当前雇主。
- GET `/leave-type-policies`；PUT `/leave-type-policies/{typeId}`：类型与annual/sick/hospital/company/unpaid规则绑定；不推断名称。
- GET `/leave-encashments?employeeId=`、`/leave-encashments/context?employeeId=&year=`：工资权限可见，不由员工管理权限替代。
- POST `/leave-encashments/preview`：只读计价，返回valuation、available和issues，不占用余额。
- PUT `/leave-encashments/{commandId}`：包含employeeId/accountId/quantity/valuationOn/payableOn/purpose/evidence/classifications/route/originMonth/expectedValuation；校验报价后原子占用并创建既有工资变更审批，返回requestId。route为monthly或supplemental，monthly可进入最终分段。
- LeaveWrite及Leave新增可空medicalReference；历史为空保持原输出；已采用政策的门诊／住院假批准须有医疗证明引用。
- ApprovalSource、CapturedLine及冻结PayrollLine新增可空compliance固定采用证据；旧记录空值不写入JSON，不改变旧指纹。

新路由沿用WorkforceEndpointSupport的身份、同源、X-RPB-Workforce及private,no-store；安全配置仅放行精确方法和路径，业务服务执行App Gate及tenant/store/company/employee权限。

新增结构V116–V120。V119给既有工资事件唯一消费增加segment来源；旧记录不改写。V120为政策、合同、额度信用、流水、命令和折现证据增加不可变约束。
