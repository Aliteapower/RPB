# 产品开通目录与入口一致性发布验收 — 2026-10-26

## 发布结果
GO，功能提交 `514b070af7d4f2708b5275421e914e4dbe85598c` 已提交、推送并部署。17 个任务文件；12 个原有无关改动 SHA-256 保持一致。前端与后端 RELEASE_SHA 均为该功能提交，实际发布目录 `/opt/rpb/releases/20261026-product-entry-514b070a-r2`。

租户管理目录只列存在开通关系的产品；正常商户侧栏和收银台入口复用 AppGate 可见应用并叠加员工权限。刷新开通列表会刷新侧栏；加载、错误和切店期间不展示旧入口。顾客既未开通且无寄存历史时隐藏入口；保留曾开通产品的关闭/到期管理、已有历史与受控领取。

## 源码、测试及构建
- 同一代码状态的 69 项后端、280 项前端组合回归全通过；无失败/跳过，生产构建通过。
- 从提交 git archive 导出独立目录，逐 Git 对象比对 2742 个文件（允许 Windows CRLF/LF），依赖 package 与 lock 文件与既有已安装目录一致。Maven 生产打包、vue-tsc、Vite 均退出 0。
- JAR 比对仅 AppGateService、StoreProductSettingsService 两个业务 class 变化，无无关业务类或迁移改动。

## 目标边界、备份与回退
只发布 `43.134.69.75 /opt/rpb`、服务 `rpb-backend`、端口 8080、数据库 `rpb_acceptance`。无迁移、权限或环境变量新增，Flyway 始终 `080|80|0`。
成功尝试备份 `/opt/rpb/backups/20261026-product-entry-514b070a-r2-before`（应用、前端与 18,264,526 字节数据库 dump）；在独立临时库恢复当前 dump 成功，兼容既有 SQL 函数 search_path 后核验并清理临时库。
失败会恢复原 JAR、前端 index/资源与前后端版本标记，保留业务数据，不恢复旧数据库。成功上线后无待处理回退。

## 在线业务验收
独立 QA 租户 `43d4b2f4-b3f6-4883-9e6e-474de2985011` 共 20 项记录内业务 API 验收通过，另执行 QA 初始开关还原与核验：
- 没有开通酒水寄存的产品不出现在管理列表和 /api/me/apps；伪造直接开启请求 409 PRODUCT_LINE_UNAVAILABLE。
- 已购买钱包在关闭时仍保留管理卡片且可重新开启；关闭时正常业务入口隐藏，恢复后显示。
- 钱包余额 3800、原次数卡余额 28 等读回与初始活动状态一致；未扣款、未操作真实存酒/积分或卡权益。QA 相邻门店设置未改变。
- QA 原钱包开关是 `False`，测试结束后已恢复该值；临时 QA 员工和顾客会话均撤销。
- sx 截图门店 `e4ef2f66-ab88-4c22-884b-02bb802eba40` 只读核验：无酒水寄存租户授权、无门店订阅项，符合新目录与入口隐藏条件。没有创建真实租户会话、改写授权或业务数据；真实 sx 登录后 UI 未在本轮自动操作。菜单显示由既有可见应用 API、线上相同资源和编译 Vue 组件回归共同验证。
- 顾客寄存 home 返回停用且无记录；共享商品目录、卡与钱包历史读取正常。

## 公网和稳定性
四个域名合计 36 个页面路由及 22 个静态资源与发布文件一致（含租户产品线管理、侧栏及收银台资源）。64 项受保护接口匿名返回 401；另 4 个域名的公开应用发现接口按原合约返回 200 且 apps=[]，不泄露未授权产品。
148 项真实业务表指纹一致，排除独立 QA 与明确动态会话/审计表，不代表全库每行不变。后端 active，PID `1842016`、InvocationID `20b1bf039fd14d309d82fa695684eaf8` 稳定，新进程日志无 ERROR；nginx、PostgreSQL、Redis、Docker 状态和进程身份未变。

## 验收脚本修正记录
首次上线保护因脚本误认为 QA 钱包原本开启而触发自动回退；已确认原版本、健康状态恢复。根据实际初始关闭状态修正 QA 生命周期验收，并在新 r2 发布/备份目录重发通过，代码未为规避该断言修改。首轮备份和目录保留。
最终检查曾把公开 /api/me/apps 误当成必须 401 的接口；核对未变更的 MeAppsController、MeAppsResponse 及既有测试合约后，改为严格断言 200 + 空 apps，重新核验通过。不是放宽应用访问授权。

## 证据
本地 `target/product-entry-release-20261026/` 中 scope.json、source-proof.json、jar-comparison.json、artifacts.json、frontend-build.log、maven-package.log、deploy.log（首次回退）、deploy-r2.log、final-check-r2.log、final-check.json、entry-live.json、live-readback.json；服务器同名 `/home/ubuntu/product-entry-release-20261026/`。
JAR SHA-256 `6b70b2e6e82149762c3349df6eabf771a6ed47a7438986642d8a6b73deceec26`；前端 index SHA-256 `20d6d5312751786b209d1af37280e8dfc616d46970a48af9d949fdfe35ac66dc`。
