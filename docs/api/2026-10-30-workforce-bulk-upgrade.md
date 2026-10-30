# 阶段4：批量审批与门店模板升级

基础路径 `/api/v1/stores/{storeId}/workforce`。PUT 保留 `X-RPB-Workforce: 1`、同源保护及原权限。以下为实现契约，验证结果由阶段报告记录。

| 路径 | 契约 |
|---|---|
| GET /pay-change-requests?companyId=UUID&month=YYYY-MM | month可选。月度子项精确月份匹配；含长期方案/模板结果时effectiveDate≤月末作为检索范围，**并非该月实际影响结论**。详情impact才展示真实来源预估，后续方案可能已覆盖。 |
| PUT /pay-change-bulk-actions | `{companyId,month,items:[{requestId,commandId,expectedRevision,expectedHash,action,note}]}` → `{results:[{requestId,commandId,outcome,request,errorCode,httpStatus}]}`。month可空，action仅approve/reject；最多100份。每份独立REQUIRES_NEW；outcome为success/stale/failed。失败request为null。重复使用原item及commandId；重试不会重新批准已成功项。 |
| GET /pay-template-library/{masterId}/upgrade-preview?adoptionId=UUID&releaseVersion=N&effectiveDate=YYYY-MM-DD | `UpgradePreview`，目标固定已发布版本可非latest。 |
| PUT /pay-change-requests/{id}/upgrade | `UpgradeWrite` → 原`PayChange` draft；由原actions提交/批准。此入口不直接生效。 |

`UpgradePreview = {baseAdoption,oldRelease,targetRelease,expectedLatestStoreVersion,effectiveDate,differences,hasLocalChanges,previewHash}`。

`differences = [{path,label,oldBase,local,target,localChanged,targetChanged,conflict}]`。三个值均为string|null；对象/列表为JSON字符串；字段label为可读中文，path稳定供选择与审计。只有存在变化的字段返回。稳定且唯一itemId逐字段比较；缺失/重复ID保守将items作为整体比较，绝不按数组位置猜身份。

`UpgradeWrite = {expectedRevision,companyId,adoptionId,targetReleaseVersion,effectiveDate,expectedLatestStoreVersion,previewHash,derivedRule,resolutions:[{path,choice}],reason}`。choice为local/target/custom；每个冲突必须显式解决且提交**完整**derivedRule。local/target选择必须匹配提交结果，custom表示明确人工调整。无冲突项允许显式人工调整；服务端仍执行原完整规则校验。保存后再次编辑走同一upgrade入口并带当前revision，不能通过普通save静默移除升级上下文。

普通Write追加可空 `upgrade = {targetReleaseVersion,expectedLatestStoreVersion,previewHash,resolutions}`，专用升级入口转入此统一聚合；直接调用普通save也执行同一冲突、来源与明确日期校验，不构成旁路。无upgrade的旧派生请求保留原语义。

`PayChange.content.upgrade? = {oldRelease,targetRelease,expectedLatestStoreVersion,effectiveDate,previewHash,resolutions}`。该字段为null时不序列化，保持阶段2/3旧JSON与hash；不改写旧submitted、revision或hash。固定目标后发布更高母版不会单独使申请过期；本店currentAt(effectiveDate)、latest并发基线、公司及目标范围仍重核。未来计划继续独立显示，不允许无日期enable覆盖。

模板升级是候选变更，不自动修改现有员工方案；接口不返回员工采用关系或个人工资。原enable仅适用于未发生本店规则修改（含项目名称）的采用；本店派生有修改时返回409 `WORKFORCE_PAYROLL_TEMPLATE_UPGRADE_REQUIRED`，需显式升级申请。无冲突不等于自动批准。

冲突未显式解决返回409 `WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED`；过期preview/revision/hash为409 `WORKFORCE_CONFLICT`，范围/权限仍403，数据不存在404，非法参数400。批量顶层参数非法400，逐项失败回200并逐项给HTTP状态和稳定errorCode，未知内部失败为500 `WORKFORCE_PAYROLL_BULK_ITEM_FAILED`。stale是已持久化业务结果，不能显示为批准成功。

影响预览复用原 `/pay-change-requests/{id}/impact`；金额为十进制字符串，未知null；模板仅候选的净额未知，不能据此声称现有员工减薪。新增 `standardBefore`、`standardAfter`、`standardPercent`（均string|null），前后标准复用原固定标准函数；比例为差额/abs(前值)×100，保留2位；前值0或未知返回null。标准与实际净额分开，支持绝对差额/比例人工排序，禁止自创幅度阈值/自动批准。基线过期的模板仍可展示固定快照标准差异，issues注明SOURCE_BASELINE_CHANGED，净额保持未知。

风险提示新增 `NEW_DEDUCTION`，包括deduction及CDAC项目新增；原DEDUCTION_REVIEW保留一般扣款复核。previewHash只绑定旧母版/本店/目标快照、latest版本与生效日期，不含差异label等展示文案。
