# 多天请假提交

`PUT /api/v1/stores/{storeId}/workforce/leave-batch`，沿用 workforce.view、本人身份、门店访问及同源写请求保护。

请求 `{from,to,items:[{id,expectedScheduleVersion,request:{scheduleId,typeId,startAt,endAt,reason}}]}`。日期含首尾，最多31天、1000个不同排班；每项使用固定UUID。客户端查询本人排班及日历生成预览，跳过无排班、店休、本人调休休息日和取消排班。公众假期有有效排班时不自动跳过。部分时段按各班开始后的相同偏移及时长预览，不裁切超界时段。

返回现有 `Leave[]`。每个排班独立生成 pending 申请，继续原审批流程。服务器在同一事务里锁定本人并核验归属、门店、日期范围、排班版本及时段；通过原 LeaveService 写入，任一失败整批回滚。新增入口不替代原单班接口，无迁移。

400 WORKFORCE_INVALID：空批、重复排班或UUID、范围/时段非法；403：身份、门店或AppGate拒绝；404：不可见排班；409 WORKFORCE_CONFLICT：排班版本变更或已有待审申请。沿用原业务错误及 no-store。固定每项UUID+相同内容重试，不新增重复申请；提交结果未知时界面冻结原请求，只允许重试。预览是客户端展示，提交仍以服务端当前数据为准。

测试覆盖多天原子提交与重试、第二项失败整体回滚、跨店/他人/跨租户、停用类型、版本变化、重复及范围校验、同源与匿名拒绝；前端覆盖跳过与合计、预览失效、快捷时长联动与选择标识。

`GET /leave-batch?from=YYYY-MM-DD&to=YYYY-MM-DD` 返回本人 SchedulePage，始终按本人过滤（包括拥有管理权限的员工），最多1000项，超限拒绝而不静默截断；不返回其他员工目录。用于范围匹配，其他读取复用原日历、类型和请假接口。
