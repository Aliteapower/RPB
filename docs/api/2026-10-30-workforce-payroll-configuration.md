# Task1 薪资配置契约（本地实现）

所有路径前缀 `/api/v1/stores/{storeId}/workforce`，先经PayrollAccess校验公司完整门店/AppGate及员工历史公司资格；employeeManage不授权。金额为十进制字符串。

- GET `/employees/{id}/pay-summary?companyId=&on=YYYY-MM-DD`：EmployeePaySummary(employeeId,companyId,on,profiles)，profiles为ProfilePeriod(profile,effectiveTo,state,sourceComplete)，state为current/future/history。effectiveTo为下一条前一日，null为长期。无数据返回空数组，不写入。
- GET `/position-salary-defaults?companyId=&positionId=`：PositionSalaryDefault[]，按生效日排序。
- PUT `/position-salary-defaults/{id}`：DefaultWrite(expectedVersion,companyId,positionId,effectiveFrom,templateId,enabled)；响应PositionSalaryDefault(id,companyId,positionId,effectiveFrom,templateId,templateVersion,enabled,version)。相同ID/相同请求重放返回原值；旧version冲突409。岗位/模板须同租户，模板同公司且启用。映射停用不影响既有方案。
- GET `/pay-profile-preview?companyId=&employeeId=&on=&templateId=`：ProfilePreview(template,defaultMapping)；templateId可选，缺省按员工岗位及日期选最近一条映射，停用映射代表无默认。纯读取，无有效默认返回template=null。显式templateId忽略默认。
- PUT既有`/pay-profiles/{id}`增加可选 expectedTemplateVersion、defaultMappingId、expectedDefaultVersion。新客户端应用预览必须提交expectedTemplateVersion；默认应用同时提交映射ID/version。保存时在来源共享锁及配置锁同事务核验，变化409（WORKFORCE_CONFLICT），停用/无效400（WORKFORCE_INVALID），无权403/不存在404沿用现有错误。保存origin由服务器捕获(templateName,templateRule,defaultMappingId,defaultVersion)，个人差异从profile.rule与origin.templateRule比较。只编辑不重新应用时不传预览字段且保留旧origin和templateVersion；旧客户端新建仍接受，但origin=null表示证据不完整。新客户端变更templateId必须重新预览；旧客户端首次创建或改选模板仍兼容，但origin=null且不声称完整来源。同ID相同负载重放返回原快照；不能绕过冻结锁修改。

V107仅新增workforce_position_salary_defaults：tenant/company/position/effectiveFrom唯一，复合FK引用同租户岗位、同租户同公司模板及公司，version>0，审计时间；旧JSON、工资快照及旧来源指纹不回写。映射维护时templateVersion只为审计，预览取当前启用模板。GET无副作用。

测试清单：无权摘要/默认拒绝；当前/未来/历史及日期边界；预览模板/默认变化409；个人覆盖来源基线；仅编辑来源保留；旧来源缺失；模板停用/跨公司/租户拒绝；已确认历史冻结；默认变更不联动；前端精确employee/company上下文、无权不请求、无默认不误选。
