# 酒水寄存操作指南

在酒存设置新增只读操作指南入口与中文PDF下载。页面按六个任务选择显示3–4步：直接收存、审核申请、门店取酒车、顾客码核销、库存/历史报表、管理员准备；中英页面共用版本化JSON，中文2页PDF由同源内容生成并校验指纹。无业务API、库存、权限或规则变化；原设置表单和回退边界保持。

验证页面选择/返回/双语言/下载链接只读、PDF源摘要与全文完整/字体嵌入/2页渲染、前端类型及干净归档构建、生产PDF与资源逐字节核验。当前Work设置禁用浏览器自动化，页面真实渲染和实体手机下载未验收；PDF已本地渲染目视检查。沿用本聊天提交推送发布授权，仅发布前端。

重建PDF：python scripts/docs/build_wine_storage_guide.py --font C:/Windows/Fonts/msyh.ttc。需要reportlab及PyMuPDF，仅作者工具依赖，不增加应用或构建依赖。更新content.json后必须重建PDF和manifest；storage-guide.test.mjs防止页面/PDF内容版本漂移。
