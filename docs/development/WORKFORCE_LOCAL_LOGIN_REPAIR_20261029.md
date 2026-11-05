# Troubleshooting Report

## Symptom
本地5196测试环境退出后无法重新进入，页面跳转普通登录。

## Evidence
管理员auth/me为401；指针61644的独立fixture schema中会话status=active，但expires_at已过期。fixture账号密码为不可用占位值，不能用普通密码登录。退出接口也会正常注销会话。恢复前登录生命周期回归以401!=200失败。

## Root Cause
本地代理一直复用创建时的4小时会话；角色入口只设置角色cookie，未检查或更新失效会话。正式鉴权行为正确。

## Affected Files
scripts/validation/workforce-proxy.mjs、workforce-fixture-session.mjs、workforce-session-smoke.mjs。无src、迁移或正式配置修改。

## Fix Plan
仅loopback测试角色入口核实本项目指针、fixture schema及身份；会话有效则复用，已过期或注销则创建新的4小时合成会话并保存fixture。旧会话不重新激活，不扩大角色权限。失败返回通用503，不输出凭据。仅重启本轮5196代理，保留8096后端、数据库、原服务和全部业务资料。

## Verification
管理员、经理、员工共9项：进入200、退出后401、重新进入200。初始过期状态已复现并恢复。Node语法及git diff --check通过（既有CRLF提示保留）。浏览器原登录页已恢复排班页面，证据target/workforce-acceptance-20261029/login-restored.png。

## Remaining Risk
仅本机合成账号代理；不可对外暴露。单次会话仍为4小时，之后点击角色入口重新进入。普通登录页不支持这些fixture占位密码。未部署。
