# 按门店查看证件到期提醒

GET `/api/v1/stores/{storeId}/workforce/document-expiries?days=30&status=all&limit=50&offset=0`

- days为1..365整数，默认30；status为all/due/expired，默认all；limit为1..100，offset非负。
- 根据当前门店timezone和服务端Clock取today。expiresOn<today为expired，today至today+days（含）为due；今天到期不算已过期。空有效期、已删除证件排除；停用类型的历史证件保留。
- 沿用AppGate和employeeManage：当前门店须可管理；只返回当前门店有有效工作门店关系，且其全部当前工作门店均在操作者管理范围内的员工证件。不能使用门店筛选扩大权限。包含当前分配员工的证件，不自动因停用/离职状态删除提醒。
- 返回`{storeId,timezone,today,days,status,due,expired,total,limit,offset,items}`，items含documentId,employeeId,employeeNo,employeeName,typeName,expiresOn,daysRemaining,status。due/expired统计同一授权范围及天数，total按status筛选；排序expiresOn、employeeNo、employeeId、documentId，数据库分页，无100条静默截断。一次查询事务内使用一致快照。
- 提醒列表不返回证件号码、备注或附件内容。未登录401、无权限403、参数错误400，均private/no-store；不新增权限、迁移、定时任务或外部消息发送。
- “管理”页提供入口；可按授权门店切换，按状态/范围查询。点击提醒直接弹窗维护该证件，复用现有私有证件API、版本/上传限制及权限；保存、上传或删除后刷新当前提醒计数和列表，保留筛选。旧提醒已删除或无权时不可维护。

验证：日期边界/门店时区、不同天数、空/删除/停用类型、多门店分配去重、经理部分权限不泄露、员工/跨店/跨租户/AppGate拒绝、稳定分页和计数、无认证/错误参数/no-store、弹窗定位和保存刷新、权限/空/加载/失败状态。
