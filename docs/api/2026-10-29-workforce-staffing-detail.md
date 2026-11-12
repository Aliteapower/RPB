# 班次人数员工名单契约

GET /api/v1/stores/{storeId}/workforce/schedule-staffing/employees?from&to&shiftId&positionId&search&limit&offset。

from/to必填、含首尾最多31天；shiftId必填；positionId可选、search默认空、最长100；limit默认50且1..100，offset>=0。返回{items:[{employeeId,employeeNo,name,date,shiftName}],total,people,limit,offset}，total为员工/日期去重的人次，people为该区间去重员工人数。列表按日期、员工编号、员工ID稳定排序、独立分页；同员工同日同班多条记录只展示一行。

与staffing统计共用tenant/store/date/position/search及scheduled+deleted_at=null筛选。只返回当前门店，不暴露他店班次名称或员工资料。无匹配返回200空列表（含其他门店/租户shiftId），不扩大读取范围。保留原workforce.view、AppGate和管理门店权限；匿名401、未获管理授权/产品停用403、参数400，private/no-store。仅增加精确GET本地安全链路径，无写入、迁移或新权限。

周/月每日人数点击传单日，区间班次汇总点击传当前已应用区间；均沿用统计已应用的岗位和姓名条件，不使用未提交的筛选草稿。弹窗按日期/班次列出员工姓名和编号，显示最新人数及人次，可分页/关闭/Escape，无修改入口。查看名单期间暂停日历自动同步，关闭后原日期/筛选/滚动位置保持。0人每日格不提供入口；数据变化导致空名单时说明暂无有效排班。

测试清单：名单/统计口径一致、日期班次分组、员工日期去重、跨页完整、岗位姓名/字面通配符、取消删除排除、跨租户门店/员工角色/AppGate、API401/403/400与无缓存、本地安全链、前端单日/区间参数、独立分页、加载/空/失败/权限、关闭后不改变排班及同步暂停。
