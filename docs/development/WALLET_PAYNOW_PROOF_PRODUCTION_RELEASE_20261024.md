# PayNow 商户及顾客凭证：生产发布记录

2026-10-24，新加坡时区。用户明确授权“提交部署”。项目 `D:\RPB`，分支 `codex/paynow-payment-product-line-staging`，远端 `Aliteapower/RPB`。

## 结果

功能提交 `c3ce328e081dc3e3438057422fa8a11f3908de7a` 已推送并部署，线上前后端版本标记一致。本记录另行提交，不改变运行程序版本。

顾客充值、账单 PayNow 补付可上传凭证交商户确认；商户可选用本单顾客凭证，核对银行 Ref 并明确确认到账。上传、OCR及顾客付款声明均不自动入账。顾客可查看本人凭证及确认/关闭后的只读历史，员工内部备注和附件不向顾客开放。

## Production Readiness：GO

- 仅提交本功能60个文件；8个原有组件换行状态、其他未跟踪验收资料及私密文件保留，未读取私密文件。
- 干净构建目录 `target/paynow-proof-release-build-c3ce328e` 按Git原始blob生成，2452个文件校验通过；1975个源码/配置文件与已验证工作区内容一致（仅忽略CRLF差异）。
- 复用该代码状态已通过的后端53项测试；干净目录再次执行相关前端124项，全部通过。干净 npm ci、vue-tsc、Vite构建和 Maven package 均成功。锁文件已有7项high漏洞提示，本轮未更改依赖。
- 生产目标通过实际版本、jar/index哈希及服务核实为 `ubuntu@43.134.69.75`、`/opt/rpb`、`rpb-backend`、`rpb_acceptance`。旧版 `8605889c`，旧Flyway `068|68|0`。
- V069、V070在生产数据库独立副本演练通过，115张原有业务及配置表的数据指纹一致，演练数据库已清理。首次副本恢复因既有嵌套SQL校验函数遇到pg_restore清空search_path而失败；仅对副本恢复SQL设置public搜索路径后完整恢复及迁移通过，未更改生产函数或业务数据。
- V069新增私有凭证表及收款作用域约束；V070附加来源顾客及同租户FK。无新权限、环境变量或外部支付调用。上传只保存证据，资金仍由原授权商户事务处理。

## 备份、切换与回退保护

- 发布目录：`/opt/rpb/releases/20261024-paynow-proof-c3ce328e`。
- 备份目录：`/opt/rpb/backups/20261024-paynow-proof-c3ce328e-before`，包含旧jar、完整前端、版本标记、数据库dump及业务指纹；dump953958字节，pg_restore目录验证可读。
- 全程持既有RPB发布锁；短暂隔离nginx到RPB 8080的流量，停止RPB后备份。新jar原子替换并完成启动、迁移、数据指纹和稳定性检查后发布前端入口、解除隔离；保留旧静态资源。
- 新Flyway `070|70|0`，069和070均成功。切换前、切换后及公网检查后的115张原有业务/配置表指纹一致；新凭证表为空，未使用生产账号执行真实上传或资金确认。
- jar SHA256：`7bda25de3c0c044415af341ebd44a039146b676d28ce889a7133e878e2c3ae45`。
- 前端压缩包 SHA256：`b7dcd555534defc6d4670bca797f42b961591c7ee8a28f349304ffb05c56cb01`。
- index SHA256：`6e53444dc0645d171fbcea1d6bac897678b54912ea8c2919a4b9f352293c0b65`。

脚本包含失败自动恢复旧应用路径，实际未触发回退。必要时持同一锁并隔离RPB入口，停止rpb-backend，从备份恢复旧jar、前端及版本标记，确认就绪再开放流量。保留新增表列、凭证和审计，不用旧数据库覆盖发布后的真实记录。整库灾难恢复需留意既有SQL函数的search_path：在受控恢复会话将pg_restore生成SQL中的空search_path改为public，再用ON_ERROR_STOP完整恢复并验证；演练脚本和日志留存在本次证据目录。

## 线上验证

- booking、platform、20000000、sx四域名及12条页面返回本次index，219个静态资源与干净构建逐字节一致。
- 六个原有公开/匿名API响应与发布前一致；原有15项接口/分类访问保护检查通过。
- 新凭证接口13项匿名、缺钱包标记、跨站保护检查全部通过，覆盖顾客context、二维码、列表、原图、multipart提交及员工待确认列表、扫描、原图。
- 最终进程 PID `1161303`，InvocationID `a7334ce9cd594515851ff77b5c9a8352`，active/running，NRestarts0。发布及独立最终检查进程身份稳定，新进程无ERROR日志。
- nginx、PostgreSQL、Redis、Docker的状态、PID、InvocationID及重启计数均与发布前一致；临时隔离规则已移除。
- 未执行真实资金或积分操作，未验证已登录真机交互或真实银行图片识别准确率；OCR仅辅助，仍须商户核对银行实际入账。

## 证据

本地忽略目录 `target/paynow-proof-*` 中的 source-proof.json、artifacts.json、clean-front-tests.log、clean-build.log、clean-package.log、preflight.log、rehearsal.log、rehearsal-v2.log、deploy.log、assets-proof.json、api-proof.json、new-guards.json、final.log。服务端业务指纹保存在备份目录。
