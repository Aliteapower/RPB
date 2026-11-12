# 存酒设置内商品维护

存酒设置新增酒品管理入口，在原页面内进入，只维护本店渠道勾选 storage 的商品，不跳到总目录。嵌入原 CatalogPage 和商品编辑器，复用查询、版本/幂等、条码、门店启停及全局状态命令，原商品目录入口行为不变。编辑/全局状态可能影响其他业务或门店，沿用原权限和确认提示；历史存酒快照不回写。

GET /api/v1/stores/{storeId}/catalog/storage-products?query=&page=0&size=12

响应 {items,total,page,size,hasMore}；query 最长120字、按酒名或编号字面搜索，page=0..100000、size=1..100，错误参数 CATALOG_INVALID/400。复用 catalog.view，租户来自员工身份、门店来自路径并校验访问权。筛选本店 channels 包含 storage，不排除本店停用或全局下架商品，以便恢复；在分页前筛选，不受原总目录500项上限影响。使用只读 REPEATABLE_READ 快照确保计数与页一致。无迁移或新增权限。

入口限当前门店的租户管理员且有 catalog.view；catalog.manage 控制编辑及本店启停，catalog.publish 控制全局状态，后台继续各自校验。不会因拥有存酒收存权限就获得商品管理权。编辑中通过原返回列表/放弃修改提示退出，外层返回暂禁用以免丢失草稿或未确认命令。切店和卸载丢弃旧响应。

验证：停用商品仍可查、渠道与门店/租户过滤、搜索/分页、目录500项以外商品、原接口兼容、权限失败；内嵌范围/编辑/启停/撤销/切店、关闭新存酒仍能管理员维护、原子入口紧凑样式及Excel回归。保留所有已有改动，本轮不发布。

实际验证结果：后端 StorageCatalogApiTest、StorageCatalogPostgresTest、CatalogAccessTest 共8项通过，使用本工作区 target/local-postgres-current.txt 指向的61644端口、独立测试 schema；前端 wine-storage-page、wine-storage、storage-merchant-scope 共71项通过；npm run build 与 git diff --check 通过。新增测试覆盖筛选前存在501项无关商品、17项分页、停用及下架商品、字面搜索、参数拒绝、跨店/缺权限/未登录、原商品接口兼容，以及原编辑保存和门店启停命令。

Chrome 本地静态接口夹具使用真实页面组件检查设置入口、列表和原编辑器；319px宽度下列表及编辑器内容宽度均297px，无横向溢出；内嵌只有一个main，编辑时外层返回禁用。该检查未连接生产或真实业务写入，不代表手机真机验收。记录见 target/storage-product-management-20261027/browser-check.json 与 verification.json。

代码复查确认：新增只读接口复用catalog.view及租户/门店访问控制，未新增数据库迁移或写入接口；编辑、版本和幂等控制复用原商品模块。商品全局资料及上下架共享，本店启停影响该商品本店全部渠道，界面提供说明。工作区原有独立文件已核对SHA256保持不变，共享文案/测试文件保留原改动并追加本轮内容。本轮未提交、推送或部署。
