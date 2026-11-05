# 商品分类产品线与寄存选酒分页

日期：2026-11-02；本地实现，不包含提交、推送或生产发布。

- 分类新增 `productLines: string[]`，使用实际产品线标识 `wallet/wine_storage`（钱包积分/酒水寄存）；`cards/rewards/shopping` 渠道归属 wallet，`storage` 归属 wine_storage，可多选。分类管理显示全部归属，寄存业务仅显示 enabled 且包含 wine_storage 的分类。
- POST `/api/v1/stores/{storeId}/catalog/categories` / PUT 同路径 `/{id}` 显式指定至少一个有效产品线；旧客户端省略字段时新增拒绝，更新保留原归属。分类已有关联商品渠道时不得取消对应产品线（409 CATALOG_CATEGORY_IN_USE）。分类管理仍使用 catalog.view/global catalog.manage 权限与现有乐观锁、幂等命令。
- 商品保存/门店渠道更改校验分类覆盖全部选定渠道所属产品线（400 CATALOG_INVALID）；分类归属不赋予产品线订阅或权限。
- 新增店员 GET `/api/v1/stores/{storeId}/wine-storage/product-options`，参数 query(<=120)、categoryId(可选UUID)、page(0..100000)、size(1..12，默认6)。返回既有顾客选酒格式 `{items,hasMore,page,categories}`；wine_storage.view、租户/门店范围不变。过滤在 SQL 分页前，排序分类顺序/编号/ID，空结果200。旧 products 列表接口兼容保留。
- 前端选酒使用6条分页，应用查询条件与输入草稿分离；每行独立页码/分类，搜索与清除回第一页，切门店/卸载使旧响应失效。加载/错误状态不显示过期页或“新增酒品”误导。
- V127 根据所有门店商品渠道及既有寄存规则/申请行补齐旧分类范围；无依据为待设置。无删除、不改历史快照。新增数据库默认空数组；测试和业务必须显式设定归属。保留历史商品关联，禁止不安全移除。
- 验证覆盖：多页无重漏、条件过滤、无关分类隔离、非法参数、越权与跨租户、取消在用范围、并发写入锁、前端翻页/条件重置/延迟响应。

分类保存成功时共享目录发出 categories-changed 通知，寄存设置刷新自己的 home 数据，新增分类可立即用于保存规则；不重新载入浏览器页面。分类管理页不显示商品查询或商品分页。
