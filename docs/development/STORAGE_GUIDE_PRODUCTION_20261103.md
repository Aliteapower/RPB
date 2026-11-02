# 酒存分步操作指南发布

2026-11-03，D:/RPB，分支codex/paynow-payment-product-line-staging。沿用本聊天持续的提交、推送及生产发布授权。功能提交与生产前端 `7d8fad91914cb12a7785715e6a3eb4a4dd411aed`；后端仍5c0efc9b。

## 内容与入口

酒存→设置新增“操作指南”卡，提供“查看操作指南”和“下载PDF（中文）”。页面版按任务选择，只展示当前任务3–4个步骤，中英文随页面语言切换；返回原设置与返回存酒继续沿用现有页面。中文PDF为两页A4，适合下载、打印或分享。

指南覆盖直接收存、审核顾客申请、门店批量取酒车、顾客码核销、库存与历史报表、管理员开店准备，共6任务21步骤；3条异常提示说明未确定结果先恢复、检查门店/开通/权限、规则保留历史快照。关键区别明确：门店提交整车即确认交付并扣库存；顾客提交整车仅生成码，店员扫码核销才扣库存，扫描成功即整车核销。无业务行为修改。

页面与PDF共用src/modules/wine-storage/guide/content.json，重建脚本scripts/docs/build_wine_storage_guide.py。PDF和源内容摘要由guide-zh.manifest.json校验，防止版本漂移。PDF嵌入中文子集字体，静态通用说明不含顾客/租户私有信息。无需新增运行或构建依赖、API、数据库迁移或权限配置。

## 验证与范围

- 使用PDF技能，ReportLab生成，PyMuPDF全文抽取及两页渲染。6任务/21步骤/3提示正文均完整，中文字体嵌入，逐页图像检查无截断或溢出。图像证据target/storage-guide-pdf-review/page-1.png、page-2.png。
- 138项关联前端测试通过；新增管理员与员工指南入口、任务选择/返回、语言切换、只读无POST、下载链接及PDF内容摘要一致性。类型检查、完整构建及干净归档4178个Git blob核对通过，归档测试再次138项通过。
- 最终构建只产生一个PDF资源assets/guide-zh-BRtyAR52.pdf，字节与已审查源PDF一致，且被员工页面chunk引用。生产445文件逐字节匹配、5域名85次入口HTML、5次版本/TLS、9项匿名401及5次PDF字节核查通过。下载地址https://booking.yumstone.sg/assets/guide-zh-BRtyAR52.pdf。
- 当前Work设置禁用本地浏览器使用；未重新执行实际网页、桌面/手机视觉验收或iOS点击下载。PDF文档本身已渲染目视验收，与网页渲染验证明确区分。HTML及资源可达不等于真实登录交互。
- 无后端变更，本轮未重复数据库或后端测试。无关钱包、员工门店改动及未跟踪资料保留且未打包。

## 发布与回退

只发布前端，后端jar SHA256保持0a98c8f15c329442645a49b85e918f799903a85d522a5503c4420e8dd80e1a10；MainPID501640、InvocationID4d411c88031e41929c5d239da78a212f、active、NRestarts0保持不变，无后端重启、库存或历史写入。发布后可用空间478945280字节，未清理备份。

备份/opt/rpb/backups/20261103-storage-guide-7d8fad91-before；恢复其中index.html、frontend-version.json及FRONTEND_RELEASE_SHA可回退5c0efc9b。旧静态资产保留，不恢复数据库或重启后端。部署有独占锁、前版本与index/hash检查、后端状态守卫和失败恢复。

证据target/storage-guide-release-20261103：source-verification、tests、build、artifacts、review-readiness、deploy-result及public-result。代码审查APPROVE、生产就绪GO，保留上述网页视觉验收缺口。
