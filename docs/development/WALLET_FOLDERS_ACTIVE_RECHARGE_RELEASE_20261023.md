# 钱包交易分类与活动充值提示发布

日期 2026-10-23，D:\RPB；用户明确授权提交部署。

## 范围及就绪检查

消费／充值／积分三个文件夹页签，共用交易记录入口，每类按全部／收入／支出在后端查询、稳定分页及完整结果汇总；详情返回保留条件。顾客与商户按各自视角显示收支，资金和积分独立。顾客充值显示当前有效活动名称或普通 1:1 模式，商户草稿说明发布条件。

复用当前交易、营销、权限及租户门店范围，无新记账能力，无新 API 写操作，无新权限、环境变量或数据库迁移；交易查询增加 category/direction 和响应 category，旧客户端省略分类仍保持原语义。原支付授权、幂等、退款、单档充值及线下收款确认不变。

证据：后端交易／个人付款／安全 41 项、营销／API／安全 25 项通过；钱包前端 113 项回归及最终营销 43 项通过；Vue 类型检查和 Vite 构建成功。PG 使用 target/local-postgres-current.txt 所指 61644 隔离环境。实际浏览器分类／收支／详情返回及只读无写入证据见两轮验收文档。生产规模性能及真机手机相机未重测。

## 发布与回退计划

正确目标 ubuntu@43.134.69.75，/opt/rpb，rpb-backend，rpb_acceptance。发布前确认前后端原 SHA a4340a9d 及 jar 哈希，持发布锁；上传不可变制品并核对哈希，停机边界备份原 jar、前端、版本及 PostgreSQL dump，核对业务表指纹。等待新进程健康且稳定再开放入口，核对公网资源、权限保护、业务数据及邻近服务状态。

失败恢复本轮备份的原 jar、前端及版本，确认旧服务健康；无结构迁移，不恢复数据库覆盖真实业务。只发布本轮明确文件，不纳入无关验收材料、私密文件和 target 本地环境。生产不代真实顾客充值、付款或积分操作。

## 实际结果

代码提交 `bf01194d1c73935b2aa7383cb0e65b2391891b86` 已推送至 `codex/paynow-payment-product-line-staging`，生产前后端 RELEASE_SHA 均为该 SHA。发布脚本正常完成，无回退。

- 干净 Git 归档构建：Maven package、npm ci、Vue 类型检查和 Vite 构建成功。npm 提示 7 项既有 high 漏洞，本轮未变更依赖。
- jar SHA256 `bb9759f0b4b98528b5c2b0ca057bf83ee0bc591b463d65cd4032d3728a8118df`；前端包 SHA256 `44c3e3f3f089926027d9648bdf8ad75333f5745a493f6742b094e2681c1240e1`，上传及安装核验一致。
- 发布目录 `/opt/rpb/releases/20261023-wallet-folders-bf01194d`；备份 `/opt/rpb/backups/20261023-wallet-folders-bf01194d-before`，含原 jar、完整前端、版本和 922193 字节 PostgreSQL dump，pg_restore 目录校验可读。
- 切换边界与最终 112 张业务／配置表指纹一致；排除会话、限流、动态身份码、审计等自然变化表，个人同意和策略历史表纳入本轮核对。营销已发布活动及顾客数据未修改。Flyway 仍 064／64 项／0 失败，没有新迁移。
- 4 个公网域名 HTML 和 183 个静态资源逐字节符合本轮构建；2 项管理入口匿名拒绝；个人码、个人授权、消费及统一交易 API 共 32 项匿名／缺标记／跨站保护通过；原公开订位、支付、日账本和身份等 9 项接口状态正常。
- 新进程 PID904083，InvocationID `476328f41a2e4d05a81b255fc50b680e`，NRestarts0，最终核验身份稳定；新进程日志无 ERROR，临时 RPB 流量隔离规则已移除。nginx、PostgreSQL、Redis、Docker 的 PID／InvocationID／状态／重启数均未改变。
- 未替真实顾客执行充值、扣款、积分或授权。实际交易交互来自隔离测试及本地浏览器证据；生产登录后的顾客充值画面、真实手机相机和规模性能未重测。

本轮证据在 D:\RPB\target\wallet-folders-*，公网权限及原接口检查亦保留在 target/wallet-personal-production-auth-proof.json 和 target/wallet-personal-public-folders-after*。未提交无关原有文件或私密材料。发布后文档补记不改变运行制品 SHA。
