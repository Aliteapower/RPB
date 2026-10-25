# 共享产品目录与钱包卡实施记录

计划：`共享产品目录与钱包卡业务开发方案.md`；起始 SHA `1e47c952`。

2026-10-25：按用户“按上述方案开发落地”开始；使用 executing-plans 逐项实施，不发布。

Ruling：沿当前非 main 功能分支原位开发，保留用户工作区；运行时归属 D:\RPB，测试仅新建随机 schema，不改既有验证业务数据。

Ruling：采用方案保守默认，详见 API 合同；免费卡不要求资金账户；未用全退、已用禁退；不实现钱包余额购卡或未来商城/寄存/自助购物业务。

Pre-flight：T1 产品快照供 T2 定价/T3 卡规则；T3 权益供 T4 核销；T2/T3/T4 供 T5 购卡；T6 只读组合各自记录。目录不引用卡/钱包，卡通过端口获得身份、门店及收款能力。

## 完成状态

| 任务 | 状态 | 交付 |
|---|---|---|
| T1 产品分类与资料 | 完成 | 独立 catalog 前后端、租户双语资料、权限、生命周期、管理后台入口。 |
| T2 税价与投放 | 完成 | 精确税价、门店覆盖价、渠道许可、门店独立启停及版本。 |
| T3 集次与奖励 | 完成 | 免费领卡、来源防重、日上限、循环轮次、独立奖励、受控撤回。 |
| T4 核销与周期 | 完成 | 双向扫码、顾客具体授权、冻结业务日、原子扣次、跨日规则、幂等恢复。 |
| T5 购卡与退款 | 完成 | 独立订单、沿现有科目确认线下实收、恰好一次发卡、未用退款、异常实收登记与退款。 |
| T6 记录与验收 | 完成 | 游标历史、顾客筛选、独立统计、架构/鉴权/并发回归、HTTP 与浏览器验收、变更说明。 |

实施与集成授权边界：只在当前功能分支保留修改，未提交、推送、合并或生产发布。

## 接口及计划裁定

- 目录入口以门店上下文 `/api/v1/stores/{storeId}/catalog` 校验当前操作门店；资料本身仍租户共享。
- 租户级共享资料写入要求 tenant_admin 和对应 catalog 权限；店长通过 listing 接口维护本店，不改变邻店或共享生命周期。
- 产品前端不依赖 cards/wallet。通用 HTTP/金额/时间/样式移到 shared-commerce，csrf 标记由各模块自己的客户端配置。
- 产品名与分类名首版用双语列；通用查询边界隐藏存储细节，不把业务文案写入系统 i18n 文案表。
- 卡方案发布版本以不可变方案与持卡快照体现；新规则创建新方案，不覆盖已发卡。
- 采用真实 PostgreSQL fixture 的 CardPostgresTest/CatalogPostgresTest 汇总多个计划中的场景，不为了文件名形式复制相同数据库启动代码。
- 追加 V073 共享收据占用、V074 异常实收、V075 授权日期/原轮次/序号及 V076 门店投放生命周期，未占用既有 V001–V070。
- 未来寄存酒、积分商城、自助购物和实物库存只定义复用方向，未伪装为本次完成的产品线。

## 验证结果

- 扩大后端回归：136 项通过，0 失败、0 错误、0 跳过，日志 `target/catalog-cards-verified.log`。
- 最后收款渠道适配后的针对性复测：23 项通过，日志 `target/catalog-cards-followup.log`；与上组有重叠，不累加为独立数量。
- 最终全量前端：239 项通过，0 失败/跳过，`node --experimental-transform-types --test --test-concurrency=1 ...`；日志 `target/catalog-cards-all-frontend-complete.log`。
- Vue/TypeScript 与 Vite 构建：通过，日志 `target/catalog-cards-build.log`。
- 最终真实 HTTP：5 组流程断言通过，日志 `target/catalog-cards-http-final.log`；脚本 `scripts/validation/catalog-cards-api-acceptance.mjs`。
- 浏览器：独立产品页、双语编辑保存、语言切换、员工卡操作、顾客卡包、冻结产品资料和 DD-MM-YYYY 日期显示已检查。
- `git diff --check` 无空白错误；8 个原有钱包组件的规范化内容哈希仍与 HEAD 一致。

测试先行或与实现同步。保留的红灯证据包括 `catalog-cards-red.log`、`card-flow-red.log`、`card-withdraw-red.log`、`catalog-boundary-red.log`；不能据此声称每一个场景都单独执行过红灯。审查补充场景与修复同步完成，并有最终绿灯。

## 审查结论

初次独立审查报告 REQUEST_CHANGES，报告的跨日授权、跨轮撤回、500 条截断、门店/租户生命周期边界问题已逐项修复。通过对应行为回归和真实接口复核关闭问题，没有反复发起全量审查。

最终判定：APPROVE_WITH_COMMENTS，仅针对本地实现与已执行验证范围。剩余项为首版明确限制和手机摄像头/微信/iPhone 实机验收，不包含生产就绪或发布结论。

详见 `CATALOG_CARDS_ACCEPTANCE_20261025.md` 和 `../release-notes/2026-10-25-shared-catalog-wallet-cards.md`。
