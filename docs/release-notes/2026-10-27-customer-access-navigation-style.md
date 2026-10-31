# 顾客账号入口侧栏样式统一

2026-10-27，用户已明确授权提交部署，发布结果另记生产验收记录。

租户后台的到店激活码、忘记密码邮件设置改为使用侧栏既有 nav-item 菜单样式；不再并排显示卡片按钮。共享入口组件通过插槽保留原有权限、门店和地址生成逻辑，其余产品线卡片入口保持原样。

桌面真实组件验收：与顾客管理一致，16px/700 字体、38px 高、左右 12px 内边距、无边框；选中项绿色背景、白色文字。390px 手机下沿用侧栏原有横向滚动菜单，文字不竖排。

89 项前端回归、vue-tsc/Vite 构建、git diff --check 通过；12 项原有无关文件 SHA256 未变。无 API、迁移、权限或业务行为变化；样式变更可直接回退这两个 Vue 文件。

证据：target/customer-access-nav-style-tests.log、target/customer-access-nav-style-build.log、target/customer-access-nav-style/desktop.png、target/customer-access-nav-style/mobile.png。
