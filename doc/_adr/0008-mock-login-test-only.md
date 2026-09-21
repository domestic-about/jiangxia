---
id: ADR-0008
status: proposed
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "doc/verify/api.sh"
decision:
  key: auth.mock_login
  value: "dev / test 配置下提供 mock 登录（xcxCode 以 mock: 开头时跳过微信换 openid，手机号取请求里的测试手机号），只为 accept 断言与 QA 拿到内部、外部多个身份的 token；prod 配置缺省关闭，且 prod 下若被打开则应用拒绝启动"
  rejected_values:
    - "生产环境也保留 mock 登录入口"
    - "验收断言全部用超管账号跑"
---
# ADR-0008: mock 登录只存在于开发与测试环境

**决策者**: 待 Kevin 过目（AI 给出；dongjiaoshan 一期有同类做法）
**关联**: AUTH-LOGIN-001、AUTH-EXT-001、doc/verify/api.sh 的 `--as` 参数、REQ-SYS-010 的测试账号 seed

## 背景

本项目最要紧的断言是「外部甲看不到外部乙的样本」。这类断言必须同时持有好几个不同身份的 token，
而小程序登录要真实的微信 code，命令行拿不到。

## 决策

按 frontmatter。「生产环境也保留 mock 登录入口」不采纳：等于留了一个不用微信就能登录任意手机号的后门。
「验收断言全部用超管账号跑」不采纳：超管天然全权限，权限类问题在它身上原理上测不出来。

## 后果

- 上线 ticket 的 accept 必须有一条：对生产地址发 mock 登录请求，被拒。
- 测试账号固定在 seed 里：内部 1 个、外部 4 个（同组 2 个、同单位异组 1 个、异单位 1 个），断言里直接按代号引用。
