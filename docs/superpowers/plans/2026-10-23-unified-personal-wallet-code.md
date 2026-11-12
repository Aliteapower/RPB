# 统一个人钱包码实施计划

> 执行技能：subagent-driven-development；用户已提供完整规格并授权开发测试，任何任务均不提交、不推送、不部署，不增加方案审批步骤。

**Goal:** 一个个人钱包码支持付款、充值及积分，已同意限额内消费由商户输入金额后扫码直接完成。

**Architecture:** 复用 WI 身份码，在独立个人码付款模块中维护限额策略、顾客同意和单次付款绑定。现有资金分摊、账单、逐笔确认、幂等及审计模块承担实际交易；身份读取不消耗付款，充值积分沿用现有入口。

**Tech Stack:** Java/Spring/PostgreSQL/Flyway、Vue/TypeScript。当前分支 codex/paynow-payment-product-line-staging，基线 f7f889e1；本地 PostgreSQL 按 target/local-postgres-current.txt，当前端口61644。

**Spec:** 本轮用户完整执行规格为需求来源。默认限额没有历史约定，设为 null/待配置，绝不默认无限额。未配置时身份功能正常，扫码付款不可启用。

## Global Constraints

- 保留已有未跟踪文档和私人文件，不操作线上，不提交推送。
- 顾客端只显示“出示个人钱包码”，说明“此码可用于钱包付款、充值及积分办理，请当面出示。”
- 付款必须携带本次有效码，不能复用选客；同一授权最多支付一笔。身份、充值、积分不消耗付款授权。
- 单笔消费必须全额有足够可用余额，失败不产生错误资金流水；超限只创建待明确确认账单，不扣款。
- 提高限额要求顾客重新同意，旧码不能升级授权；降低限额立即生效。
- 不记录明文令牌，保留旧接口兼容但旧直接扫码付款不能绕过新限额同意。
- 单档营销、收款确认、本金/赠送分摊和积分独立规则保持。

## API Contract

- GET/PUT /api/v1/stores/{storeId}/wallet/personal-payment-policy：{limitCents:long|null,version:int,idempotencyKey?}，管理员 wallet.settings.manage；修改需版本和幂等，null 关闭免确认启用。
- GET /api/v1/public/stores/{storeId}/wallet/personal-payment-consent：{configured,policyVersion,limitCents,consentedLimitCents,paymentEnabled,requiresConsent,ruleVersion}。
- POST 同路径：{policyVersion,limitCents,accepted:boolean,idempotencyKey}，明确同意当前金额和规则版本，accepted=false 关闭付款；不自动开资金账户。
- POST /api/v1/public/stores/{storeId}/wallet/personal-codes {}：{type:'customer_personal',code,expiresAt,paymentEnabled,limitCents,consentedLimitCents,policyVersion}，60秒有效，刷新撤销旧码，不要求资金账户。
- POST /api/v1/public/stores/{storeId}/wallet/personal-codes/status {code}：{state:'active'|'expired'|'used'|'confirmation_required',bill?}，仅本人查询，成功后前台更新码并显示收据；超限显示原账单确认入口。
- POST /api/v1/stores/{storeId}/wallet/personal-scan-charges {customerCode,amount,description,idempotencyKey}：{status:'paid'|'confirmation_required',bill,account?}。员工同时需 wallet.charge.create / wallet.collection.confirm。从令牌推导顾客/账户，不信任传入顾客或账户。限额内原子全额支付，超限只建立一笔待顾客确认的原账单。
- GET /api/v1/stores/{storeId}/wallet/personal-scan-charges/result?idempotencyKey=...：同员工/门店查询原结果，未记录返回404，重试使用原请求编号和内容；码以哈希参与幂等而不持久化明文。
- 旧 customer-codes、WC/WP、付款确认接口保留；WI 识别保持只读。新路径严格同源标记、模型版本、private/no-store。非法400、未登录401、越权403、过期/已用/余额不足/需开通/未配策略409（稳定业务码），不存在404。

## Database / rollback

V064 增加策略和同意表（tenant/program/customer复合外键、金额边界、版本、更新时间及审计），扩展 WI 付款快照与账单绑定字段及适当唯一性约束。身份仓储继续只负责识别，新个人码仓储负责授权状态；资金和积分表不改写。政策初始null、同意不存在，旧接口明确确认付款仍可用。回退须同时回退前后端，新增表/列可保留，不回滚账务数据。任何实际发布另行授权。

## Tasks / tests

- [x] 后端：政策/同意/统一码/支付与查询模块及迁移，关键PostgreSQL/API测试先于或伴随实现。覆盖正常、重复/并发、过期/刷新/已用、超限、无余额/停用、越权/跨租户/发行主体、调高限额重新同意、身份不消耗、敏感令牌不入日志。
- [x] 前端：统一顾客出码与启用规则、限额展示/管理员设置；消费金额说明优先、扫码立即提交、未知结果先查原交易；充值积分状态隔离；补组件行为测试。
- [x] 联调：本地独立schema+端口，不停现有运行环境。图片识别跑通消费、单次授权、充值、积分、超限确认、原交易重试和双方记录。相机验证单独报告，不将图片识别等同真机。
- [x] 审查：API/数据库/TDD/代码与发布说明，回归营销、资金分摊、退款及相关权限，报告待配置限额与剩余风险。

实施补充：新增 POST /personal-scan-charges/reconcile，通过与原命令同锁的不可执行结果封存解决刷新后未决请求。只有返回 not_submitted 才允许重新扫码，晚到的旧请求被稳定拒绝。完整接口见 docs/api/2026-10-23-wallet-personal-payment-contract.md；验收见 docs/development/UNIFIED_PERSONAL_WALLET_CODE_ACCEPTANCE_20261023.md。

## Review Focus

- 码刷新与扣款并发锁顺序、时间必须在锁后校验。
- 同意或策略变化与扫码事务并发不得升级旧授权。
- 超限同一授权只产生一个待确认账单，明确确认仍校验所有者与确定金额。
- 网络异常时持久保留请求编号，查询原结果成功后不能再扣第二笔。
- 顾客/门店切换和提交锁定必须清除未提交金额与身份，UI统一不等于授权合并。
