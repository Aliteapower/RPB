# 门店地址、定位校测与商业历史接口

## 现有身份

所有新增配置继续使用stores.id与tenant_id，不创建门店。地址使用stores.share_address，不新增重复地址来源。原clock-site/clock接口与历史ClockPolicy JSON不变。

## /api/v1/stores/{storeId}/workforce/clock-site/settings

GET返回Settings={storeId,storeCode,storeName,operatingEntityId,operatingEntityName,address,storeVersion,point,version,canEditAddress}。point和version沿用原门店定位点；version=0表示尚未保存。AppGate workforce+本门店员工管理范围。

PUT Write={expectedStoreVersion,expectedVersion,address,point}，两版本非负，point按原规则名称/坐标/10..1000米；address可空，最多500字。修改地址额外需要tenant.admin.manage（不能靠员工管理权限修改公共地址）；地址不变时允许单独维护点。以原来源共享锁→站点锁→门店行锁执行，版本冲突409，同内容重试不重复审计。地址与定位同事务，不改主体负责人、租户Logo、门店身份或计费数量。保持原private/no-store、同源写头及稳定错误。

## PUT /api/v1/stores/{storeId}/workforce/clock-site/check

CheckWrite={expectedVersion,point,location}，point为空时检查已保存点，否则检查草稿；expectedVersion若提供需与当前站点一致。仅上述管理员范围，纯只读。

CheckResult={distanceMeters,radiusMeters,accuracyMeters,allowed,code,siteVersion,checkedAt}。通过code为空。缺位置/精度差/过期/范围外以allowed=false和原WORKFORCE_LOCATION_*返回正常200以显示测量；非法坐标400、缺门店点409、版本变化409、越权403。距离与判定复用WorkModeRules，不存测试位置，不创建打卡。

## 平台商业历史

GET /api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/events，仅平台管理员，private/no-store；返回{events:[{id,eventType,billingCycle,status,periodStart,periodEnd,amount,currency,paymentNote,createdAt,snapshotAvailable,stores}]}。tenant和subscription必须匹配，不向租户员工开放。

新event_payload保留原字段并追加storeSnapshotVersion=1,storeSnapshots=[{storeId,storeCode,storeName,operatingEntityId,operatingEntityName,storeStatus,itemStatus,quantity,unitAmount,amount,currency,periodStart,periodEnd}]；stores即该快照数组。它表示商业操作后的订阅项目，不宣称为银行实收凭证。旧记录缺少快照时snapshotAvailable=false且stores=[]，不使用当前主档回填历史。

停用/恢复不改旧购买事件，当前汇总只取active且未删门店的active项目。关闭产品功能不是退费。无新的自动扣款/退款接口。
