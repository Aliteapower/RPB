# 恢复、删除与排班表整理本地验证

2026-10-29，D:\RPB，暂不部署。按用户连续补充的要求：取消记录可恢复；无关联打卡的有效或取消记录可删除；复制到下周不包含取消记录；保留原权限、数据和既有打卡。

## 实现与排查

原周/月/列表直接展示全部取消历史，造成同日有效和取消卡片重复；取消记录编辑弹窗没有恢复入口。ScheduleService增加恢复分支，原ID原快照，仅改状态/版本并记录schedule.restore审计；时间、休息、公司、时区快照保持原值，不采用后来修改的模板内容。恢复检查工作日期、任职/门店、启用模板及跨店时间重叠；有任何出勤、待审补卡或有效请假关联仍拒绝。

删除采用独立ScheduleDeletionService/Controller/DTO，PUT /schedule/{id}/deletion，expectedVersion必填。只允许没有上述关联的记录，逻辑标记cancelled及deleted_at，版本递增，写schedule.delete审计；相同版本重试返回同结果。保留物理行和原ID，不改打卡数据。服务沿用AppGate/租户/原管理门店范围，匿名401、普通员工403、版本/关联409，private/no-store与同源保护；本地安全链只新增精确PUT路径。

schedule-board同员工同店同日优先展示全部有效排班，隐藏旧取消卡片；无有效项时只展示最近取消记录。最近取消记录删除后不重新露出更老历史。明细和数据库保留历史；保存/恢复/删除成功只更新对应格子及计数，周表/月表/列表一致。普通schedule列表隐藏删除项。整周复制新旧接口均仅取scheduled且未删除的记录。

## Database Review

V094__workforce_schedule_soft_delete.sql新增nullable deleted_at和约束deleted_at非空时status必须为cancelled。默认null，未更改原字段、外键、租户/门店归属。所有删除UPDATE含tenant_id、store_id、id、version；不级联、不物理删除。沿用门店规划共享锁及员工/排班锁，和打卡共享员工锁串行化。

先读target/local-postgres-current.txt核对D:\RPB所有权、PG61644；仅将V094应用到workforce_browser_6b3d0ca974744b0c9b681036f8462dd6，保留原20条排班字段（仅新增deleted_at=null）。原员工、班次、工作门店、工资批次、付款、账号摘要不变；原始打卡事件、出勤、补卡、请假摘要不变。没有生产迁移或部署。

## TDD / 验证

- 首轮新增恢复/显示测试在旧实现下2项失败，前端无恢复按钮导致2项失败；实现后通过。
- 最终前端55项通过，包含恢复原ID/字段、冲突不关闭、删除成功移除、删除未知结果同版本重试/关闭保护、已打卡删除拒绝、有效计数递减，以及旧排班操作回归。vue-tsc/Vite构建通过。
- 出勤/排班相关36项通过（包含基础继承测试）：恢复原快照、跨店/同店冲突、原打卡不变、过期/停用/权限拒绝、审计失败回滚、软删除/重放、不复活旧取消卡片、AppGate拒绝、删除与打卡并发竞争。
- 整周复制20项、原批量排班22项通过。新增取消源记录不进入下一周预览或提交测试通过。
- 删除API2项、本地安全链1项通过。合计81项后端场景通过；首轮两个测试替身重新设桩触发旧answer，修正为doAnswer/doThrow后定向复验，并新增并发测试通过。生产代码未因测试替身问题改变；同一生产代码状态的其他已通过回归未重复全量执行。
- 实际API5项通过：旧取消卡片隐藏，恢复冲突409，已有打卡删除409，员工403，取消记录不在复制来源。

证据：target/workforce-restore-20261029/frontend.log、backend-final.log、attendance-full.xml、backend-recheck.log、api.json、migration.json、data-before.json/data-after.json、clock-before.json/clock-after.json。

## 实际页面与UI Review

仅在本地经理身份验证，保留用户当前页，另开后台测试页。11月22日新建→取消→恢复成功，原ID不变、快照不变、版本1→2→3，仅一张有效卡片；删除有效未打卡记录后版本4，卡片消失，空白格可新增，物理行仍保留。11月8日本轮测试记录也已逻辑删除。原20条排班未修改。后台测试页已关闭。

桌面截图：restore-delete-actions.png、clean-roster.png、restored-cell.png、deleted-cell.png（均在上述target证据目录）。恢复/删除入口在原弹窗，保留错误/加载/权限状态，未知结果阻止关闭并重试原请求。按钮沿用原flex-wrap，未调整用户正在操作的浏览器全局视口。

## Code Review

无未解决P0/P1：Controller只调用应用服务；没有跨租户SQL或新增权限；恢复/删除和打卡通过同一员工锁串行；删除和审计同事务；软删除记录不能再次保存/恢复；复制条件排除取消和删除状态。原工时/工资/付款及其他产品线代码未改。API契约与实现一致。结论：本地实现通过。

## 交付边界与风险

未提交、推送或部署；已有未提交改动保留。关联出勤/待审补卡/有效请假时禁止删除；已删除项不提供恢复，取消项才提供恢复。恢复只限今天至90天且原班次须启用。

V094是增量列，回退应用时保留列和历史，不执行DROP或物理删除。旧版本不认识deleted_at，可能重新展示隐藏项；故不能将旧版本UI显示与新版等同。当前之前运行JAR备份为target/workforce-restore-20261029/backend-before.jar，本地新后端只替换8096，未改其他运行环境。
