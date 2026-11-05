# 商户直接代登记收存 API（2026-10-25）

POST `/api/v1/stores/{storeId}/wine-storage/registrations`

请求：`customerId`、`lines: ReceiptLine[]`、`reason`、`rulesConfirmed: true`、`idempotencyKey`。lineId 由服务端生成，忽略客户端值。1–50 行，照片使用当前顾客的未使用 receipt 照片。商户确认已向顾客说明保存规则、获同意且收到实物；不冒充顾客自助申请或签署。

权限：wine_storage.receive + 原 StorageAccess staff / App Gate 新业务检查；同租户有效顾客、本店产品和当前分类规则。沿用原收存校验、期限和照片权限。

响应：沿用 Application，status=stored。记录原始登记行（无虚构顾客申请照片，rulesAccepted=false）、收存快照、staff.registered 审计（actor=当前商户操作人，rulesConfirmed=true），received 审计。登记、关联照片及收存同事务，任何失败全部回滚。原顾客申请和商户收存 API 不变。

幂等：按租户/门店/操作人/staff/register/idempotencyKey 和完整请求重放；同键同请求返回原记录，不增加库存；同键异请求沿用 STORAGE_CONFLICT。失败不残留待收存申请。错误沿用 StorageException：参数400、无权限403、不存在404、状态/照片/幂等冲突409。

无需迁移：复用 applications、application_lines、items、events、commands，均沿用 tenant_id/store_id 边界。测试覆盖成功、重放、不同请求冲突、缺少确认、照片/规则失败回滚、跨租户顾客和权限/App Gate 拒绝。
