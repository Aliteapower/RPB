# 存取酒查询报表

## 版本与范围

2026-11-03，D:/RPB，分支codex/paynow-payment-product-line-staging。沿用本聊天验证、提交、推送、发布生产的明确授权。功能提交 `5c0efc9b9ec3e90c934bd7a4c62e442bd4e6f01d`。

## 调整

商户、员工端原“记录”改为“报表”，独立查询门店存酒、取酒记录。无需先确认顾客，不出现新增顾客、办理存取酒、追加纠错或补图按钮。支持报表类型、顾客姓名/手机号/酒品/位置/标签、状态、起止日期，默认全部期间；按门店时区解释日期，结束日完整包含。

一笔申请或取酒单一行，酒品与数量合并显示，不把多条操作日志当成多笔业务。汇总为全部筛选结果的单据数、完成单数与实际收/取瓶数，不是当前库存。待处理、取消、过期记录不计入实际瓶数。服务端每页20笔，稳定时间/id排序；查询条件变化重置页码。点详情查看原申请、实收、酒品、照片、保管人与日志，只读且保留原事实。顾客端记录不变，现有库存与受控交付规则不变。

新增GET /reports及/reports/{kind}/{id}，接口契约见docs/api/WINE_STORAGE_REPORTS.md。现有Home默认兼容；报表页使用includeRecords=false仅加载页面上下文，避免为分页报表先下载全店历史。正常办理页切回时恢复完整Home。

## 验证与审查

- 干净归档4169个Git blob逐项一致；前后端完整构建通过。136项关联前端测试通过，包括无顾客选择/创建、条件筛选、服务端分页、汇总、切店/查询的迟到响应、照片放大、原始证据、错误重试、英文及既有取酒车回归。不声称全项目测试通过。
- 28项关联后端测试通过，包括10项真实指针PostgreSQL测试（其中3项新报表，其余为继承库存/目录回归）、5项新报表权限/API/日期边界/明细范围测试、Home上下文与历史门禁、安全及模式回归。指针target/local-postgres-current.txt端口61644/postgres；原本已停止，按项目快速重启指南恢复现有集群，测试只用随机自有schema，未改真实订阅。
- 代码审查检查租户+门店SQL范围、wine_storage.view、当前或历史App Gate、非法参数、404、只读事务与一致性汇总；报表无公开顾客端接口，不返回取酒码秘密。现有GET精确本地白名单扩展不增加公开业务权限。无迁移、无新依赖、无权限配置变化。
- 干净构建中14个本次后端类文件与实际测试产物逐字节一致；基于生产原jar仅替换本次类，实际13个条目变化，所有其他类、依赖、配置及127个迁移文件字节不变，避免换行和无关重编译差异带入生产。
- 本轮浏览器工具返回“Local browser use is disabled in Work settings”，未能执行桌面/手机渲染和真实账号视觉验收，无新截图。已做组件行为、响应式CSS源代码和构建检查，不能等同实际手机显示通过。合成只读预览进程已停止。

## 发布、风险与回退

生产发布结果及公共资源核验以target/storage-reports-release-20261103内deploy-result、public-result为准。本次后端需要一次RPB服务重启，无数据库迁移、业务写入、库存扣减或历史覆盖。保留工作区其他钱包、员工门店及未跟踪资料，不打包这些改动。

回退恢复 /opt/rpb/backups/20261103-storage-reports-5c0efc9b-before 中原jar、index.html、frontend-version.json、RELEASE_SHA、FRONTEND_RELEASE_SHA，只重启RPB并验证401及版本。旧静态资源保留，无需恢复数据库。

证据目录target/storage-reports-release-20261103包含source-verification、build、backend-build、tests、backend-test-result、tested-class-verification、artifacts、review-readiness、visual-result和发布核验结果。实际渲染和真实账号使用仍是未验证范围。

## 生产结果

前后端版本均为5c0efc9b9ec3e90c934bd7a4c62e442bd4e6f01d。后端jar SHA256为0a98c8f15c329442645a49b85e918f799903a85d522a5503c4420e8dd80e1a10；MainPID501640，InvocationID4d411c88031e41929c5d239da78a212f，active/running，NRestarts0。264张业务表在停止服务后的切换前后指纹一致，schema仍127|127|0，环境、nginx配置与邻居服务保持；启动日志无ERROR。发布后可用空间521924608字节，未清理历史备份。

生产444静态文件逐字节匹配、5域名85次HTML入口、5次版本/TLS检查及9项匿名401通过，含新报表及明细接口；匿名产品访问摘要为空，未开通预订门店context仍404。匿名检查不等同真实账号报表交互，未声称实体手机或私有页面视觉验收。
