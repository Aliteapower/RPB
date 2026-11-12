# 顾客钱包交易入口分离

日期：2026-10-24。项目：D:\RPB。用户已明确授权“提交部署”。本记录为开发验证及发布准备说明，实际生产结果另行记录。

## 新增与调整

- 个人钱包余额卡右上新增交易记录入口，进入钱包金额记录，包含消费、充值、退款和金额冲正。
- 积分余额卡的交易记录入口仅进入该顾客的积分记录，包含增加、扣减、兑换和冲正。
- 首页下方与底部导航的交易记录进入全部记录。顾客列表提供全部／钱包／积分切换，保留日期、门店、方向、分页和详情返回能力。
- 修复前端显式 category=all 被转换成 auto、从而只查询消费分类的问题。未带分类的旧界面请求仍保留 auto 行为；商户原有页签不变。

## API、迁移与权限

- 现有只读 GET transactions 增加 money 分类，在数据库过滤、完整汇总及分页前排除积分来源。合约见 docs/api/2026-10-23-wallet-transaction-folders-contract.md。
- 无迁移、无新账本、无新增权限。查询仍与原租户、门店、顾客本人及来源权限条件相交；仅积分权限不能查询 money 分类。分类继续绑定分页游标。
- 无资金操作、积分记账、App Gate 或其他产品线业务变化。

## 验证与 TDD 审查

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 首页钱包／积分／全部及底部导航入口 | 通过组件渲染检查；新增检查在实现前失败 | target/wallet-entry-front-red.log、target/wallet-entry-front-final.log |
| 分类查询、切换、汇总、分页、加载／错误／空列表、详情返回 | 相关前端两组共102项通过 | target/wallet-entry-front-final.log |
| 导航与重试隔离回归 | 8项通过，使用 Node --experimental-transform-types | target/wallet-entry-extra.log |
| 真实 PostgreSQL 分类、本人范围、权限、分页游标及已有安全回归 | WalletTransactionsPostgresTest、WalletTransactionsApiTest、WalletLocalRuntimeSecurityTest 共27项通过 | target/wallet-entry-backend-final.log |
| TypeScript 与生产构建 | 通过 | target/wallet-entry-build-final.log |

PostgreSQL 使用本项目 target/local-postgres-current.txt 指向的61644端口，测试创建独立临时schema；未更改共享业务记录。新混合数据测试核实：全部6条、钱包2条、积分4条；钱包分页汇总包含充值与消费且无积分，跨分类游标拒绝，仅积分权限查询钱包拒绝。

扩展钱包前端回归不是全绿：原有 wallet-cash-topup.test.mjs 在Vite环境初始化阶段挂起，停止该测试进程后其余结果正常输出；单独以15秒超时重试仍未完成，见 target/wallet-entry-cash-recheck.log。初次全量命令中两个直接导入TypeScript的测试因strip-only模式不支持参数属性而失败，改用transform-types后8项通过。未修改这些无关测试或现金充值业务。

混合后端测试首次运行因测试准备操作者缺少开户及充值权限而失败，已恢复准备数据所需权限并保留独立的仅积分权限拒绝检查；最终27项全部通过。

## 代码与 API 审查

本轮自审：APPROVE。检查范围包括入口条件、顾客／商户页签差异、显式all保留、中英文文本、金额与积分汇总分离、SQL来源白名单与原身份条件相交、游标条件绑定及加载失败不显示旧记录。API为可选分类值扩展，旧值及省略参数语义保持；无需迁移或运行配置调整。

## 风险与回退

- 未做本轮真实浏览器／手机视觉验收；现金充值测试初始化超时未解决，不能声明全部前端回归通过。
- 发布时需配套前后端，新前端的money分类依赖新后端；本轮没有生产发布。
- 可按本次文件差异回退入口、查询分类、合约和测试，无数据回写；保留工作区原有修改，不回滚其他钱包业务。
