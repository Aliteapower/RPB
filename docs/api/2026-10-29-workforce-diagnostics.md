# Workforce 500诊断协议

本地验收审查修复；沿用所有workforce接口的HTTP状态和{success:false,code:WORKFORCE_PERSISTENCE_ERROR}响应，无业务字段/权限/迁移变化。

DataAccessException或IllegalStateException进入统一500处理时，服务端生成新的UUID，返回X-RPB-Request-Id，并在ERROR日志只记录该ID、HTTP方法、已匹配的路由模板、异常类名、首个SQLException的合法5位SQLState（无则unknown）。不信任请求传入的追踪ID，不记录请求体、实际路径参数、异常消息/堆栈或SQL绑定值。private,no-store不变。403/409等预期业务错误仍按原协议处理。

验证用注入数据库失败和审计失败的HTTP测试，证明每次ID不同、响应最小化、日志可关联且不包含伪造薪资/生日/SQL值；其他workforce HTTP合约回归保持通过。
