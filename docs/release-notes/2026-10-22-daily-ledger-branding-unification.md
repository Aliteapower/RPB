# 每日营业记录品牌入口去重

2026-10-22，本地完成；未提交、未发布。线上版本仍为6ddda281。

## Changed

账本菜单改为“记账设置”，去掉展示名称、英文名称、账本名称及Logo上传/移除等重复编辑项。弹窗只维护底钱、币种、餐段、最终统计班次、非现金与开支项目，并提供租户管理员专用的“前往租户资料”入口；其他门店管理员提示联系租户管理员。

账本名称读取现有租户资料中当前门店的displayName，Logo读取现有租户共享媒体。旧英文副标题和原始branding JSON保留兼容，不重写营业记录。新Logo只在租户资料维护，清空后不会恢复旧图。移除重复UI后顺便删除了对应无用主题规则。

## API / Migration

接口契约见 [品牌收敛契约](../api/DAILY_LEDGER_BRANDING_CONTRACT.md)。不新增表或Flyway迁移。账本设置PUT不再写branding；新增AppGate/view保护的当前Logo只读接口，普通员工无需租户管理权限即可查看，但不能改共享Logo。

旧数据须在发布前运行一次性迁移进程：profile=`ledger-branding-import`，属性`rpb.daily-ledger.branding-tenant-code=<tenant-code>`，使用目标数据库与媒体存储配置、正常认证、127.0.0.1随机端口。成功关闭自身上下文。操作复用现有媒体上传服务，保留原图、标记已接入统一配置；重复执行不再上传，管理员清空后不重新采用旧图。多个门店旧图不同时，未配置共享Logo则拒绝自动选择。有共享Logo则保持共享图不被覆盖。新建原站快照导入也调用此流程。

本轮仅对D:\RPB指针指定PostgreSQL的daily_ledger_validation_20261022执行sx兼容迁移，无生产数据操作。

## Validation

- 47项Java定向测试通过：5项品牌来源与资源作用域、3项API/AppGate、9项实际PostgreSQL、5项金额规则、2项导入Runner、19项AppGate服务与4项拦截回归；无失败/跳过。
- 9项前端行为测试、Vue类型检查与构建通过。
- 实际本地API检查：共享图清空后账本为空，旧资源404；恢复后图片字节一致；共享名称修改后账本同步，已恢复原名称；旧客户端branding覆盖被忽略。迁移前后所有daily_ledger_records完整行指纹一致。
- 桌面浏览器：弹窗没有上传控件，只有一个记账设置标题；资料链接跳转成功，租户资料显示迁入的原Logo；账本侧栏读取共享Logo URL且图片加载成功。
- 手机浏览器：375px内容宽下弹窗内容无内部横向溢出，底钱/币种及餐段可读；临时视口已恢复。
- 证据在忽略目录target/daily-ledger/branding-settings-deduplicated.png与branding-settings-mobile.png。本地预览为http://127.0.0.1:5179，独立后端8182，不替换原8081服务。

## Review / Risk

按UI、API、TDD与代码审查核对：共享配置继续由tenantadmin模块维护，dailyledger只通过应用服务读取；迁移职责位于migration包，只显式运行，不在GET中写数据。新媒体接口有tenant/store与当前资源ID双重限定。旧设置版本CAS和冻结账目不变。无阻塞问题。

未改平台租户资料的上传/清空业务。若后续发布，必须先采用旧Logo再切换页面；存在多门店品牌冲突的租户需先选择共享Logo。应用回退保留新媒体及原branding JSON，避免丢图；不删除账目或媒体文件。
