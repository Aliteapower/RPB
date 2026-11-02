# 公开预约商业授权边界

公开预约属于 `reservation_queue` 产品。公开入口、context、新预约以及管理员公开预约配置均使用当前门店真实租户和门店调用 `AppGateService.evaluateCommercial`。平台状态、租户授权、授权期间、门店开关和店级订阅按共享 AppGate 规则判断，不维护另一份状态。

| 接口 | 商业授权拒绝 |
| --- | --- |
| `GET /api/v1/public/booking-entry` | 过滤未授权门店；零店沿用 404 `store_not_found`，多店仍沿用现有歧义错误 |
| `GET /api/v1/public/stores/{storeId}/booking/context` | 沿用 404 `store_not_found`，不返回可提交表单所需 context |
| `POST /api/v1/public/stores/{storeId}/booking/reservations` | 沿用 409 `booking_disabled`，不调用预约创建服务，包括旧幂等键重试 |
| `/api/v1/stores/{storeId}/tenant-admin/public-booking/**` | 先校验管理员身份/门店范围及真实门店归属，再校验商业授权；拒绝沿用 403 `FORBIDDEN` |

管理员配置 GET 也属于业务工作区，不作为历史接口。请求/响应 DTO、有效授权时的幂等处理及独立 `booking.enabled` 开关保持原有合同。有效商业授权不会自动开启管理员关闭的预约开关。顾客登录为可选时仍允许匿名预约；若携带可识别的顾客会话，租户必须与预约门店相同。

此次不修改本人预约历史、旧预约公开分享、取消等原单路径或其规则，不迁移或删除订阅、配置、预约数据。恢复授权后下一次请求自然恢复，不依赖修改预约配置。
