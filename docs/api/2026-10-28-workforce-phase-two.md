# Workforce 第二阶段API/数据库合同

基址 /api/v1/stores/{storeId}/workforce，全部GET/PUT沿用现有会话、AppGate、X-RPB-Workforce:1写保护及private,no-store。所有tenant来自认证身份。

|方法路径|合同|
|---|---|
|GET /shifts|门店管理员列表（含停用），员工只通过排班快照获知班次|
|PUT /shifts/{id}|ShiftWrite(expectedVersion,name,startTime,endTime,nextDay,breakMode,breakMinutes,breakAfterMinutes,breakStartOffset,breakEndOffset,breakPaid,enabled)|
|GET /schedule?from&to|最多31天；管理员当前店，员工本人；返回Schedule及EmployeeOption|
|PUT /schedule/{id}|ScheduleWrite(expectedVersion,employeeId,shiftId,workDate,status=scheduled/cancelled)|
|PUT /schedule-copy/{commandId}|CopyWrite(sourceWeek,targetWeek)，周一、最多100条、原子/幂等|
|GET /clock|本人当前店当日/跨夜可用排班、全店范围本人活动session（只含所属门店及本人数据）、允许动作|
|PUT /clock/{commandId}|ClockWrite(scheduleId,action=IN/BREAK_START/BREAK_END/OUT)；服务端时间；同命令同参数返回原状态|
|GET /attendance?from&to&employeeId&limit&offset|按排班工作日、最多31天、分页<=100；普通员工忽略筛选并限定本人，管理员仅本店|
|GET /attendance/{scheduleId}|计划、有效结果、原始事件及补卡历史；无session也能显示缺卡|
|PUT /corrections/{id}|CorrectionWrite(scheduleId,basedSessionVersion,inAt,outAt,breaks:[{start,end}],overrideBreak,reason)|
|GET /corrections?from&to|本店申请；员工仅本人，管理员全部|
|PUT /corrections/{id}/decision|DecisionWrite(expectedVersion,status=approved/rejected/withdrawn,note)；withdrawn仅申请人，approve/reject仅管理员且非本人|

日期ISO8601，时间HH:mm，Instant含Z/偏移。JSON快照字段后端生成。shift/schedule更新409强制刷新；create UUID同内容重试返回现状，不同内容409。原始clock命令和copy命令有持久化回放，不使用浏览器本地时间。

错误沿用WORKFORCE_INVALID(400)、FORBIDDEN(403)、NOT_FOUND(404)、CONFLICT(409)、PERSISTENCE_ERROR(500)，新增WORKFORCE_CLOCK_STATE(409)、WORKFORCE_SCHEDULE_OVERLAP(409)、WORKFORCE_ATTENDANCE_OVERLAP(409)、WORKFORCE_PENDING_CORRECTION(409)、WORKFORCE_ACTIVE_ATTENDANCE(409)。不向客户端输出SQL或其他店人员资料。

Home增量增加clockStores，仅本人工作分配与Auth门店访问交集；原stores管理范围不变。审批approved/rejected需非空意见，withdrawn可空意见。所有补卡Instant先规范化到微秒后比较/入库，以保持同内容重试；clock在无活动记录时返回最近本人本店出勤供下班回看。

数据库：workforce_shifts、workforce_schedules、workforce_attendance_sessions、workforce_clock_events、workforce_corrections、workforce_schedule_copies。scope复合外键；scheduled interval唯一逻辑在员工锁事务检查；active session按(tenant_id,employee_id)部分唯一；clock id按tenant唯一及动作请求一致校验；pending correction按schedule唯一。原始event只insert，correction decision追加于申请字段及独立Audit；projection可更新且version递增。event保存响应session快照使历史命令重放不执行新动作。

评审：现有API不变；不改变旧产品权限/订阅；所有写命令与审计同事务；审批锁同一员工再检查版本/重叠；查询scope不依赖客户端employeeId。迁移只向新表添加，无原表业务回填；应用回退保留表及事实，不以删数据回滚。真实PG在pointer实例独立schema验证。
