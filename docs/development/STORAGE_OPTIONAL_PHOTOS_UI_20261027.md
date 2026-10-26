# 存酒照片选填与收存表单优化（2026-10-27）

## 范围与实现

- 顾客申请、再次寄存、商户收存和直接代登记均可传空照片数组并保存；上传时仍检查格式、体积、数量、重复、租户、门店、顾客、阶段和照片复用。专门补拍照片的接口仍要求至少一张。
- 后端新增 requireOptional 明确表达选填策略，不放宽原 require；申请与收存调用选填策略，保留原事务、授权、规则、幂等和历史快照。
- 收存表单按“选择酒品 → 填写存放信息 → 核对并保存”组织；已有酒品优先，未收录酒品展开填写，明确至少一项酒名及分类。
- 为酒瓶状态、酒品来源补标签；新酒品选择分类后自动带出适用规则，暂无规则时提示联系管理员。
- 照片标为选填并解释可不上传；位置、标签、备注合并折叠；增加款数/瓶数汇总和未完成必填事项提示。直接登记不显示不存在的原申请瓶数。
- 沿用本店默认位置、规则同意和实际收存确认；未改库存、有效期或核销模式。中英文文案同步，既有自填数据不翻译覆盖。

## API / Database Review

仅放宽明细 photos 空数组校验，null 和非法图片仍拒绝。端点、DTO、权限和 AppGate 不变；无数据库迁移，历史照片不修改。契约补充于 docs/api/WINE_STORAGE_API.md。

## TDD / 排错

- 先添加无照片商户登记、顾客提交及必填提示用例，前端首次 42 通过、2 失败；后端因尚无 requireOptional 编译失败。
- 加入 PostgreSQL 的无照片申请/收存/重放/重新寄存和商户直接登记用例，以及保留补拍照片至少一张、同意确认的回归。
- 首次后端 42 项中 41 项通过；新再次寄存用例误申请 2 瓶而之前仅领取 1 瓶，被原数量校验正确拒绝。只将测试夹具改为 1 瓶，定向重跑通过；没有修改数量业务校验。
- 旧前端断言仍期待“填写瓶数并拍照”，随新选填文案更新。

## 最终验证

- 后端：42 个不同用例均有通过证据（首次 41 个，修正夹具后定向重跑 1 个）。命令：mvn -q '-Dtest=StoragePostgresTest,StoragePhotoPolicyTest,StorageAccessTest,StorageRequestSecurityTest' test；定向重跑 StoragePostgresTest#applicationReceiptAndRedepositCanOmitPhotos。使用 D:\RPB\target\local-postgres-current.txt 的 61644 端口，隔离测试 schema，不改业务 schema。
- 前端：node --experimental-transform-types --test src/test/frontend/wine-storage-page.test.mjs src/test/frontend/wine-storage.test.mjs src/test/frontend/storage-locations.test.mjs src/test/frontend/storage-customer-selection.test.mjs src/test/frontend/storage-merchant-scope.test.mjs：54 项通过。
- npm run build：vue-tsc/Vite 通过。git diff --check 通过。
- IAB 浏览器在本机 127.0.0.1:5211 使用内存模拟接口检查实际构建：393px 与约 322px 窄屏、中英文，无横向溢出；未上传照片、选酒并确认规则后保存按钮可用。没有连接生产或模拟实际实物/金融交易。
- 截图：target/storage-optional-photo-20261027/receipt-mobile.png、receipt-mobile-detail.png、receipt-english-narrow.png。截图展示模拟布局，不代表手机相机或真实后端浏览器验收。
- 12 项原有无关文件 SHA256 与此前保留快照一致。

## UI / Code Review

- 必填事项、选填项、保存结果意图与服务端契约一致。查找、加载、上传、缺少酒品/规则/同意和失败恢复都有对应状态；加载或上传期间仍禁止提交。
- 控制器、权限、租户门店边界不变，未涉及其他模块或数据库结构。
- 只读历史照片及传入照片的安全校验不变。当前未发现未解决的 P0/P1。
- 尚未执行生产发布及手机实机验收；此次保持本地改动，未提交或推送。
