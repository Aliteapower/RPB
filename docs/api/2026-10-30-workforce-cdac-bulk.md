# 薪资方案CDAC批量设置

沿用个人薪资方案的公司薪资授权及公司全部门店AppGate/访问覆盖，不增加角色权限。不是员工管理权限。只新增生效方案，保留原工资、CPF、付款、模板版本及历史工资。UI复用薪资方案列表和弹窗，不增加页面。

PUT `/api/v1/stores/{storeId}/workforce/pay-profiles/cdac-bulk/preview`：请求`{companyId,month,employeeIds,cdac:{status,reference}}`，月份YYYY-MM，1至500个去重员工，status仅applicable/exempt/manual；豁免和手动均必须说明（500字以内）。响应`{fingerprint,month,rows:[{employeeId,employeeName,sourceProfileId,sourceEffectiveFrom,previousStatus,nextEffectiveFrom,status,reason}],ready,unchanged,blocked}`。按生效月首日选来源方案，显示下一既定方案的生效日，说明此次范围。无来源、目标月已存在方案、目标起及后续已确认/发布工资、外国员工自动适用、非手动方式与旧CDAC项目冲突、已离职/尚未入职/当月不属该公司均阻止。完全相同设置显示无需变更。未知/越权员工404/403，不泄露资料。

PUT `/pay-profiles/cdac-bulk/{commandId}`：请求`{selection:<上述请求>,fingerprint}`，重新检查权限、员工/方案/冻结月份及指纹。指纹变化409 WORKFORCE_CDAC_BULK_CHANGED；仅保存预览ready行，其他行不变，整个保存事务原子提交。响应`{preview,profiles:[新ID]}`。命令ID相同、同actor/store/company及相同规范化载荷重试返回原结果；异载荷409；重放仍重验权限。V103仅新增租户/公司/门店/actor范围命令表，用于可靠重试及审计，不更新原表数据。新增方案ID由命令ID+员工ID生成。源写共享锁与工资确认独占锁协调，员工按UUID顺序加锁，避免预览后并发调整或确认绕过。

手动模式继续在月度计薪核对人工项目金额，不设置默认扣款。已有已确认工资保持快照不变，不批量修改其对应月份或更早月份。日后已有方案保持，预览明确显示覆盖截至下一生效日之前；已有同月方案不自动覆盖，须单独维护或选择后续月份。

测试清单：三模式/依据必填、净保留其他字段、新生效方案、次月既定方案、确认工资保护、旧手工冲突、无方案/离职、预览失效、混合跳过、幂等/冲突重放、并发提交、事务回滚、公司/门店/租户/AppGate及前端选择/失效/重试状态。仅本地，无部署。
