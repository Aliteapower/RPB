# 员工薪资至付款本地浏览器验收

日期：2026-10-30（Asia/Singapore）；项目：D:/RPB。状态：本地真实浏览器完整办理链路、390窄屏操作及320窄屏复核完成；发现的显示问题已修复。未提交、推送或部署。HEAD仍52d20557。

## 数据与运行时

复用target/workforce-browser-acceptance-20261030/fixture.json及其flow，未重复seed、重置或清理既有数据。数据库依据本项目target/local-postgres-current.txt：127.0.0.1:61644/postgres、postgres/空密码；隔离schema为workforce_browser_e06f3d21e3d5403eafc1bc95536c9c8e。后端8098、环回代理5198，使用本地真实Spring Boot、V107及构建后的Vue页面，不以mock接口代替办理链路。只对该合成租户办理工资；未访问生产写接口。

历史排班与打卡为前轮合成前置数据；本轮复用已有两名员工、模板、岗位默认及一份已保存时薪方案。自由员工个人覆盖方案、两班核准、工资创建/重算/确认/发布、付款及冲销均通过真实页面操作。本轮不声称历史打卡由真机现场采集。

## 实际流程与结果

| 环节 | 本地浏览器实际结果 |
| --- | --- |
| 员工薪资摘要 | 时薪员工显示时薪20、模板验收时薪标准v1、岗位默认v1、固定津贴10、付款偏好现金；原员工草稿保留已在前轮真实页面确认。 |
| 个人覆盖 | 自由员工2026-09-15入职，生效方案显式选择验收自由全额模板，月薪3000、固定津贴200、银行DBS（合成验收）；不改变同岗位另一员工方案。 |
| 工时核准 | 工作台待核准1人/2班，自由免核准1人；直达9月15日迟到班次核准7.5小时，再直达另一门店9月16日核准2小时。迟到30分钟保留事实，无额外扣款。 |
| 工资计算 | 2026-09公司批次51b28bb2-1c59-4e20-82f7-4a4aec22e6bd仅2条工资。时薪基本190+津贴10=200，实际/有效核准9.5小时，两店来源归一行、固定津贴一次；自由基本3000+津贴200=3200，不按月中入职折算。总工资/净额3400，SDL汇总10，雇主总成本3410。 |
| 全量复核及冻结 | 创建预计算v2→全量重算v3，三类金额差额均0；确认v4→发布v5。已发布仍有未付余额，发布不等于付款。 |
| 付款登记 | 时薪先登记现金100，显示部分付款；冲销原100后恢复余额200，保留原记录；200.01超付被既有客户端BigInt守卫拒绝，未发送付款请求或新增记录；再登记现金200。自由员工带出冻结银行资料登记3200。最终4条台账含1条冲销，净登记3400、余额0、已付清2人。 |
| 工资单 | 管理员查看两份工资单，时薪9.5小时/基本190/津贴10/其他扣款0；自由工时不适用/基本3000/净额3200，显示各自付款记录。项目来源可展开核准引用、版本、月份与公式证据。 |
| 本人及权限 | 切换合成员工后没有公司工资管理入口/金额工作台，仅本人1条200元已发布工资单及本人付款记录；直接访问另一员工管理员工资单页面显示无此功能权限，不显示他人工资金额。随后恢复合成管理员。 |

工资JSON文本MD5在confirmed→published→付款/冲销/读取之后均为2b3016a9c0a349372ecb709b538c96f4，原历史快照未改写。最终只读校验脚本verify-final.mjs同时断言两条工资、34200实际/核准秒、津贴一次、扣款0、自由全额、4笔台账及净登记额。证据final-data-verification.json。此哈希仅用于本地合成数据的一致性比对。

## 窄屏与桌面

- 390 CSS像素真实页面操作：员工摘要、个人方案、核准、月批次、工资单及付款/冲销流程；实测390×845，页面没有整体横向溢出，表格在wf-table-wrap中横向滚动。
- Chrome窗口宽度与缩放存在工具换算/最小宽度限制；320使用target内acceptance-frame.html同源iframe装载相同真实应用和API，不更改业务CSS、不mock数据。校准后应用实测320×844。员工摘要（中英文）、核准表单、月度已付清工作台、展开长来源的工资单、付款表单均检查；document.scrollWidth不超过视口，长编号overflow-wrap:anywhere，表格overflow-x:auto，表格工资单操作可通过键盘到达。
- 桌面新标签页实际2687×818，复核同一已发布/已付清批次及金额；最后部分桌面/背景标签页CDP截图采集超时，未以失败截图声称额外视觉签收。已保存有效390/320画面、最终桌面DOM与尺寸；320截图已人工打开检查，未见遮挡/重叠。
- 最终payments-settled-320-evidence.jpg只裁去原截图外侧容器/空白，未改应用内容，原始payments-settled-320-frame.jpg保留。部分早期工具截图存在重复拼接或空白，不能用于断言产品自身重复渲染；真实DOM main=1，最终有效320画面另存。

## 问题、最小修复与验证

1. EmployeePaySummary直接展示rule.basis/payment.method；改用已有usePayrollText，显示时薪/现金及对应英文。
2. PayrollEvidence直接显示approvalState和系统Component.label；按已知状态与系统kind展示中文/英文。自定义津贴名称原样保留，技术来源编号/公式证据不重写。
3. PayrollPaymentSummary将员工资料变化原因标签用于员工列；复用既有工资员工标签，保留来源原因employee原文含义。

仅修改上述3个Vue组件及对应3份组件测试；无API、权限、后端金额公式、迁移或运行时生产配置变化。测试先RED：摘要新增中英文2项失败、原8项通过；来源/表头新增3项失败、原36项通过。修复后最终相关49/49（configuration10、breakdown13、progress26），0失败/跳过；npm run build含vue-tsc --noEmit和Vite通过。命令及日志位于target/workforce-browser-acceptance-20261030：

- node --import ./scripts/tests/workforce-register.mjs --test --test-concurrency=1 src/test/frontend/payroll-breakdown-ui.test.mjs src/test/frontend/payroll-progress-ui.test.mjs src/test/frontend/payroll-configuration-ui.test.mjs：display-green-final.log。
- npm run build：display-build-final.log。
- node target/workforce-browser-acceptance-20261030/verify-final.mjs：最终数据、快照和钱包保留断言通过。
- git diff --check（包含cr-at-eol设置）通过；HEAD52d20557、暂存区为空、原钱包8文件SHA256全部不变。

按ui-review、troubleshooting、tdd-review和code-review做限定范围复核：修复只影响显示、按kind不按用户label猜测，旧JSON不写、权限读写路径不变；未发现必要未修问题。此前完整前端783及分阶段后端结果仅按未改范围复用，不将本轮49项相加冒充全量；本轮没有后端业务变更，未重跑无关后端全库。

## 保留范围与未验收

自由员工全额基本工资、租户/门店/公司权限、历史快照及个人覆盖保留。跨公司工资归属、已发布工资金额更正保持现状。保留全部合成记录、脚本和证据；8098/5198本地服务仍供复核。浏览器视口临时覆盖在收尾重置，原用户生产标签页未作为验收对象操作。

未执行真实银行转账、CPF申报、真实资金、真机定位/打印、Safari等其他浏览器或100/300/500性能验收。登记中的银行及金额均为合成测试数据，不代表真实付款完成。

## 2026-10-31 新增治理／财务版本：HTTP验收与浏览器边界

本节对应D:/RPB治理阶段1–6中的新增源码。上文截图、390/320验收属于其当时的较早源码，**不能作为本轮新增界面的视觉通过证据**。本轮Chrome扩展不可用、IAB CDP超时，原生工具因不能确认浏览器URL停止；因此新增浏览器／窄屏仍未验收。以下是实际本地服务HTTP、只读SQL及真实组件/PG测试，非浏览器操作。

### 冻结阶段5的实际AWS办理链

严格读取target/local-postgres-current.txt，D:/RPB自有PostgreSQL61644。原8098/5198服务和原schema保留。主治理副本8100/5200验证10月计算；因现有规则要求月份结束后才可确认，10月保持未确认，未改变Clock或确认规则。另从原合成数据复制独立财务副本8102/5202，使用相同通过审查的阶段5制品，9月走完整链。

复用原租户、companyB、第三店和既有otherStaff账号；只在财务副本新建测试员工。明确公民/标准GG/不适用CDAC，采用已发布8000固定月薪原样方案，无个人覆盖，符合首次采用免重复审批。AWS10000为虚构合同核定金额，走统一月度申请提交/批准。旧V1未静默升级；缺年度资料时CPF/净额null、已知公司SDL11保留。总部明确选择同年V2，并录入1–8月外部实际/原责任，未来月份由批准方案预测。

| 已实际核对 | 结果 |
| --- | --- |
| 年度OW/年度AW上限 | 96000 / 6000 |
| 9月基本工资/AWS/gross | 8000 / 10000 / 18000 |
| AW计CPF基数/员工CPF/雇主CPF | 6000 / 2800 / 2380 |
| 净额/公司SDL | 15200 / 11 |
| 状态与付款 | calculate→confirm→publish；现金登记15200，重放原付款ID仍仅1行，余额0 |
| 消费及历史证据 | 稳定事件消费1、完整年度冻结评估1；确认至发布/付款的工资JSON MD5保持6e4829e8d64fdc3bc0f3640852caa0d6 |
| 本人接口 | 原已授权员工可读本单；publicAw仅四项金额，内部凭证/分类理由/审批与年度指纹不返回；年度管理接口403 |

随后保存合成离职事实9/30，10–12月由雇佣证据变known-zero：全年OW72000、AW上限30000。年度复核责任差额1480（员工800/雇主680）只生成不可变复核，重放及重读只1记录；原工资净额15200和付款不变，无自动追扣。原完整年度冻结证据仍保留当时10月forecast8000/全年96000，未改写为新事实。法定责任结算／实缴登记属于阶段6，不能由工资付款推断已缴纳。

两副本及原基线的旧已发布工资51b28bb2-1c59-4e20-82f7-4a4aec22e6bd仍为MD5 2b3016a9c0a349372ecb709b538c96f4、付款4行/1冲正/净3400；自由员工全额基本工资3000保留。

证据：target/workforce-template-governance-20261030/financial-http-checkpoint.md、financial-browser/http-aws-calculation-result.json、http-aws-published-paid-result.json、http-aw-reconciliation-result.json、frozen-year-evidence-after-leaving.json及各progress/原始SQL核对结果。runtime-current.json分别记录实际schema/PID/制品hash。所有为合成记录，无真实转账或CPF平台申报。

### 最终阶段6实际HTTP续验（非浏览器）

最终源码task9-base、后端独立Maven55415退出0，447业务文件hash匹配；只切换自有财务副本8102/5202至最终制品，V112固定SHA未改。阶段6及整轮终审修复Spec PASS / Quality APPROVE。先前legacy读取500已修复，原首次GET即按冻结手工无项目证明CDAC0，后续法定事实cdacProfile保持null，不靠补新资格恢复。

| 已实际办理 | 核验结果 |
| --- | --- |
| 年度复核→责任→实缴 | EE800/ER680合1480，独立登记及精确重试；本金0、利息unknown、追回资格800但无自动工资扣回。原不可变复核保留，来源变化时另建当前复核。 |
| 离职后合同奖金→工资单→付款 | AW1000/EE200/ER170/现金800，SDL公司2/个人2.500000/尾差-0.50；部分付款、重试、整笔冲正及再次登记4行，本人安全工资单可读。 |
| 原两店员工漏计→工资单→付款 | 原gross/net200不变；原期OW25独立审批、calculate/confirm/publish、付款25及本人查看，新增CPF/SDL0；旧工资付款4行仍原状。 |

三条实际HTTP JSON通过当前前端guards：14 Detail、7 Evidence、4 Company、2 Remittance、2 Settlement和错误ID拒绝，partial=false。只读核对原net3400/付款4、AWS net15200/付款1、冻结年度forecast96000及完整评估MD5均未变。十月新法定责任EE200/ER170/SDL2仍open，未从工资付款推断实缴。

证据：financial-browser/http-six-reconciliation-result.json、http-six-departed-aw-result.json、http-six-original-omission-result.json、http-six-client-contract-result.json、financial-ledgers-final.json及原progress/响应。首个SDL断言只修复2.500000与2.50的表示比较，从相同ID/命令续跑未重复写；CIM进程查询取消后使用jcmd完整命令行归属核实，仅重启自有后端。

本节仍不构成新增页面的浏览器或窄屏验收，也未执行真实银行、CPF申报或真机操作。最新阶段及能力限制见[本地实施记录](../release-notes/2026-10-31-workforce-template-governance-financial.md)。
