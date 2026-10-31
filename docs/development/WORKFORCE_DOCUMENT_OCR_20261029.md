# 员工证件批准日期及本地OCR验收

2026-10-29，D:\RPB，仅本地实现和验证，未提交、推送或部署。

## 行为

沿用员工证件页面、提醒维护弹窗及权限。新增证件持有人姓名、批准日期和独立的正式PR取得日期，旧记录默认空值，未推断回填。姓名只属于证件，不改员工主档；旧客户端省略新增字段时保留原资料，新表单可明确清空。

上传仍先将图片重编码压缩至1MB以内，PDF上限5MB。上传成功后自动调用本地Tesseract，PDF经本地pdftoppm渲染前2页。识别姓名、号码、批准日期、有效期及明确正式Entry Permit的签发日期。候选默认全部不选，用户核对勾选、填入编辑表单、保存后才落库。失败保留已上传附件，可手工填写和重新识别。

工资SPR方案可明确选择已维护PR取得日期的证件；工资公司授权后仅取得脱敏尾号和必要日期元数据，不提供附件。普通批准日期、IPA、REP或护照日期不直接作PR取得日期。修改或删除已引用证件日期产生CPF_DOCUMENT_REVIEW_REQUIRED，阻止最终确认，不能通过员工自改证件自动改变工资；已确认工资快照不改。旧手工PR日期继续兼容，换员工时清空前员工PR日期。

SDL沿用按工资计算的规则，不按证件批准日开启或关闭。月中首次取得PR需要分段工资的原有限制保持。

## 结构与约束

- documents继续负责证件存储、字段、版本、员工/门店授权和审计；V100只新增元数据列及日期先后约束。
- documents/ocr独立负责候选解析、受限执行和识别编排；Controller仅转发。未耦合付款识别规则，无新增独立业务页面。
- 识别前后复查权限、证件版本、附件存在和hash；不长时间持有数据库事务。错误代码和接口见 docs/api/2026-10-29-workforce-document-ocr.md。
- 并发2、总deadline59秒、stdout64KiB、渲染目录32MiB，失败销毁子进程并清理临时目录，不输出证件原文或stderr到日志。
- 系统不确定的日期、多个冲突候选及互相矛盾的PR/批准/到期日期保留人工核对；CRLF标签换行兼容。

## 验证

- 前端178项真实组件与相关回归测试全部通过，含显式选择、不能静默覆盖、上传失败重试、识别失败保留附件、切店/员工迟到结果、版本变化、PR引用确认和旧手填日期清空。npm run build（vue-tsc+Vite）通过。
- OCR后端26项通过：parser6、service9、engine9、API2；含真实Tesseract合成PNG，以及真实三页合成PDF仅识别前两页。无跳过。
- 主体后端首轮97项中发现1项旧证件版本回归：附件上传后旧版本编辑被误当幂等成功。已恢复原策略（创建命令可重试，过期编辑409），原证件PG19项和本轮元数据/工资引用PG5项复测全部通过；首轮其余用例通过，包括继承的工资流程、证件到期提醒、生日配置、CPF计算及API/安全。
- 工资引用验证包含：批准日不等于PR日、证件归属校验、请求日期与保存日期相同、员工自改日期只阻断复核不重写原CPF、证件删除、冻结工资不变、工资元数据证件号脱敏。
- 浏览器真实上传合成JPG压缩至67KB，自动识别5字段；保存前API确认字段仍为空。勾选填入并保存后，姓名、号码、2025-09-20批准日、2025-10-01正式PR日和2030-10-01到期日均正确落库。
- 真实HTTP验证PDF识别、前2页提示、无原文泄漏、版本409、跨站403、非法日期400、员工本人读取及识别、跨店拒绝、工资选项脱敏及原手工客户端保留新字段；7组检查通过。
- 浏览器检查工资引用只带入正式PR日而非批准日，要求明确核对Entry Permit；取消表单，未保存或改变原工资方案。
- 自有合成证件验收后软删除，未删除原记录。对26类原业务表逐行哈希比较全部不变，包括员工、原证件/附件、打卡、核准、工资、付款及现有CPF/SDL政策历史。

证据目录：target/workforce-document-ocr-20261029/，包含frontend-final.log、build.log、backend-tests.log、backend-final.log、runtime-api.json、before-review.json、runtime-cleanup.json、data-before/after.json、recognition-review.png、document-saved.png及cpf-date-reference.png。OCR专属surefire报告位于target/surefire-reports/。首次无上限JVM出现本机内存分配失败，限制Maven 512MB和测试JVM 256MB后通过；诊断文件已保留到本证据目录，无删除。

## 本地环境与恢复

读取target/local-postgres-current.txt，确认runtimeWorktree=D:\RPB、port=61644；仅向自有 workforce_browser_6b3d0ca974744b0c9b681036f8462dd6 验收schema应用V100。后端8096、代理5196；未操作其他运行实例或生产数据库。

本地Tesseract已存在eng/chi_sim。配置可选WORKFORCE_OCR_TESSERACT_CMD，Windows默认标准安装位置，其余系统默认tesseract。PDF配置WORKFORCE_OCR_PDFTOPPM_CMD；本次专用后端启动时指向本机现有poppler绝对路径，未写入业务源码、全局环境或生产配置。工具缺失/损坏/超时返回可手填提示，上传仍保留。以后重启本地验收后端时需再次提供PDF命令环境变量；生产是否安装/配置工具需另行验证和授权。

## 官方日期依据与限制

- CPF采用正式Entry Permit注明的PR取得日期，第一/二年周年次月换档：https://www.cpf.gov.sg/service/article/i-have-successfully-applied-for-a-new-re-entry-permit-and-retained-my-singapore-permanent-resident-status-how-will-this-impact-my-cpf-contributions
- ICA取得日期位置：https://ask.gov.sg/ica/questions/clxwhkiyp00i0dsothovwhiqn
- SDL涵盖本地/外籍员工并按工资计算：https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy

合成样本验收不代表所有真实手机照片或证件排版都能准确识别。字体S识为$等情况不会猜测修正证件号；仍需对照原件人工核对。PDF只识别前2页，其他页面人工维护。

## 审查

独立审查的PR/到期冲突、CRLF标签解析两项均先RED后修复GREEN，复核APPROVE。旧手填PR日期切员工残留由专属真实UI测试先RED后修复GREEN。未发现剩余阻断问题；不构成生产发布验收。
