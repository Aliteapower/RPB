# 名单多选批量调整当天班次

PUT /api/v1/stores/{storeId}/workforce/schedule-adjustments/preview，{date,sourceShiftId,targetShiftId,targets:[{scheduleId,expectedVersion}]}。1..500条、ID去重、版本>0；同一天同一来源班次、每员工只能一条。预览返回fingerprint/date/目标班次/时区、total/ready/unchanged/conflicts及员工、日期、原班次时间、目标时间、状态和原因。

保留原管理门店权限/workforce.view/AppGate、同源写入和private/no-store。租户外ID404，他店ID403（不得泄露其姓名/班次）。来源版本/日期/班次/状态变化、软删除、已有关联打卡/补卡/请假、任职/工作门店不适用、超出今天至90天、目标时段与其他记录跨店跨日重叠，均为conflict；同日同班多段请单独调整；同目标班次unchanged，不重写历史快照。只读预览不写入。

PUT /schedule-adjustments/{commandId}，{request:上述请求,fingerprint}。权限重新检查后加原门店规划共享锁、通用批量命令锁、门店时区共享锁、目标班次锁、按顺序员工和源排班锁；重新预览，fingerprint变化409 WORKFORCE_ADJUST_CHANGED且无写入。仅更新ready；冲突及unchanged跳过。复用ScheduleService.save原ID/日期/员工与expectedVersion，原打卡保护、冲突及逐条审计不变。结果{commandId,updated,unchanged,conflicts,scheduleIds}，无ready400。

复用V093回执表，操作类型前缀、请求/指纹、actor/tenant/store绑定，跨类型或跨店/操作者重放409。相同命令请求重放原结果；未知结果保留相同UUID/请求并阻止关闭。每行保存、批量审计和回执同事务，失败全部回滚；不新增迁移或权限。

名单schedules增量返回version。复选框仅单段且有版本者可选，选择限定日期，跨页保留；支持选择本页及当前日期全部（最多500）和清空。多段提示单独调整。先选目标启用班次、预览全部选中结果，明确确认更新/跳过数；成功清空选择并同步刷新名单、人数及排班表，保留已应用筛选。选择日期/目标变化必须重新预览，不能默默覆盖其他记录。

测试：预览只读、多人原ID更新、部分冲突/同班跳过、打卡保护、跨店/租户/经理/AppGate、版本和目标班次/占用变化、并发/重放/跨类型回执、审计回滚、多段、500人边界、前端跨页选择/同日约束/预览确认/未知重试/关闭保护/三个视图刷新。
