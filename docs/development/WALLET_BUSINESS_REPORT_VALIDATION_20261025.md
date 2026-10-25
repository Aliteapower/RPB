# 钱包营业报表验证与审查

## 已完成

- 独立 controller / service / repository / DTO，复用 TransactionProjection，未向交易写服务增加分支。
- 后端校验已登录员工的租户、门店、报表权限；积分单独限制，不提供公共路由。精确 GET 本地安全放行、WalletErrorAdvice 注册，保持 private/no-store。
- 自然日/月/年，严格日期、闰年与新加坡午夜边界；完成时间区别于申请时间；无分页截断（1,202 笔）；跨租户/门店、待处理排除、本金赠送分离；消费退款、双向积分冲正及独立积分产品不混入。
- 只读切页不自动开钱包账户；UI 空/加载/错误/权限、旧门店请求晚到、大项筛选、无数据项隐藏、中英文、CSV 格式及公式转义。

## 执行证据

`target/local-postgres-current.txt` 指向 61644，本轮 PostgreSQL 测试使用该端口，自动创建/删除专用测试 schema，未访问线上数据库。

- `mvn -q -Dtest=WalletBusinessReportTest,WalletComponentReportingTest,WalletLocalRuntimeSecurityTest,WalletTransactionsPostgresTest,WalletTransactionsApiTest test`：42 项通过，日志 `target/wallet-business-report-backend.log`。
- 新增消费退款、积分断言后，重跑 WalletTransactionsPostgresTest 的对应 2 项通过，日志 `target/wallet-business-report-points-refund.log`。
- `node --experimental-transform-types --test src/test/frontend/wallet-*.test.mjs`：212 项通过（后续增加工作区只读回归、大项过滤测试）。
- 最终 `wallet-business-report*.test.mjs` + `wallet-workspace.test.mjs`：20 项通过，日志 `target/wallet-business-report-final-frontend.log`。
- `npm run build` 与 `git diff --check` 通过。Node/Mockito 实验类型转换与动态 agent 为既有工具链提示，不影响结果。
- 本地浏览器 http://127.0.0.1:5191/ 使用实际组件、隔离样例数据，已点选日/月/年和充值大项。截图 `target/wallet-business-report-preview/category-topup.png`、month.png、year.png（后两张为大项筛选加入前）。没有线上新功能运行验收。

## 排错记录

最初的历史时间测试尝试更新追加式钱包分录，被数据库不可变约束正确阻止；改为在独立 schema 中直接插入合法历史事件及对应 legacy 资金批次，未削弱约束。API 未登录测试发现新 controller 尚未注册错误映射，补齐后通过。全量前端首次使用 strip-only 不支持既有参数属性语法，改用现有 Node 的 transform-types 后通过。测试 renderer 补齐 DOM 根节点方法后运行真实组件。

## 自审结论

通过：仅增加只读查询和报表展示；无写 SQL、迁移、账号信息返回、外部消息、自动部署。各项目 count 用于存在性判断，父子项不混合相加。渠道按历史名称隔离；大项筛选一致应用到卡片、表格及 CSV。未做跨产品汇总，未修改每日营业记录产品。

原工作区 8 个钱包组件改动、3 份原有验收文档和私人文件保留。无提交、推送、发布。剩余边界为生产规模性能和发布后的真实只读验收。
