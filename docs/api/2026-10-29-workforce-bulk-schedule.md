# 批量排班与周表契约

根 /api/v1/stores/{storeId}/workforce；原workforce.view、AppGate、store访问及employeeManage；无新增权限。所有响应private/no-store，PUT沿用X-RPB-Workforce:1及同源保护。

- GET /schedule-bulk/options?positionId=&search=&limit=50&offset=0：授权当前门店在职员工分页及总数，岗位/姓名编号筛选；返回{items:[{id,employeeNo,name,positionId}],total,limit,offset,positions,shifts,timezone}。岗位目录只读，班次仅当前门店。
- PUT /schedule-bulk/preview：请求{employeeIds,shiftId,from,to,weekdays:[1..7]}。UUID去重排序，重复项可归一；最多500人、31个自然日、5000候选行，沿用今天至90天的排班窗口。返回{fingerprint,employeeCount,total,ready,existing,conflicts,items:[{employeeId,employeeNo,employeeName,date,status,reason}],timezone}。status=ready/existing/conflict；同店同日原有scheduled→existing，跨店时间重叠/失效工作门店或雇佣日期→conflict。预览不写排班。
- PUT /schedule-bulk/{commandId}：请求{request:{...预览请求},fingerprint}。原权限重新检查；锁门店时区来源、班次与全部员工，服务器重算指纹，变化409 WORKFORCE_BULK_CHANGED且无写入。仅新增ready行，已有/冲突跳过，回执{commandId,created,existing,conflicts,scheduleIds}；前端必须清楚显示跳过数量后确认。没有ready行400。回执和审计与新增同事务。相同UUID、相同请求/指纹/操作者重放原回执；任一不同409 WORKFORCE_CONFLICT。重放仍须当前门店管理授权。
- GET /schedule-board?from=周一&positionId=&search=&limit=50&offset=0：返回{employees:[...基础员工],total,limit,offset,items:[原Schedule],timezone,weekScheduleCount,positions}，涵盖该页员工当周排班；员工范围包括门店当前在职员工及当周原有排班员工（即使已离职），不跨店泄露。上限100人/页。weekScheduleCount为全门店当周scheduled总条数，维持旧整周复制上限判断。

错误：匿名401；AppGate或管理权限403；未知/越界选择404；参数或超限400 WORKFORCE_INVALID；预览变化409 WORKFORCE_BULK_CHANGED；幂等冲突409 WORKFORCE_CONFLICT。无旧接口语义变化。

V093新增租户/门店/操作者复合FK的批量命令回执表，命令主键、请求SHA256、JSON结果、创建时间；不更新旧排班/工资/付款。回退保留回执及所有业务记录，关闭新入口或回退应用；不降级删表。


## 2026-10-29 月表与弹窗扩展

GET /schedule-board兼容原周查询；新增可选to。省略to时仍要求from为周一；指定to时要求from为自然月1日且to为同月最后一天，最多31天，否则400。月表的员工筛选、分页及历史排班归属以整月范围统一计算，不拼接各周分页；仍按原租户、门店和管理权限校验。

Board新增periodScheduleCount，表示整个查询期间有效排班数；weekScheduleCount保留原语义，为from所在周的有效排班数。其他返回结构及原接口不变，无数据库迁移。

批量弹窗沿用原options、preview和提交契约，成功关闭并刷新当前周表/月表/列表，保留范围、筛选和分页。门店筛选在弹窗内使用该门店的API，不改变周表URL，切店清空选择和预览；提交结果未知时保留原UUID和请求，禁止关闭。

复制沿用原接口、原100条限制、权限与冲突整批撤销规则。周视图默认目标为当前查询周的下一周；成功后展示目标周，不覆盖旧数据。月表不把整月作为一周复制，复制入口在周范围下显示。
