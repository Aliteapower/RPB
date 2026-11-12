# 本店待审核申请与酒妹/保管员（2026-10-25）

## API
- GET `/api/v1/stores/{storeId}/wine-storage/pending-applications`：wine_storage.view + receive，返回 `{application,customer:{id,displayName,phone}}[]`，仅本店 pending，按提交时间最早优先，最多100项，核实后刷新补齐。无需前端先查顾客。商业停用时可查看，实际收存仍走原 App Gate。顾客只属于申请的 customer_id，不接受前端另指定顾客。
- GET `/.../custodians`：wine_storage.view，默认只返回启用人员 `{id,name,kind,enabled,version}`。`includeDisabled=true` 仅管理员及 wine_storage.custodians.manage 权限可读。
- POST `/.../custodians`，POST `/.../custodians/{id}`：tenant_admin / store_manager 且 wine_storage.custodians.manage；请求 `{name,kind:'hostess'|'custodian',enabled,version,idempotencyKey}`。新增 version=null，更新需要当前版本；返回人员 DTO。沿用租户/门店授权。管理非交易不要求商业开启；实际收存仍要求开启。
- 原 Receive 与 Registration 请求增加可空 `custodianId`，省略/null 保持原行为；不接受客户提交该字段分配人员。服务端验证当前门店启用人员，将 `{id,name,kind}` 冻结到每项 Snapshot.custodian；确认记录 Receive.custodianId 保留关联。新命名/停用不改旧快照。null 历史安全兼容，不增加数量或改变期限。

## 数据库 / 权限
V078 新建 wine_storage_custodians（tenant/store复合范围、名称、类型、启用、乐观版本、操作者/时间）及管理审计；名单停用不删除。复合FK与启用名称唯一索引；新增权限只授 tenant_admin / store_manager，同步新账号默认权限集合。对原应用/items结构无变更，只增加JSON可空字段，旧内容可读。

## 错误与幂等
沿用 STORAGE_INVALID400 / FORBIDDEN403 / NOT_FOUND404 / CONFLICT409。人员更新版本冲突409，重复名称409。管理命令复用租户/门店/操作者/staff/custodian.write/幂等键，同请求重放，不同请求409。收存幂等重放不受之后人员停用影响。人员选择与收存同事务，选择时共享锁，停用/改名与之互斥。

## 测试清单
- 无选顾客直接显示并审核待办；顾客/门店对应；无权限不可查看；并发已处理仍被原 receive 拒绝。
- 管理员增改停用、重放、乐观冲突、普通员工拒绝；跨店/跨租户人员和停用人员拒绝。
- 收存可不填人员；代登记和审核都可选；收存照片、规则、原交易校验不变；快照不随改名/停用变化。
- 前端角色/权限、中英、忙碌、空态、保存失败、历史姓名；钱包全量与存酒回归。

## 数量与原申请图文补充

用户确认无需顾客再次授权：商户核对实物时可改实收数量，原申请行保持不变；确认收存后顾客记录同时展示原申请总瓶数、实际收存总瓶数、核实明细和说明。复用原 Receive 命令和原快照/幂等语义，不新增审批状态。新可空 custodianId 在为空时不序列化，保持旧客户端未确定命令的规范请求哈希兼容。

原申请说明按原文显示；照片点缩略图打开页内大图，提供继续放大/适合屏幕/原图，支持 Escape 与焦点返回。图像仍经原鉴权照片API读取，不改变照片内容与归属。
