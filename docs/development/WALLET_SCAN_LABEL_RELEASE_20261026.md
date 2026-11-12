# 钱包扫一扫文案发布验收 — 2026-10-26

## 结果 / 范围
GO，用户明确授权提交部署，前端功能提交 `3efedcd25cee9f9309b5fb02b595115533377b15` 已推送并发布。业务改动仅 `src/modules/wallet/uiLocales.ts` 两处翻译值：中文“扫一扫付款”→“扫一扫”，英文“Scan to pay”→“Scan”。首页链接与扫码页标题继续共用 wallet.ui.scanPay；扫码逻辑、路由及提示说明未修改。原有 12 项无关改动哈希完全保留。

## 构建与回归
从已提交 SHA 导出独立 git archive。复用上轮已安装且 package.json/package-lock.json 完全相同的依赖目录（junction），没有复制未提交源码。vue-tsc 与 Vite 生产构建退出 0。16 项导航、卡码、个人钱包和商户出码相关测试通过，零失败/跳过。中英文翻译导入校验、git diff --check 通过。

## 部署与回退保护
只发布 /opt/rpb/frontend，备份 `/opt/rpb/backups/20261026-wallet-scan-3efedcd2-before`，不可变发布目录 `/opt/rpb/releases/20261026-wallet-scan-3efedcd2`。先复制静态资源，最后原子替换 index.html；校验失败自动恢复旧 index 与 FRONTEND_RELEASE_SHA。保留旧资源支持已打开页面。
仅 FRONTEND_RELEASE_SHA 更新；后端 RELEASE_SHA 保持 `00854affcdadcfa2ae82c76931dab0ce5de1f511`，JAR SHA-256 保持 `61bff9be9038724df0d2a80c6a3d3047d7cab72fc6ccbd8724933a113fec02ab`。没有后端重启、数据库迁移、依赖/配置或权限变化，Flyway 保持 080|80|0。无数据库写入，无需恢复数据库。

## 发布后线上验证
- 四域名的首页与扫码路由共 8 个 HTTP 页面，字节均等于发布 index；8 项首页/扫码页/扫码组件/i18n 等资源与发布文件一致。
- 线上 i18n 资源包含 scanPay:“扫一扫”和 scanPay:“Scan”，不再包含旧 scanPay 值；首页和扫码页仍引用相同翻译键。
- 12 项未登录 API 访问返回 401。
- 后端、nginx、PostgreSQL、Redis、Docker 的状态、PID、InvocationID、重启次数前后一致；148 项真实业务表指纹一致（排除 QA 租户、会话与明确的动态审计表，不能理解成全库每行恒定）。

## 浏览器运行验证及边界
使用与线上逐字节一致的构建资源，在仅监听 127.0.0.1:5206 的受控 HTTP 数据夹具运行真实前端。首页链接“扫一扫”点击进入 /scan，标题“扫一扫”。通过真实扫码组件的粘贴入口识别测试码，验证 useCustomerWallet 的五条跳转：WB→bills、WR→points-redemptions、WM→merchant-orders、CK→cards、WS→wine-storage。只验证识别与导航；夹具目标详情使用简化数据，未将其作为业务详情验收。
没有建立生产登录会话、执行真实扣款/积分核销/取酒，也没有声称 iPhone 相机或相册识别验收通过；摄像头在测试中主动停止。浏览器验证窗口首次后台附加超时，改用前台临时窗口后完成；结束后关闭窗口与本地服务。

## 证据
`target/wallet-scan-label-release-20261026/`：scope.json、artifacts.json、frontend-build.log、scan-routing-tests.log、deploy.log、live-check.json、browser-routing.json、home.png、scan.png。服务器 `/home/ubuntu/wallet-scan-label-release-20261026/` 保存服务前后快照、业务指纹及线上资源验证。
前端 index SHA-256：`5bd81a77c2e1d83b3b5870d946942a4e0d164ea7ef755fa2abe0283e071c2760`。
