# 顾客端存酒卡紧凑布局

日期：2026-10-27。用户随后授权“提交部署”，已提交、推送并发布前端。

## 调整

- 页头合并标题与店名，移除重复的顾客区徽标。
- 存酒、取酒、记录使用紧凑分段导航；记录子导航区分主次。
- 取酒卡片左侧集中展示酒名、余量、到期时间、位置，右侧保留数量和出码操作；展示二维码时取消按钮使用次级样式。
- 表单、列表、提示及卡片统一间距。桌面内容最大宽度 820px，手机适配到 320px。
- 输入控件至少 16px 字号，操作按钮至少 44px 高；极窄屏表单单列显示。

## 范围与兼容性

- 新样式统一以 `.storage-customer-page` 限定，保存在 `src/modules/wine-storage/customer/customer.css`。
- 无 API、数据库迁移、权限、App Gate、租户或门店边界变化；存取酒和数量校验逻辑不变。
- 不修改 Wallet 或商户端样式。工作区已有 Wallet 改动保留。

## 验证与审查

- 检查清单：顾客/商户入口隔离、空态、错误与登录状态、门店直接登记模式、瓶数限制、出码与重复提交保护；复用既有回归用例，纯样式改动不新增业务测试。
- `node --test src/test/frontend/wine-storage-page.test.mjs src/test/frontend/storage-customer-selection.test.mjs src/test/frontend/wine-storage.test.mjs`：85 项通过。测试夹具存在缺少翻译键的提示，不影响通过结果。
- `npm run build`：通过。
- 浏览器检查：实际 Vue 组件搭配模拟 API，17 组中英文、320/390/768/1280px 布局与取酒码检查通过，无横向溢出，按钮高度不少于 44px；检查截图后修正极窄屏英文下拉框及取消按钮层级。
- 截图与布局结果：`target/storage-customer-ui/`。该目录仅为可重建的本地验证产物。
- ui-review / tdd-review / code-review 自查：无本次变更引入的必须修复问题；既有交互、状态提示及禁用条件保留。

## 风险与回退

- 浏览器验证使用模拟数据，未执行真实数据库存取酒或手机微信扫码验收。
- 回退本次顾客页及取酒组件的模板调整，移除顾客样式引用即可恢复原布局；不涉及数据回退。

## 生产发布结果

- 功能提交：`a40e5e5f417f92b7519e4943e998a942a072bbf6`，已推送到 `origin/codex/paynow-payment-product-line-staging`。
- 干净 Git archive 的 2,907 个文件逐项核对 blob，重新执行 npm ci、85 项回归及类型检查/Vite 构建通过。12 个无关工作区文件 SHA256 保持一致，未带入提交或制品。
- 服务器：`ubuntu@43.134.69.75`；不可变前端制品 `/opt/rpb/releases/20261027-storage-customer-compact-a40e5e5f`；完整回退备份 `/opt/rpb/backups/20261027-storage-customer-compact-a40e5e5f-before`。
- 持有项目既有发布锁，备份核对通过后添加新资源、原子切换 `/opt/rpb/frontend/index.html`，更新 FRONTEND_RELEASE_SHA；未执行数据库写入、迁移或后端重启。
- 四个生产域名共 32 个页面入口返回新 index，11 项相关公开资源字节一致，12 项匿名接口访问被拒绝。279 个发布文件逐项一致，8,485 个旧资源完整保留。
- 后端仍为 `f3f35ca9f2e87e30f54f497e5fdd5049deda64fb`；PID 2372336、InvocationID `8d062780d6764965b892fbab41bdd702`，后端/Nginx/PostgreSQL/Redis/Docker 服务身份及环境配置未变；Flyway 保持 `086|86|0`。最终观察无新增后端 ERROR。
- 前端归档 SHA256：`08c7d93c1f58e0b8171231b97edcc59caf248c031400b3e3b4689aa2c0d7be8f`；index SHA256：`3f665a8de7b92313b22e967ae7eb42e7cd358d838e3097563b9cf6da8760e164`。
- 线上回退：从上述备份原子恢复 index.html 与 FRONTEND_RELEASE_SHA，保留哈希资源；无需重启或恢复数据库。
- Production readiness：GO。证据：`target/storage-customer-compact-release-20261027/` 及服务器同名 `/home/ubuntu/` 目录。未执行真实存取酒、微信真机或相机扫码验收。
