# 员工调休安排

基址 `/api/v1/stores/{storeId}/workforce`，沿用AppGate、门店管理权限、同源写保护及 `private, no-store`。无新角色或授权。

- PUT `/rest-swaps/preview`：`{employeeIds,restDate,makeupDate,shiftId}`，1..500员工，日期不同且相差最多30日，均在本店今天至未来90日。休息日期必须已有启用店休/公共假期；补班日不可为店休；班次必须属于当前店且启用。
- 返回 `{fingerprint,ready,conflicts,cancellations,items,timezone}`。items含employeeId/employeeNo/employeeName/status/reason/cancelSchedules。无预约写入。休息日当前有效排班只在无关联记录时列为待取消，预览清楚展示取消数。
- PUT `/rest-swaps/{commandId}`：`{request,fingerprint}`，只提交ready员工，冲突跳过。事务内取消原排班、创建补班、保存每人关联记录及审计，失败整体回滚。原排班保留取消状态，不删除。先取source共享锁、planning共享锁、命令锁、门店行/日历锁、班次锁、排序后的员工锁；重取预览，源变动409要求重新核对。
- 相同命令ID、同员工/门店/请求重试使用原回执；改变请求或越权重放拒绝。未知网络/500结果使用原请求重试。

冲突包括：已参与另一调休的任一日期、两日不符合任职/门店分配、关联打卡/有效请假/待补卡、补班日已有排班、休息日或补班时段与未取消的其他排班（包括其他门店）重叠。跨店冲突仅显示通用原因，不泄露其他门店员工或班次信息。

调休明细按tenant/store/employee隔离，记录两个日期、取消ID、新排班ID。公共假期不抵扣，原核准calendar源和工资公式未增加人员调休标记。

GET `/calendar`追加 `restSwaps`（管理员/经理当前门店，员工仅本人）；排班board追加当前员工页的restSwaps，周/月/列表显示关联日期。补班排班取消、删除、改期或休息日又出现有效排班时投影异常状态，提示维护者核对，原关联不改写。

UI：管理→考勤设置→安排调休，默认全选筛选范围内的本店在职启用员工，可逐人取消。每批最多500人，超过时要求按岗位/姓名缩小范围；筛选会重新全选匹配员工。预览取消数、参与数与冲突原因后显式确认。
