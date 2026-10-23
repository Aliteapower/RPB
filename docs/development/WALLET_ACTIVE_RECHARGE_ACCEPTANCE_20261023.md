# 顾客充值活动优先检查与展示优化

2026-10-23，D:\RPB。本轮未提交、推送或部署，保留上一轮交易分类及其他已有改动。

## 诊断及线上只读核对

用户截图显示顾客普通 1:1 档位、商户活动为草稿；用户随后确认已在线上发布。21:41（Asia/Singapore）只读核对 RPB 正确目标 ubuntu@43.134.69.75、/opt/rpb、rpb_acceptance：门店 20000000-0000-0000-0000-000000000983 对应钱包计划 83ae0dc1-04f5-4eb4-a1db-358ba44dc312，campaigns_enabled 和 self_topup_enabled 均为 true。模板 published，current_published_revision=4，生效时间为 2026-10-23 17:55 至 2026-11-22 17:55（+08）；第 4 版档位 100+10、300+50、500+80、1000+200，均启用。没有执行任何生产发布、配置修改或资金操作。

先前默认 SSH 别名指向其他项目主机，仅读取数据库目录并确认没有 RPB 数据库后，改用项目发布手册指定的 RPB 主机；未在其他主机作修改。

现有 WalletMarketingService.customerOffers 与员工 offers 共用 live；MarketingRepository.active 仅选择本租户、本钱包计划、已发布且当前有效的最高优先级活动。无有效活动或商户关闭活动时使用基础 1:1 档位。此规则原已实现，不需改写后台业务或自动发布草稿。

已打开顾客页面不自动监听商户发布事件，需点击刷新重新读取活动。本轮没有声称已通过真实顾客登录核对生产响应或手机页面；线上证据为只读配置和档位核验。

## 实现及审查

- 复用 WalletTopupOffers，为顾客及使用该组件的商户页面显示“活动充值”、当前活动名称和每次只能选择一档说明。
- 无有效活动时显示“普通充值（1:1）”和无赠送说明，明确当前模式。
- 商户草稿卡提示需发布、在有效期内且启用活动才对顾客生效。
- 补齐已有 campaignName 响应的前端类型和中英文文案；无新 API、权限或数据库迁移。
- 保留单档申请、服务端报价校验、实际收款确认、付款声明不入账、PayNow 及幂等规则；不修改资金和积分逻辑。
- 按 troubleshooting、tdd-review、code-review、release-note 检查；自审无阻塞问题。无其他产品线业务代码修改。

## 验证

- 前端新增真实组件测试，旧版缺少活动名称和模式提示时失败（target/wallet-topup-active-red.log），实现后营销、积分等组件 43/43 通过（target/wallet-topup-active-frontend.log）。覆盖活动模式及无活动 1:1 模式，仍无额外获取报价按钮。
- 后端 3 类 25 项通过：WalletMarketingPostgresTest 15、WalletStaffMarketingApiTest 6、WalletMarketingLocalSecurityTest 4（target/wallet-topup-active-backend.log）。新增顾客有效活动优先测试，验证草稿、已发布、未开始、过期、停用及 1:1 回退；同时验证顾客和员工读取相同有效档位。
- PostgreSQL 使用 target/local-postgres-current.txt 指向的 61644 运行环境，测试创建独立随机 schema，未使用公共业务记录。
- npm run build 通过（target/wallet-topup-active-build.log）；git diff --check 通过。

## 风险及回退

没有生产界面发布，新增提示目前仅在本地代码中；真实手机未重测。若要撤销本轮展示优化，仅恢复本轮组件、类型、文案和相应测试，不回滚此前交易分类或其他已有改动。无数据回写、迁移或权限回退需求。
