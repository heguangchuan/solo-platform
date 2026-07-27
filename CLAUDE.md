# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库布局

双模块 monorepo，根目录没有构建工具：

- `solo/` — Spring Boot 4.1 后端（Java 21，Maven）。运行端口 `10003`。
- `solo-ui/` — Vue 3 + Vite + TypeScript 管理台。开发端口 `3000`，`/api` 反代到后端。
- `sql/` — 数据库初始化脚本：`solo_db.sql`（主库必装）、`gb28181.sql`（启用国标时装）、`onvif.sql`（启用 ONVIF 时装）。三份物理分离但共用同一个 MySQL 库，见 `gb28181.md` 的 ADR-010。
- `lib/` — 本地依赖 `onvif-ws-client-1.0.1.jar`，以 `com.solo:onvif-ws-client` Maven 坐标引入。

## 常用命令

后端（`solo/`）：
- 运行：IDE 里启动 `com.solo.SoloApplication`，或在 `solo/` 下执行 `./mvnw spring-boot:run`。
- 打包：`./mvnw -f solo/pom.xml package`
- 跑单个测试：`./mvnw -f solo/pom.xml test -Dtest=SomeTest#someMethod`

前端（`solo-ui/`）：
- `npm run dev` — Vite 开发服务器，`/api` 代理到 `localhost:10003`。
- `npm run build` — 先 `vue-tsc --noEmit` 再 `vite build`，类型检查随构建一起跑，没有独立 lint 脚本。

数据库初始化（当前阶段不维护增量迁移，按 ADR-010 直接改主文件、本地重建库）：
```
mysql -uroot -p < sql/solo_db.sql     # 必装
mysql -uroot -p < sql/gb28181.sql     # 启用国标模块时装
mysql -uroot -p < sql/onvif.sql       # 启用 ONVIF 模块时装
```

按用户偏好：**未经明确要求，不要执行编译 / 构建 / 测试命令。**

## 后端架构

根包 `com.solo`。子包按**领域**组织，不按分层：

- `system/` — RBAC（用户 / 角色 / 权限）、字典、配置、文件（MinIO）、消息、日志、任务、服务器。表全部 `sys_*`。
- `ai/` — AI 能力（LLM / 图像 / 视频）。`ai/llm/` 下的 `LlmClientRegistry` / `ImageGenerationClientRegistry` / `VideoGenerationClientRegistry` 按 model 选实现。表 `ai_*`。
- `gb28181/` — GB/T 28181 国标 SIP 模块（详见下节）。表 `gb_*`。
- `onvif/` — ONVIF 摄像头维护 + 实时点播（客户端主动探测流派，不涉及 SIP）。表 `onvif_*`。手写 SOAP 通过 dom4j + Java 21 `HttpClient` 实现，故意不引用 `lib/onvif-ws-client-1.0.1.jar` 里的 JAX-WS stub —— 那批 stub 依赖老 `javax.xml.bind`，与 Jakarta 生态冲突。
- `media/` — ZLMediaKit 集成，供国标与 ONVIF 复用：REST 客户端（含 `openRtpServer` / `closeRtpServer` / `addStreamProxy` / `delStreamProxy`）、Hook 接收、SSRC 池、`gb_media_server` CRUD、`MediaUrlBuilder`、`MediaServerBootstrap`、`MediaServerSelector`（挑一个可用节点，跨模块共享）。
- `aop/` — 横切注解：`@RequirePermission`（RBAC 权限校验）、`@OperationLog`、`@RateLimiter`、`@NoRepeatSubmit`。
- `common/` — `Result` / `PageResult` / `ServiceException`。**所有 Controller 一律返回 `Result` / `PageResult`；业务错误抛 `ServiceException`**，由 `config/GlobalExceptionHandler` 统一翻译。
- `config/` — 全局基础设施：`WebMvcConfig`、`JwtInterceptor`、`RedisConfig`、`MybatisPlusConfig`、`AsyncConfig`、`SecurityHeadersFilter`、XSS advice、MinIO、Scheduling。
- `task/` — 动态定时任务引擎：`TaskHandler` 实现注册进 `TaskHandlerRegistry`，`DynamicTaskScheduler` 按 `sys_task` 行调度。
- `websocket/` — `SystemMessageWebSocket`（消息推送）、`TerminalWebSocket`（基于 JSch 的 Web SSH 终端）。
- `tests/` — 开发期诊断接口，**不是**单元测试。

持久层：MyBatis-Plus 3.5.15。逻辑删除字段 `deleted`。**所有表都没有物理外键**，跨表引用只在应用层做（ADR-011）。

鉴权：JWT 双 Token（access + refresh，`jjwt` 0.12.6），由 `JwtInterceptor` 校验。权限码形如 `gb28181:device:list`、`system:user:edit`，`PermissionAspect` 读 `@RequirePermission` 做拦截。

## GB28181 模块（`com.solo.gb28181`）

自成一体的 GB/T 28181-2016 实现，用于视频监控设备接入。**动手前先读 `gb28181.md`**，它是 Sprint 计划与 ADR 记录的唯一权威源。当前进度（截至 2026-07-27）：S0 / S1 / S2.1 / S2.2 / S3（回放）已完成（设备注册、目录同步、按需实时点播、历史回放）。拉流代理、推流管理、资源目录、设备主动检测等计划外功能也已闭环。云台（S4）、报警（S5）、级联（S6）未启动。

`gb28181/` 分层：

```
sip/           JAIN-SIP 协议栈启动（SipLayer / SipProcessorObserver）
processor/
  request/     入站 SIP 请求：REGISTER / MESSAGE / INVITE / ACK / BYE ...
    message/   MESSAGE 按 (rootType, cmdType) 两级分发：Keepalive / Catalog / DeviceInfo / RecordInfo / Alarm / MobilePosition ...
  response/    出站请求的应答处理
transmit/cmd/  出站 SIP 命令构造器（SipCommander 家族）
event/         内部事件总线（DeviceOnlineEvent / DeviceOfflineEvent ...），驱动异步管线
auth/          RFC 2617 完整 Digest 鉴权（qop=auth 分支，见 ADR-006）
config/        Gb28181Properties + JAIN-SIP 装配
task/          定时对账（扫 Redis 判离线）
utils/         SipUtils、XmlUtils（含海康 `&` 和 `<Address` 兼容）、DigestServerAuthenticationHelper
```

**关键约束，除非在 `gb28181.md` 里补写 ADR 否则不要打破：**

- **禁止阻塞 SIP 监听线程**。所有 handler 分发到异步线程池；心跳排队 100 ms 批处理 200 条一批（ADR-007）。
- **JAIN-SIP 栈必须无条件设置两个属性**：`gov.nist.javax.sip.STACK_LOGGER=Slf4jStackLogger` 和 `SERVER_LOGGER=Slf4jServerLogger`。任一漏设启动即 `NoClassDefFoundError: org/apache/log4j/Priority`。`sipLog` 只控制 `LOG_MESSAGE_CONTENT`（是否打印报文体），不控制这两个属性是否设（ADR-001）。
- **自定义 `MessageParser`**（`GbStringMsgParser`）强制 UTF-8 解析头 / 首行——JAIN-SIP 默认 ISO-8859-1，中文 `name` / `address` 会乱码（ADR-009）。
- **Digest 挑战必须带 `qop=auth`、`cnonce`、`nc`**——海康 / 大华 / 宇视都走这条分支，简化版 `MD5(HA1:nonce:HA2)` 全线失败（ADR-006）。
- **设备在线状态双写**：MySQL `gb_device.status` 供查询，Redis TTL Key 才是权威。`@Scheduled` 每 30s 对账（ADR-005）。
- **所有 `gb_*` 表都不建 `FOREIGN KEY`**，跟主库一致（ADR-011）。跨表引用用 `device_db_id` 这类逻辑字段。
- **日志前缀走业务语义**，不用类名。按 `gb28181.md` §4 的分类表用（`[设备注册]`、`[协议栈]`、`[消息分发]`、`[实时点播]`、`[流媒体]` ……）。新增领域时同步扩展那张表。

流媒体接入：SIP `INVITE` 流程先经 `media/ssrc/SsrcFactory` 分配 SSRC（按 ZLM 节点分桶），调 `media/client/ZlmRestClient.openRtpServer`，发 INVITE，等 ZLM 的 `on_stream_changed` Hook（`media/hook/ZlmHookController`）确认流就绪，然后用 `MediaUrlBuilder` 组装 HTTP-FLV / WS-FLV / HLS / WebRTC 播放地址返回。按需拉流（`gb28181.media.stream-on-demand`）在无人观看时自动回收；自动点播（`auto-apply-play`）在播放地址被访问但流不存在时自动补一次 INVITE。

`application.yaml` 是运行时配置的唯一源：`gb28181.*` 段管 SIP 身份 / 计时器，`gb28181.media.*` 段管 ZLM 节点 / 点播超时 / SSRC / 录制开关。

## 前端要点

- Element Plus + 自动导入（`unplugin-auto-import` / `unplugin-vue-components`），`ElButton` 之类不用手写 import。`components.d.ts` 和 `auto-imports.d.ts` 是生成物，不要手改。
- `src/stores` 放 Pinia store，`src/router/index.ts` 是路由，`src/api/*.ts` 与后端 Controller 一一对应。
- 视图目录跟后端领域对齐：`views/System`、`views/Gb28181`、`views/Ai`、`views/Task`。
- 组合式函数在 `src/composables`，自定义指令（如 `v-permission`）在 `src/directives`。

## 值得记住的项目约定

- 表命名：`sys_*`（核心系统）、`ai_*`（AI 能力）、`gb_*`（国标）、`onvif_*`（ONVIF）。新模块请自选前缀并保持独立。
- 后端包结构领域优先：新功能通常展开为 `com.solo.<domain>/{controller,service,mapper,entity,request,response}`。
- 当前开发阶段，SQL 直接在 `sql/solo_db.sql` 或 `sql/gb28181.sql` 里改；**不要**建增量迁移脚本（ADR-010）。
- 数据权限走 SQL / Service 内部处理——没有部门 / 岗位模型，也没有独立的登录日志功能（记忆 `admin-feature-scope`）。
