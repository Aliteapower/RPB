# 前端依赖安全修复

## Version / Date

2026-10-27，本地修复，基于 `cb07ad31`。尚未提交或部署生产；此前生产发布记录中的 7 项风险仅在本地修复。

## New

增加真实 Vue SSR 属性处理回归：拒绝含回车的属性名，避免将一个属性名变成多个 HTML 属性；正常顾客文本仍按 HTML 规则转义。测试调用安装后的真实渲染器，不以版本号断言代替行为验证。

## Changed

`package.json` 将 Vue 最低版本提高至 `^3.5.44`，Vite 提高至 `^6.4.5`，同步锁文件。保持原主版本，不使用 `--force` 或新增 overrides。

## Fixed

| 审计涉及包 | 原锁定版本 | 修复后版本 |
| --- | --- | --- |
| vue | 3.5.38 | 3.5.44 |
| @vue/server-renderer | 3.5.38 | 3.5.44 |
| vite | 6.4.3 | 6.4.5 |
| postcss | 8.5.15 | 8.5.31 |
| brace-expansion | 2.1.1 | 2.1.7 |
| nanoid | 3.3.13 | 3.3.20 |
| source-map-js | 1.2.1 | 1.2.2 |

同时更新 Vue 同版本编译器、运行时、共享库及其解析/源码映射依赖，共 19 个包版本变化。锁文件包条目数量保持 145（含项目根条目），没有增删包。未改业务代码、模块边界、API 或运行配置。

## Migration

没有数据库迁移、后端变更或业务数据变更；本轮不启动本地数据库。未改登记同意、激活或密码恢复规则。

## Permission

没有权限、App Gate、租户或门店范围变化。

## Verification / TDD Review

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 完整依赖审计 | 通过 | 修复前 7 high；修复后 0 info/low/moderate/high/critical，包含开发依赖 |
| 干净安装 | 通过 | `npm ci`，安装后仍为 0 漏洞 |
| 依赖兼容性 | 通过 | `npm ls --all --json` 退出 0，无失效或未满足依赖 |
| SSR 安全回归 | 通过 | 旧版 1 失败/1 通过，升级后 2 通过；保留修复前后日志 |
| 全部前端 Node 测试 | 通过 | `src/test/frontend/*.test.mjs` 与 `src/utils/*.test.mjs`，337 通过，0 失败/跳过 |
| 类型检查与生产打包 | 通过 | `npm run build`，包含 `vue-tsc --noEmit` 与 Vite build |
| 既有无关文件 | 通过 | 12 项 SHA-256 与修复前一致 |

测试覆盖真实 Vue SFC 编译与渲染、登录/激活/邮箱、钱包与存酒、目录权限和门店范围等既有场景。新增权限、幂等、数据库场景不适用，因为这些实现没有变更。

全量测试命令采用 PowerShell 展开文件列表，然后执行 `node --experimental-transform-types --test --test-concurrency=1 <文件列表>`。最初基线使用 strip-only 参数，两份测试因既有 TypeScript 参数属性语法加载失败；在原发布源码、旧依赖上改用 transform 参数后，这两份测试 8 项全部通过。无需修改应用或既有测试。

证据保存在 `target/dependency-security-fix-20261027/`，包括审计前后 JSON、版本差异、干净安装、SSR 修复前后测试、全部前端测试、构建与无关文件哈希。原依赖的 transform 复核日志在 `target/customer-security-release-20261027/dependency-baseline-transform.log`。

## Code Review

- Scope：只修改依赖声明、锁文件、新增安全回归和本说明；19 项依赖变化均在 Vue/Vite 编译与安全修复链上，包来源为原 npm 官方仓库，保留 integrity 校验。
- P0 / P1：未发现阻塞项。没有租户隔离、权限、事务、API 或数据完整性改动。
- P2：无本轮必需优化。
- Final Decision：APPROVE，本地验证完成。生产尚未包含本次升级。

## Risk

审计结果反映 2026-10-27 当前 npm 漏洞库，不等同于不存在所有安全问题。前端依赖升级已通过现有测试与构建，尚未执行真实手机端人工验收或生产路由验收。构建使用当前工作区，包含原有未提交钱包组件；不可直接将该 dist 作为此次独立生产发布制品。

## Rollback Notes

本地需要撤销时，仅恢复本轮 `package.json` 和 `package-lock.json` 的差异，再运行 `npm ci`；不要覆盖现有无关文件。恢复旧依赖会重新引入已知风险，应优先修复兼容问题。生产没有切换，无生产回滚操作。
