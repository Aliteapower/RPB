# 顾客存酒与储值钱包入口边界

- GET `/api/v1/public/stores/{storeId}/wine-storage/home` 追加只读布尔值 `walletEnabled`，来自当前租户和门店wallet的AppGate商业判断。顾客存酒可用不意味着钱包可用；门店不匹配沿用原403/404。既有字段、身份校验和酒水只读/新业务规则不变；旧客户端忽略新字段，旧服务未返回字段时新客户端隐藏返回钱包。
- GET `/api/v1/public/stores/{storeId}/wallet/me` 在顾客身份及门店归属验证后，如果当前钱包商业不可用、该店未曾开通且本人没有该钱包计划的历史账户，返回403，code=`WALLET_PRODUCT_UNAVAILABLE`。不能仅凭顾客注册或存酒产品进入空钱包首页。已有本人历史账户、既有开通过但关闭/过期的门店继续原只读查询，原资金/积分/历史不改写。
- 普通钱包首页在context.denialReason非空时，不挂载运营面板，显示不可用说明和显式 `transactions?history=1` 入口；历史列表、详情及原单善后仍由各原接口校验，不借history标记绕过权限。
- 顾客注册、到店激活和密码恢复独立于钱包开通；不新增权限、接口方法、迁移或写操作。错误响应沿用钱包 `{code,message}`。
- 从存酒进入共享顾客注册时保留本门店 `returnTo=/wallet/{storeId}/wine-storage`；注册页返回与登录成功继续存酒。仅允许当前门店的已注册公开路径，不接受外部网址、其他门店或任意路由。
