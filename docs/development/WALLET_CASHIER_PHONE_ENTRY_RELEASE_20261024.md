# 收银台页签与手机号选客前端发布

2026-10-24，D:\RPB。用户明确授权提交部署。

## 范围及就绪检查

蓝色收银卡片增加消费收款／充值／积分页签，按权限进入对应扫码业务；下方独立手机号查询入口，核对候选并明确确认后才显示充值及积分操作。手机号确认状态独立于扫码凭证，切换充值／积分保留顾客，更换门店或来源清空状态，手机号下一位仍回到手机号入口。

GO：同代码状态钱包前端123项回归、类型检查和构建通过，本地浏览器验证确认前后、业务来回切换与更换顾客。见 WALLET_CASHIER_PHONE_ENTRY_ACCEPTANCE_20261024.md。原实际收款确认、活动单档、积分不抵现金、付款授权、幂等和未决恢复不变。

无 Java、API、权限、环境、依赖或数据库迁移。复用现有顾客查询，服务端租户、门店、本人及操作权限不变。真实手机相机及触控尚未重测，不替真实顾客执行交易。

## 发布及回退

目标 ubuntu@43.134.69.75、/opt/rpb/frontend。预期旧前端52ace1a0，后端cb400bf7。仅发布前端，后端jar、进程、版本及数据库不变。持发布锁并校验原版本／index及上传制品哈希，备份完整前端及前端版本，先复制资源再原子替换index；旧资源保留，避免已打开页面失效。

失败恢复前端备份和FRONTEND_RELEASE_SHA，不回写数据库。发布后验证公网HTML、全部资源、原接口状态及RPB和邻近服务进程。只提交本轮文件，保留无关本地材料。

## 实际结果

运行前端提交 `acb9b206f145b730eaa2d5fec159e73cb4344ae2` 已推送至 codex/paynow-payment-product-line-staging；生产 FRONTEND_RELEASE_SHA 已为该 SHA。后端 RELEASE_SHA 仍为 `cb400bf7de938d5e85733b513a05353fa565ed78`，符合前端单独发布范围。

- 干净 Git 归档 npm ci、Vue 类型检查及 Vite 构建通过，复用同代码状态123项前端测试与隔离浏览器证据。既有7项 npm high告警，本轮未变更依赖。
- 前端包 SHA256 `fa5335a4abe0d92c5bcd0e3cb3c60041c9a09eba7c541d9bac3f36ba88b5b6bb`，上传及解包前核验一致。
- 发布目录 `/opt/rpb/releases/20261024-wallet-phone-entry-acb9b206`；备份 `/opt/rpb/backups/20261024-wallet-phone-entry-acb9b206-before`，完整旧前端和旧版本已验证可读。旧assets保留，未触发回退。
- 4个公网域名HTML和全部184个静态资源逐字节符合本轮构建；2项管理入口匿名拒绝、2项顾客查询匿名401；原订位、支付、日账本、身份等9项接口状态检查通过。
- 后端jar SHA256仍 `40b9fccd3358e8f86a177a645f89dceaa5b34dbca6c2e2cc6b4ddaf177d625fa`；PID927854、InvocationID `2dd3d1569e9c494a9cc1af42dd3b0346`、NRestarts0保持不变。RPB、nginx、PostgreSQL、Redis、Docker进程／InvocationID／状态／重启数发布前后及最终一致，无服务重启。
- 无数据库迁移、写入或配置修改；没有替真实顾客授权、充值、付款或办理积分。线上登录后的业务交互与真实手机相机／触控未重测，完整交互证据来自隔离环境。

证据在 D:\RPB\target\wallet-phone-entry-*；原接口检查在 target/wallet-personal-public-phone-entry-after*。无关原有改动、未跟踪验收材料和私密文件保持原状。后续发布记录文档提交不改变运行前端SHA。
