# 整周批量复制契约

根/api/v1/stores/{storeId}/workforce，沿用workforce.view、AppGate和原管理门店权限、同源PUT保护、private/no-store。

- PUT /schedule-copy-bulk/preview：{sourceWeek,targetWeek}，均须周一且不同。复制全门店有效源排班，不受岗位/姓名筛选影响；最多5000条、1000名员工，不静默截断。返回{fingerprint,sourceWeek,targetWeek,employeeCount,total,ready,existing,conflicts,timezone,items:[{sourceId,employeeId,employeeNo,employeeName,sourceDate,date,shiftName,startAt,endAt,status,reason}]}。
- 同店员工目标日已排班标记existing并跳过；已取消历史不阻止新排班。任职/工作门店不适用、目标日期超出今天至90天、班次停用、夏令时无效、跨店/相邻日期时间重叠、本批候选相互重叠标记conflict。仅ready可新增；不覆盖或取消旧记录。按当前启用班次模板生成，源历史快照不变。其他门店详情不暴露。
- PUT /schedule-copy-bulk/{commandId}：{request:{sourceWeek,targetWeek},fingerprint}。提交再次验证原权限并核对完整来源指纹；变化409 WORKFORCE_COPY_CHANGED，无写入。仅新增ready项，已有及冲突跳过，无ready400。返回沿用批量Result {commandId,created,existing,conflicts,scheduleIds}。
- 复用现有批量回执表，不新增迁移；操作类型、操作者、租户、门店和规范化请求绑定。UUID重放相同请求返回原回执，不同请求或操作者409。新增、回执、审计单事务，失败整体回滚。
- 排班写入统一先取门店规划共享锁；整周复制提交取对应独占锁，防止预览重验后的源集合变化。再按固定顺序取命令、门店时区、班次及员工锁。其他门店写入仍通过员工锁处理交叉重叠。

保留旧/schedule-copy/{id}兼容接口及其旧语义；排班页入口改用新预览/批量提交，不再使用100条限制。周/月/列表、批量新建及原数据保留。

错误：401匿名、403权限/AppGate、400参数或容量上限（WORKFORCE_COPY_TOO_LARGE）、409预览变化或幂等冲突。前端先展示员工、源/目标日、班次、时间及逐条原因；明确确认新增/跳过数。结果未知保留同UUID及请求重试，禁止关闭；成功后刷新并展示目标周。目标周一当天至周日为完整目标周，按星期一一对应；如11月2日为11月2日至8日。成功后以目标周为新的来源周，下一次目标自动加7天，依次复制。

必要验证：源只读、已有目标跳过、取消历史保留、跨店/候选重叠、班次停用、预览失效、并发重复/源新增、审计失败回滚、租户/门店/AppGate、5000条与几百名员工、旧接口回归、UI预览/重试/成功刷新。
