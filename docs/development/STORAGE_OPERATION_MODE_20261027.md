# 存取酒模式设计与验证计划

- 按门店配置，沿用存酒模块和 AppGate，不改变其他模块边界。默认顾客核销；租户管理员可选直接登记。复用已有商户存酒登记，直接取酒使用专用命令并复用锁库存与交付事务。
- 不采用前端跳过扫码（会被伪造请求绕过），不将旧待授权单批量改为已授权。直接登记创建的取酒单与扣减同事务完成，审计明确属于商户登记。模式修改和直接取酒使用同一门店事务锁。
- V084 新增门店设置表及设置审计表，复合门店租户外键、模式约束、版本号；无历史回填，读取无配置时返回默认模式。不改已有迁移。
- 测试清单：默认模式拒绝直接取酒；管理员及权限校验；配置版本、重放、非法值、门店租户隔离；直接取酒的库存、批量回滚、过期、并发、重复重放、模式切回重放；旧扫码授权回归；前端设置及直接确认入口、顾客入口；构建。
- 仅本地实现，不提交、推送、部署。保留现有 Wallet 改动。

## API Review

- `/settings` 与 `/direct-deliver` 均在现有 `/api/v1/stores/{storeId}/wine-storage` 下，显式 DTO，使用 StorageIdentity 会话及租户门店范围。配置要求 tenant_admin + rules.manage + 新业务 AppGate；直接取酒要求 deliver 权限，沿用历史存酒可受控领取规则。
- 复用 StorageRequestSecurityFilter 的同源写保护；本地 allowlist 只加这两个 POST 路径。幂等按租户、门店、操作人、操作类型与键隔离，内容变更冲突，网络丢响应走原命令恢复。
- Home 新字段兼容前端旧响应缺省值。模式切换不会改动旧授权，直接登记不记录 customer authorized 事件。

## Database Review

- V084 只创建两张新表，无存量数据更新。门店租户复合外键、模式 check、版本 check；设置主键限定每店一条，审计索引覆盖门店和时间。
- 无设置记录时读默认 customer_verification / version 0，首次写入版本 1。更新锁与直接交付采用相同门店 advisory lock，防止切换与执行发生竞态。
- 回退应用时可保留新表。回退前应处理未确定交付请求、告知门店恢复核销流程；不要删除历史存酒或交付记录。

## TDD 与排错

- 先添加数据库行为用例，缺失 SettingsService/DTO/directDeliver 时编译失败；实现后修正 AssertJ 对 TransactionTemplate 泛型推断的二义性，使用有类型的中间变量。
- 前端首次新增用例因测试模拟 select.props.onChange 失败；Vue v-model 使用原生 change listener，改为 select.listeners.change 后 52 项通过。
- 已覆盖租户管理员设置权限、默认值、版本与重复命令、跨租户/跨店默认隔离；直接取酒的无顾客授权审计、模式切回后重放、权限失败、归属、过期、库存不足。新增批量回滚与并发扣减用例，旧待授权单不被模式切换豁免。

## Code Review

- 范围限于存酒模块、两个本地安全入口及相关文档测试，未编辑已有 Wallet 改动。
- 控制器不访问持久层；设置独立服务/组件；直接取酒复用原事务、UUID 排序行锁、有效期复查和整批回滚，客户端按钮只负责明确交付意图。
- 顾客直接登记模式隐藏新申请与新出码，服务端同步拒绝新请求；旧记录仍可查看，旧申请仍可由商户审核，已成功命令可重放。
- 审查未发现尚未解决的 P0/P1。浏览器实机、手机相机和生产验收未执行。

## 最终验证

- `mvn -q '-Dtest=Storage*Test' test`：原存酒与商品寄存相关回归通过（新增并发用例之前）。
- `mvn -q '-Dtest=StoragePostgresTest,StorageModeApiTest,StorageAccessTest,StorageRequestSecurityTest' test`：最终 40 项通过、零失败、零跳过；PostgreSQL 按 D:\RPB\target\local-postgres-current.txt 的 61644 端口，隔离临时 schema 执行全量迁移（含 V084），测试后清理自身 schema，不改业务 schema。
- `node --experimental-transform-types --test src/test/frontend/wine-storage-page.test.mjs src/test/frontend/wine-storage.test.mjs src/test/frontend/storage-merchant-scope.test.mjs src/test/frontend/storage-customer-selection.test.mjs src/test/frontend/storage-locations.test.mjs`：52 项通过。
- `npm run build`：vue-tsc 和 Vite 通过。`git diff --check` 通过。
- 日志：`target/storage-mode-backend.log`、`target/storage-mode-frontend.log`、`target/storage-mode-build.log`。Mockito 动态 agent 警告未影响测试结果。
