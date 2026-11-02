# 存酒登记选择交互：验证与审查

发布补记（2026-11-02）：已随功能提交`26947810`推送并发布生产，V126。见[实际发布记录](WINE_STORAGE_PRODUCTION_RELEASE_20261102.md)；以下保留实现阶段的验证说明。

日期：2026-11-02；D:/RPB；本地实现未提交、未部署。

## 范围与实现

顾客选择器增加独立clear事件，由useMerchantStorage取消当前选择、清空查询并使旧顾客查找请求失效。父页按顾客ID卸载未提交登记表单，不删除顾客或改写已存记录。

StorageReceiptWinePicker独立负责每项酒品的已有/新增模式、查询、结果点选、重新选择、清空及加载错误。父StorageReceiveForm负责原产品按ID验证、规则、草稿、照片、瓶数、确认及提交。使用稳定行键，直接登记追加空白酒品，待审核拆分保留原lineId。选择和清空会使旧确认失效；重选尚未选定或保留原酒品时禁止提交。清空查找不改变已登记业务数据。

## TDD Review

| 场景 | 证据 | 结果 |
|---|---|---|
| 点选搜索结果直接选酒、无需下拉框 | wine-storage-page.test.mjs | 通过 |
| 清空顾客→空查询/无选中，重选后草稿为空 | clearing a customer returns to an empty lookup… | 通过 |
| 清空酒品重置数量、照片和确认，重新选择可保存 | receipt search results select wine directly… | 通过 |
| 已有/新增明确切换、名称带入、清空查询 | receipt separates new wine entry… / wine search can be cleared… | 通过 |
| 多酒品互不影响，新增从空白开始 | each receipt wine has independent search… | 通过 |
| 重新选择未完成不可提交，保留原酒品可继续 | reselecting requires an explicit choice… | 通过 |
| 失败提示及重试，与无结果区分 | wine search failures explain retry… | 通过 |
| 中文/英文、选定酒品编号及清空操作 | receipt picker exposes English… | 通过 |
| 原停用商品不可收存、按ID保留有效原商品、照片选填、归属/权限及不限时间前端回归 | wine-storage*.test.mjs | 合计99项通过，0失败/跳过 |

命令：`node --test src/test/frontend/wine-storage*.test.mjs`。日志：target/wine-picker-frontend.log。本轮只修改前端；后端和V126保持上一轮已验证状态，不重复运行未受影响的112项后端测试。

## UI / Code Review

参照同模块StorageProductPicker的结果按钮、MerchantStorageDeposit的登记入口及原收存表单。已读取PAGE_LAYOUT_CONSISTENCY_GUIDE.md；复用storage-panel、storage-receive-grid及现有表单外层宽度，不新增独立页面宽度或悬浮保存栏。结果长名称换行，模式操作可换行，窄屏搜索输入独占一行。中文和英文功能用例均通过，但这不等于浏览器视觉验收。

原提交路径、DTO、API校验、租户/门店范围、权限及持久幂等不变，无本轮新增迁移。待审核仍验证原商品是否可寄存，不把失效商品补回可选范围。请求代数防止旧结果覆盖当前结果，卸载使请求失效；按行汇总搜索忙状态，清空和模式切换不发起业务写入。审查未发现阻塞问题。

## 视觉范围与工具限制

建立target/wine-picker-preview的本地合成数据验收页，未使用真实顾客数据，也没有业务写入。内置浏览器CDP导航及绑定超时；进一步尝试本机Chrome时，Computer Use因无法可靠确认当前Windows浏览器网址而停止本轮操作，之后没有继续浏览器操作。因此没有桌面/390px/320px视口、登录后相邻页及实体手机视觉验收证据。已经停止本任务创建的5186预览服务，保留本地验收源文件。

## 构建与交付

最终 `npm run build` 通过（Vue/TypeScript校验及Vite生产资源构建，日志target/wine-picker-build.log）。仅检查本轮及原存酒修改路径的git diff --check，不改无关钱包、员工和未追踪材料。保留未提交状态，未推送或部署生产。
