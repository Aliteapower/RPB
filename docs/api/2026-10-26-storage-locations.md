# 常用存放位置与仓位

## 业务与 API

位置清单按 tenant/store 隔离，由具有 `wine_storage.rules.manage` 权限的租户管理员维护；普通有 `wine_storage.view` 权限的员工只读选用。维护属于新业务配置，必须通过正常 App Gate，关闭/到期后不可维护。

- `GET /api/v1/stores/{storeId}/wine-storage/locations` → `[{id,name,defaultLocation,version}]`。只读，不创建配置。未配置时返回本店稳定默认 ID 的“前台寄存处”。
- `POST /locations` → 新建，body `{name,version:null,idempotencyKey}`。
- `POST /locations/{id}` → 修改，body `{name,version,idempotencyKey}`。默认位置同样可改名，未持久化的默认位置第一次修改须 version=0。
- 名称去首尾空白后 1..100 字，不得与本店其他位置忽略大小写重复。无删除接口；新增与修改沿用 StorageCommands 幂等和审计，版本冲突 409，越权 403，不存在/跨店 ID 404，格式错误 400。
- 收存 ReceiptLine 兼容新增可选 `storageLocationId`；原 `location` 作为可留空的具体仓位。null 表示不指定位置，旧客户端省略字段维持原行为。新 UI 默认选择本店默认位置，也允许主动选“不指定”。
- 收存返回与物品 snapshot 冻结 `{storageLocation:{id,name}}`；客户端传入的显示名称不被信任。选用 ID 必须属于当前 tenant/store。重命名与收存通过同一门店位置锁串行化，后续改名不改写已收存或旧确认单。
- 原历史 location 字符串不转换、不迁移、不覆盖；旧快照缺少新字段时继续显示原值。

## 数据库审查

V081 新增位置与修改审计两表，包含 tenant_id/store_id 复合 FK、范围内名称唯一、每店唯一默认行、版本非负、时间及操作者。默认读取不写库，不为未启用门店批量建业务记录；首次修改默认位置才落库。无已有业务表结构或数据修改，不新增权限名。

## 测试清单

默认读取零写入、选填位置/空仓位、已有历史兼容；新增/改名/同键重放/旧版本/同名；租户管理员与普通员工边界、关闭 gate、跨店/租户；收存冻结名称并在后续改名后不变；直接登记与审核收存均保留选定 ID；前端默认、主动清空、仓位留空、管理 CRUD、切店晚响应及中英文显示。
