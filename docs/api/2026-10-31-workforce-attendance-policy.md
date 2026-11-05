# 考勤策略 API（2026-10-31）

基础路径 `/api/v1/stores/{storeId}/workforce`。四种方式 `clock/default_normal/contract/free`，独立于定位方式。GET无副作用；所有命令PUT末尾id为全租户幂等commandId。

- GET `/attendance-policy-defaults` → `List<PolicyVersion>`。
- PUT `/attendance-policy-defaults/{id}`，`DefaultWrite(expectedVersion,effectiveFrom,mode,arrangement)` → `PolicyVersion`。
- GET `/attendance-policy` → 本人 `PolicyPage`。
- GET `/employees/{employeeId}/attendance-policy` → 授权管理者或本人 `PolicyPage`。
- PUT `/employees/{employeeId}/attendance-policies/{id}`，`AssignmentWrite(expectedVersion,effectiveFrom,adoptDefault,defaultId,mode,arrangement)` → `PolicyVersion`。

`Arrangement(weekdays:List<Integer>[1..7],startTime:LocalTime,standardWorkMinutes:int,unpaidBreakMinutes:int,unpaidBreakStartOffsetMinutes:Integer)`；净工作+休息<=1440分钟、净工作>0、休息>=0；休息>0必须指定从班次起点算的休息起始分钟偏移，偏移>=0且偏移+休息<=净工作+休息；休息=0时偏移须为空。时区从来源门店捕获，不接受客户端时区。clock/default_normal必须提供安排；contract/free安排可空，不产生虚拟出勤。
`PolicyVersion(id,employeeId,companyId,sourceStoreId,effectiveFrom,mode,origin,sourceDefaultId,sourceDefaultVersion,arrangement,timezone,version)`；origin为store_default/personal/adopted_default。
`PolicyPage(employeeId,canManage,today,assignments,effective)`；effective含mode/policyId/version/sourceStoreId/origin/arrangement/timezone/sourceReason。

采用默认必须指定该来源店的defaultId，并复制完整策略和安排快照，默认后来变化不会影响员工。解除个人覆盖也使用显式采用默认。首次采用固定sourceStoreId与日期，不使用可变main_store_id解释历史。员工历史严格追加，最早门店时区的明天、不得早于入职、不得倒排生效日；expectedVersion是该范围最新版本（空为0）。命令同租户/actor/store/employee/请求完全相同时重放原响应，否则409。

读写均调用workforce AppGate；默认仅管理者可读写。员工本人可读本人，管理者需管理员工全部任职门店；纯工资权限无权。写入依次source共享锁、策略范围锁、员工锁，重新核验归属；来源门店与员工公司须一致。400 WORKFORCE_INVALID，403 WORKFORCE_FORBIDDEN/原AppGate错误，404 WORKFORCE_NOT_FOUND，409 WORKFORCE_CONFLICT。

无员工新策略时旧工作方式free解析为free，其余clock；不自动采用门店默认。resolver.resolve(tenant,company,employee,date)和expected(tenant,company,employee,from,to)为服务接口。expected只为clock/default_normal生成安排区间，contract/free始终空，不代替实际打卡。

安全白名单仅上述精确GET/PUT模式。新模式整体启用门槛由上层链路集成控制；本地版本已与attendance/daily及新版工资来源接入，实际验证范围见开发验收记录；不据此声明生产已发布。

审查：api-review检查了方法、DTO、AppGate、重放、错误和白名单；database-review检查新增表tenant复合外键、范围唯一、追加版本与审计，不修改或删除旧数据。TDD清单：安排边界、历史快照、默认不自动采用、free回退、并发/重放、越权/AppGate/租户FK与白名单。
