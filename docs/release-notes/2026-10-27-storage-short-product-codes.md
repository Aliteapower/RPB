# 寄存酒短编号与默认分类

2026-10-27，本地实现，尚未提交部署。

存酒酒品管理新增时，编号可留空，保存事务内自动分配1至5位数字（1..99999）；也可填写自己的编号，沿用原80字符上限。界面显示生成时机和手填提示。分类默认选本租户启用且中文名称精确为“酒水”的分类；没有则手选，不创建分类。默认瓶、默认寄存酒渠道及原商品总入口行为保留，编辑既有商品不覆盖单位/分类/编号。

收存时自动建立的寄存酒商品也复用新短编号分配，替代WS-UUID。目录持有租户级事务锁，自动和手填新建共用锁；候选排除本租户全部已有商品（含归档），原unique(tenant_id,code)作为最终约束。幂等重放返回原结果，不再生成；短号用完返回CATALOG_CODE_EXHAUSTED/409并提示自定义编号。历史WS编号及寄存快照保持不变。

无数据库迁移、索引、权限注册、依赖或环境变化。沿用catalog.manage和租户管理员/门店访问控制，原商品编辑、条码、审计及存酒事务边界保留。Reservation、Queue、Walk-in、Seating、Cleaning业务未改。回退本轮应用与前端即可，保留已建立的短编号商品，勿恢复旧库覆盖交易。

验证：本工作区target/local-postgres-current.txt指向61644端口，隔离schema中执行StorageProductCodePostgresTest、CatalogProductEditPostgresTest、StorageProductEligibilityTest、StorageCatalogApiTest、CatalogAccessTest共29项通过；覆盖真实SQL并发、幂等、原子编辑、短号耗尽、跨租户候选隔离、自定义编号及冲突拒绝。78项前端回归、vue-tsc/Vite构建及diff检查通过。只读权限及原商品接口兼容沿用现有回归。

本地应用内浏览器真实组件/静态接口夹具确认“酒水”选中、编号非必填及生成提示；319px下clientWidth=scrollWidth=305。证据target/storage-short-codes-20261027/，截图new-wine.png。该检查没有生产写入，不等同手机真机验收。

API/数据库/代码复查：写入仍在目录边界，所有商品新建路径覆盖同一事务锁，号码查询带tenant_id，分配器拒绝事务外调用；无跨租户默认分类查找，不回写历史编号。12项无关工作区文件SHA256保持不变。尚未提交、推送或部署。

后续授权发布：2026-10-27以da729ee9同时上线前后端。干净源码78项前端回归与构建、JAR目录模块差异校验及线上只读检查通过，数据库版本保持V084。完整发布记录见docs/development/STORAGE_SHORT_PRODUCT_CODES_RELEASE_20261027.md。
