# 顾客存酒与钱包入口隔离生产记录（2026-11-02）

项目 `D:/RPB`，沿用本聊天对入口修复的提交、推送、生产发布授权。分支 `codex/paynow-payment-product-line-staging`。

## 结果

- 最终前端 `9c6e840c952a3a7d637a0823dc9dcf9ddd6705f5`，后端 `4986d58794376530c1e88ec0bda447713c805640`，均已提交并推送。
- 存酒home按当前门店钱包AppGate返回walletEnabled；仅严格true才显示返回钱包。未开通、关闭、到期、未知或读取失败不推断钱包有效。
- 钱包me拒绝从未开通且无本人历史钱包账户的情况，403/WALLET_PRODUCT_UNAVAILABLE；普通首页不再加载空余额、积分、卡、充值等业务面板。原历史账户、已开通过的关闭/到期只读资格保留，不删除业务事实。
- 已开通过但关闭/到期的钱包限制收敛在普通首页；历史、卡权益及原单子页沿用原有各自控制，不将首页限制扩大为整棵路由禁用。从未开通的不可用标记仍阻止业务子页。
- 从存酒进入共享顾客注册，返回和登录成功后继续当前门店存酒，独立保留顾客注册、激活和密码恢复。仅接受当前门店已允许的返回路径，拒绝外部地址和其他门店。

## 验证

- 最终前端276项关联测试在干净源码归档上通过；覆盖开通/关闭/恢复、门店竞态、历史、有效卡和原单路由边界、中英文、顾客密码、注册回程、交易、积分及存酒。
- 后端156项通过，包括新增入口拒绝、商业门禁、身份/门店隔离、只读接口，及指针61644/postgres上的真实PostgreSQL钱包账本、交易历史与存酒测试。测试只创建/清理随机自有schema。
- 新增用例具有失败复现与修复通过证据。最终发布后端5个相关类与上述156项测试使用的类逐字节一致。
- 类型检查和Vite构建通过。两轮干净归档各4115个Git blob核对一致；9个原有无关业务文件及其他未跟踪内容未纳入提交或清理。
- 使用最终初始候选制品的只读合成API，浏览器检查桌面1280、390与320实际CSS宽度；10组记录的clientWidth与scrollWidth相等。仅存酒无返回钱包、钱包有效时有返回、未开通不可用、已到期仅有历史入口，中英文排版及截图已保存。第二轮仅收敛非首页路由，以上首页几何和数据路径不变，复用其视觉证据并新增路由回归。
- 公网最终444个文件逐字节匹配；booking、platform.booking、20000000.booking、sx.booking、mw.booking共5个域名的65次入口页面、5次版本及TLS检查通过；4项匿名业务接口正确401。
- 生产只读核查确认截图门店 `2ce7e5c7-5bf6-4ae6-a57d-63391add368a`：wine_storage租户状态enabled、门店开关true；wallet租户授权缺失、门店开关false。未更改该门店真实授权或开关。

## 发布保护与当前运行

- 后端仅更新 WalletAccess、WalletAccountService、StorageAccess、StorageDtos$Home、StorageQueryService 共5个类；其他类、依赖、历史迁移、配置保持上一生产jar原字节，包CRC及Spring Boot包结构验证通过。
- 后端jar SHA256：`85db8fc5035b369d8e2af8aeeee9127a9f874be538370e80281543a6db63fd60`。
- 首轮切换期间264张表指纹一致，无业务SQL写入、迁移、权限、订阅、资金或库存变更。Flyway仍为 `127|127|0`；环境、nginx配置及相邻服务保持原状。
- 当前后端MainPID `316077`、InvocationID `2b95f19a81af47bfa70543d49e9948f7`，active/running、NRestarts0，当前启动日志应用ERROR为0。最终补充仅切换前端，未再次重启后端。
- 第一轮备份 `/opt/rpb/backups/20261102-customer-entry-4986d587-before`；最终前端备份 `/opt/rpb/backups/20261102-customer-entry-9c6e840c-before`。旧静态资产保留，服务器末次约1.14GiB可用，没有擅自清理旧备份。
- 本地证据 `target/customer-product-gate-release-20261102/`：artifacts.json、session-artifacts.json、两轮source-verification、clean-frontend-tests.log、tested-backend-class-verification.json、visual-result.json及PNG、public-result.json、final-result.json和部署日志。只读合成预览服务已停止，临时视口已恢复，未停止共享PostgreSQL。

## 回退与边界

整轮回退使用第一轮备份中的jar、index、frontend-version及RELEASE_SHA/FRONTEND_RELEASE_SHA，恢复后只重启RPB；保留旧静态资产，不恢复数据库覆盖在线业务。单独恢复中间前端4986d587会恢复已纠正的非首页路由限制，应避免将它当作长期回退目标。

当前可访问的Chrome会话未找到截图对应的已登录顾客标签，因此没有宣称生产真实顾客私有页面的交互验收。生产证据为制品/服务/业务配置只读核查，页面角色与开关矩阵由组件、API/PostgreSQL及浏览器合成场景共同覆盖。没有代真实顾客登录、注册、付款或办理存取；实体手机、微信、印刷二维码及真实卡权益核销未验证。
