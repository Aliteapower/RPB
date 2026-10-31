# 请假申请分组及统一审批

新增批次提交成功后在原事务中保存不可变申请组及成员关系，组ID使用原批量请求第一项固定UUID，保留原请求JSON用于严格幂等比较。原单日申请和历史无组记录继续独立展示，不按时间、原因等猜测回填。原Leave DTO和核准快照不变。

GET `/api/v1/stores/{storeId}/workforce/leave-groups?from=...&to=...` 返回 `{timezone,items:[{id,grouped,items:Leave[],from,to}]}`。沿用最多31天筛选，组内任一天匹配则展示完整组，详情链接使用原申请范围，避免跨月成员打不开。单组最多1000项；总结果超5000项拒绝，不静默截断。员工只看本人，管理员/经理仅在既有管理门店范围可看。

PUT `/leave-groups/{id}/decision` 请求 `{status:'approved'|'rejected',note,items:[{id,expectedVersion}]}`。统一处理当前全部待审天，已单独审核记录不再改变；服务端核验成员身份、所有当前pending项均在请求中、每项版本，采用原LeaveService逐条审批但同一事务提交。任一天失败整批回滚。原PUT `/leaves/{id}/decision`继续用于单天审批及其他原有状态流转。

精确同源请求保护和AppGate沿用；不能自行审核、跨店、跨租户或越过经理授权。重复相同版本+结果+审核人+备注按原单条审批重放规则处理；混入其他组成员、遗漏待审天、版本变化、另一审核人抢先处理均409，不部分成功。不存在或不可见组404；请求非法400；权限拒绝403；未登录401。所有响应no-store。

V101为新增组/成员表及旧表复合唯一约束，不修改原记录内容。成员通过复合FK同时绑定组与请假记录的tenant、store、employee、applicant，防止跨作用域链接。无删除入口，历史只追加组关系。回退先回退应用，保留新增表，不删除已产生的申请关系。

时长沿用排班/所选起止时段毛时长（用户最终确认），不改成扣休息的净工时。额外显示明确的24小时制起止信息，避免上午12点与中午12点混淆；不改写原排班、打卡或历史核准。

## 整组撤回及销假扩展

同一PUT decision接口追加 `withdrawn`、`cancel_requested`、`cancelled`、`cancel_rejected`，不新增迁移或权限。

| 操作 | 调用方 | 当前需要包含的全部记录 | 结果 |
|---|---|---|---|
| withdrawn | 原申请人 | pending | withdrawn，立即撤回 |
| cancel_requested | 原申请人 | approved | cancel_requested，等待销假审核 |
| cancelled | 原有管理权限，禁止本人/原申请人审核 | cancel_requested | cancelled，销假生效 |
| cancel_rejected | 同上 | cancel_requested | approved，保留原批准 |

批准/拒绝申请仍包含全部pending项。混合状态组仅处理本次操作对应的状态，其他日期不改动；不得遗漏当前符合状态的记录。前端明确显示操作名称与条数，保留单天同等状态操作。除withdrawn外均要求非空说明，withdrawn允许空说明，最长500字。来源版本、每项重放、整批事务、拒绝越权和响应未知时冻结原请求的规则不变。
