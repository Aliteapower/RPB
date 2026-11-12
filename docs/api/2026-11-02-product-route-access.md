# 产品路由访问摘要

GET `/api/me/apps?storeId=<uuid>&includeAccess=true` 保留原apps/historyApps，并按需追加 `productAccess:[{appKey,businessAllowed,historyAllowed,historyRoute,denialReason}]`。默认不请求额外摘要，兼容旧客户端。摘要不使用入口显示开关代替商业授权，角色和门店取真实员工/管理员会话；无有效员工身份、跨租户或无门店范围不返回资格。顾客不使用该接口。

businessAllowed由产品商业状态与本人的相关产品权限计算；管理配置的既有管理员资格不因缺少收银/预约新建权限被收窄。historyAllowed由注册的历史权限及AppGate.evaluateHistory计算；完整路径来自代码目录，不能接受数据库任意跳转。公司工资、个人记录、来源门店与原单所有权仍由业务服务逐项验证。

前端摘要只决定是否挂载业务/历史界面，不能替代命令API授权。没有摘要或读取失败时不挂载受控业务；独立history=1只能进入声明的只读路径，不能授权写入。租户资料、开通管理、共享账号/注册不受单产品业务页门禁影响。
