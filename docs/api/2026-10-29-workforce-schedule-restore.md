# 取消排班恢复契约

沿用 PUT /api/v1/stores/{storeId}/workforce/schedule/{id}，同源写入、原AppGate/管理门店权限和租户范围不变。已取消记录以原employeeId、shiftId、workDate、当前expectedVersion和status=scheduled恢复；不得借恢复更换员工/日期/班次。

恢复沿用原id、时间、时区、班次休息规则和公司快照，仅更新状态与版本，新增schedule.restore审计，不删除旧记录/审计。校验今天至90天、有效任职/工作门店、启用班次、跨店/跨日时间重叠。冲突409 WORKFORCE_SCHEDULE_OVERLAP，版本变化409；已有关联出勤/待审补卡/有效请假继续409 WORKFORCE_ACTIVE_ATTENDANCE，禁止改动关联打卡。

周/月/列表使用schedule-board：同员工同门店同日存在有效排班时仅显示全部有效排班（允许多段非重叠班次）；无有效排班时只显示最近更新的取消记录（updated_at、created_at、id降序）。原数据库及明细查询保留全部历史。保存/恢复响应只刷新对应格子，并同步此显示规则。

恢复按钮在取消记录弹窗，结果未知保留原id/版本/请求并阻止关闭；重试后读取精确版本恢复原操作结果。恢复沿用原接口；删除所需迁移及安全链增量见下节。

测试清单：恢复保留快照与ID、重复/过期版本、同日已有/跨店重叠、已打卡数据不变、超期/停用/任职、权限及AppGate、列表显示最新且库中历史完整、前端恢复和错误/重试、周/月/单条/批量原回归。

## 未打卡记录删除

PUT /schedule/{id}/deletion，{expectedVersion}，返回{id,version,deleted:true}。允许有效或取消、但无关联出勤/待审补卡/有效请假的记录，沿用原管理权限和同源保护。逻辑删除标记cancelled并更新deleted_at、version、updated_at，审计schedule.delete；不删除行、不修改出勤。相同expectedVersion重试已成功删除返回同结果，不重复审计；其他版本409。已删除不可保存/恢复。

V094新增nullable deleted_at并约束仅cancelled可删除，原数据全部null。排班查询隐藏删除记录；最近取消记录删除后不重新露出更老的取消记录。原始历史查询与审计继续保留，复制仅取scheduled且未删除的源记录。无生产迁移或部署。
