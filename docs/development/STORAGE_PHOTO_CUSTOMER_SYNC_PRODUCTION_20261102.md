# 存酒照片三端查看发布记录（2026-11-02）

项目 `D:/RPB`，分支 `codex/paynow-payment-product-line-staging`。本聊天入口及存酒体验优化的已有提交、推送和发布授权继续适用。功能提交649c42bf，预览标题补充提交及生产前端 `13cec62704938088badce3864fdc01d95b36fbc9`；后端保持 `0559e4cec0c699fe0c4b9ac40273f52f78539425`。

## 改动

- 顾客“我要取酒”卡片展示商户/员工收存照片；“我的记录”的寄存申请和已结束寄存也可查看对应物品照片。商户/员工的寄存物品明细由普通图片改为同一放大组件。
- 共享StorageItemPhotos及itemPhotos组合员工收存、原申请和该物品后续补充照片，去重；仅读取sourceId相同且action为photos.added的事件。不改写原快照。无图片不显示空图库。
- 复用StoragePhotoViewer的点击预览、继续放大/适合屏幕、原图、关闭/Escape和焦点恢复；标题改为通用“照片预览 / Photo preview”。原照片UUID通过各端自己的api.base读取，顾客使用public/customer身份接口，商户员工使用staff接口。
- 后续照片随既有home查询刷新展示，不增加轮询或业务命令；已有图片不需重新上传。

## 验证与范围

- 失败复现后补实现；最终干净源码120项相关前端测试通过，覆盖员工收存与后续照片、去重/忽略其他物品、放大关闭/Escape、刷新补图、顾客历史及员工记录、无图、无业务POST和既有存取流程。类型检查和构建通过。最后仅预览文字调整复用类型结果，最终Vite构建及120项测试再次通过。
- 指针 `D:/RPB/target/local-postgres-current.txt` 的61644/postgres上，既有真实PostgreSQL照片及租户/门店/顾客隔离用例1项通过。只使用随机自有schema，未改真实订阅或真实照片。后端和图片访问权限没有修改。
- 最终4145个Git blob核对一致，构建排除原有无关前端改动。浏览器合成PNG验证收存/补充两图加载、放大/适配/关闭，桌面1280、390和320视口均无横向溢出；320视口受垂直滚动条影响实际clientWidth/scrollWidth均305，两个缩略图均完整位于内容宽度内。桌面截图已保存，窄屏为DOM/交互验证，不声称保存了窄屏截图。最终通用预览标题再次浏览器核验。
- 生产444个文件逐字节匹配；5域名的85次入口HTML、5次版本和TLS检查通过；6项匿名私有接口401，包含照片读取。没有代真实商户、员工或顾客登录、上传、出码或存取酒；真实设备/微信、真实照片上传及生产私有图片查看未验收。

## 生产与回退

仅发布前端，无API、数据库、配置、资金、库存、权限或迁移变更，不重启后端。后端jar SHA256保持 `2c7dc912aefd66621459956f0c1ca32c0d01eb7bc7bcdfb78e6050239681dd36`，MainPID331593、InvocationID c52a4a02d5bb4893a57226669f07d976、active、NRestarts0均保持。部署后约834MiB可用，未清理旧备份。

备份 `/opt/rpb/backups/20261102-storage-photos-13cec627-before`。回退恢复该目录index.html、frontend-version.json和FRONTEND_RELEASE_SHA；保留旧静态资产，不恢复数据库，不需要重启后端。

证据：`target/storage-photo-release-20261102/` 的source-verification、artifacts、tests-final.log、backend-photo-test、visual-result、PNG、build-final、deploy.log、public-result；只读合成服务已停止、临时标签已关闭、视口已恢复。无关工作区材料保留。
