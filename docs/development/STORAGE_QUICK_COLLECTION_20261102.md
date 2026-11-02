# 快捷存取模式：验证和审查

日期2026-11-02；项目D:/RPB，本地工作区未提交。

## 需求与范围

默认模式不要求顾客扫描授权，门店直接核对顾客、酒品、瓶数和实物后确认交付。顾客仍能查本人记录、申请存酒、出示取酒码；普通顾客码由通用扫一扫识别后转入存酒/取酒。保留“直接登记”模式对顾客自助入口的既有区别。无生产发布授权。

## API Review

- POST /direct-deliver扩展为两模式均可新执行，使用原wine_storage.deliver及顾客归属检查；返回completed、code=null，按原命令键持久重放。
- POST /intents/{id}/deliver允许staff/awaiting_authorization在店员明确交付后完成；customer来源仍按原authorized状态检查。取消、完成、超时和库存/期限变化仍拒绝；同键重放不重复扣减。
- 原authorize接口保留兼容，实际调用才记录顾客authorized事件。新UI不创建商户授权码，不要求顾客扫码；恢复的旧单不显示授权QR但可由商户确认交付。
- /scan-deliver保持仅customer来源的取酒码可直接核销，普通顾客码不作为扣减令牌。无新增接口、DTO字段、权限或数据库迁移。契约已更新WINE_STORAGE_API.md。

## TDD Review

| 场景 | 用例 | 状态 |
|---|---|---|
| 默认模式直接交付、本人自助申请仍可用、记录实际模式及幂等重放 | quickModeAllowsDirectDeliveryAndRetainsCustomerSelfServiceAndReplay | 通过 |
| 默认模式拒绝过期、超量、错误顾客及无权限 | directDeliveryRejectsExpiredOverdrawForeignCustomerAndDeniedStaff | 通过 |
| 旧商户单无需authorize可完成、不伪造顾客授权、不可重复交付 | legacyMerchantIntentCanBeDeliveredWithoutCustomerScanButStillExpires | 通过 |
| 整批回滚、并发不超扣、原扫码取酒及多租户/门店/产品权限 | Storage*Test | 通过 |
| 默认模式无授权扫码步骤、范围及瓶数正确、确认前不写入、双击只提交一次 | wine-storage-page.test.mjs | 通过 |
| 顾客记录/申请/取酒码保留、身份码识别后快速存酒、旧单恢复、双语设置 | wine-storage-page.test.mjs | 通过 |
| 原不限时间及清除顾客/选酒流程回归 | wine-storage*.test.mjs | 合计102项通过，0失败/跳过 |

前端命令：node --test src/test/frontend/wine-storage*.test.mjs；日志target/wine-quick-mode-frontend.log。后端命令：mvn -o -Dtest=Storage*Test test；使用target/local-postgres-current.txt指定61644端口，在隔离测试schema执行，不修改实际业务数据。构建日志target/wine-quick-mode-build.log。

## Code / UI Review

实现仍由StorageWithdrawalService编排，原StoreScope、权限、事务、幂等仓库和库存行锁不变。只扩展staff待处理单可交付条件，没有把任意非authorized顾客单放行。新直接交付沿用既有锁顺序；旧单不增加逆序settings锁。直接办理及旧单完成均记录真实店员事件；原intent.created改为记录实际模式，避免默认模式直接办理被错误写成staff_registration。

商户取酒组件统一使用既有直接确认交付流程，移除新建授权码及等待顾客授权的轮询；旧单仍可恢复、取消和交付。保留身份及瓶数确认步骤。顾客端移除扫码确认入口，不删除历史授权审计。复用现有Layout/面板/按钮，无新增宽度、CSS或浮动操作栏。

桌面、390px/320px登录视觉及实体手机未验收；上一轮浏览器工具已因无法可靠确认Windows浏览器网址终止操作，本轮未重复该受阻路径。组件测试不视为浏览器视觉证据。

## 构建与最终结果

完整npm run build通过（Vue/TypeScript检查及Vite生产构建）；后端mvn -o -Dtest=Storage*Test test合计112项通过，0失败/错误/跳过；范围内git diff --check通过。未改无关钱包/员工工作区材料。保留本地未提交状态，未推送、未部署生产。

最终审查结论：APPROVE_WITH_COMMENTS。必要行为及相关前后端测试通过；登录后桌面和手机视觉验收尚未完成。无生产部署。
