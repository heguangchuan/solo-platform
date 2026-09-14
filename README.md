# Solo 视频监控平台

基于 **GB/T 28181-2016** 国标协议的视频监控接入与管理平台：自研 SIP 协议栈接入国标设备，集成 ZLMediaKit 完成流媒体分发，内置完整的 RBAC 权限后台。一套代码覆盖**设备接入 → 实时点播 → 录像回放下载 → 云台控制 → 多级级联**的完整链路。

## 功能特性

### 视频监控（GB/T 28181 国标）

- **设备接入**：SIP 注册（RFC 2617 Digest 完整 qop=auth 鉴权）、心跳保活、在线状态实时维护（Redis TTL + 定时对账）
- **通道目录**：注册后自动拉取 Catalog，分片合并入库，支持超大目录；设备信息（厂商/型号/固件）自动同步
- **实时点播**：主/子码流切换，UDP / TCP 被动 / TCP 主动三档收流；HTTP-FLV / WS-FLV / HLS / WebRTC 播放；按需拉流（无观看自动断流）
- **历史回放**：录像查询、时间轴回放、暂停/续播/拖动/倍速（SIP INFO + MANSRTSP）
- **录像下载**：DOWNLOAD INVITE 倍速下载，MP4 落 S3 兼容存储，预签名 URL 直接下载
- **云台控制**：八方向 + 变倍 + 聚焦 + 光圈 + 雨刷；预置位管理；巡航路线（巡航点编排、速度/停留时间、启停）
- **报警与移动位置**：设备报警、GPS 位置上报解析与事件总线
- **主动检测**：设备存活 fire-and-forget 探测，三态回调校准在线状态

### 国标级联（双向）

- **作为上级**：下级平台白名单管理，下级注册后视为设备接入，通道目录自动入库，可直接点播/回放下级通道
- **作为下级**：注册保活到上级平台（401 Digest 应答、掉线重注册），资源共享目录树勾选推送，响应上级 Catalog 查询（分片），被上级点播时经 ZLM sendRtp 推流

### 流媒体（ZLMediaKit 集成）

- **多 ZLM 负载均衡**：Redis ZSet 按活跃流数选点，SSRC 池 Redis 化，跨 JVM 安全
- **拉流代理**：外部 RTSP/RTMP 拉入 ZLM 转推，启停管理、播放地址生成
- **推流管理**：推流只读视角 + 推流方 token 鉴权，僵尸流自动对账清理
- **自动点播**：播放地址被访问但流不存在时自动补 INVITE

### 电子地图首页

设备/通道树 + 全屏电子地图，通道定位、点击播放，深色监控风格。

### 系统管理（RBAC 底座）

- 用户 / 角色 / 菜单权限（按钮级），JWT 双 Token 认证，强制改密
- 数据字典、参数配置、操作日志、登录在线用户
- 服务器管理：Web SSH 终端（JSch）、服务器监控
- 文件管理：S3 兼容对象存储
- 站内消息：WebSocket 实时推送

### ONVIF 模块

ONVIF 摄像头维护与实时点播，手写 SOAP（dom4j + Java 21 HttpClient），无 JAX-WS 历史包袱。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1 · Java 21 · MyBatis-Plus 3.5 · JAIN-SIP · jjwt · dom4j |
| 前端 | Vue 3 · TypeScript · Vite 6 · Element Plus · Pinia · ECharts · Leaflet |
| 基础设施 | MySQL 8 · Redis · ZLMediaKit · S3 兼容对象存储 |

## 快速开始

### 环境要求

- JDK 21+、Maven 3.8+、Node.js 18+、MySQL 8+、Redis 6+
- ZLMediaKit（视频功能必需，可用 Docker 部署）

### 1. 初始化数据库

三个脚本共用同一个 MySQL 库，按需安装：

```bash
mysql -uroot -p < sql/solo_db.sql     # 必装（系统 + 权限）
mysql -uroot -p < sql/gb28181.sql     # 国标模块
mysql -uroot -p < sql/onvif.sql       # ONVIF 模块（可选）
```

### 2. 配置后端

编辑 `solo/src/main/resources/application.yaml`：

- `spring.datasource` / `spring.data.redis`：数据库与 Redis 连接
- `gb28181.*`：SIP 端口（默认 5060）、平台国标编码、域、注册密码
- `media.*`：ZLM 节点地址 / 密钥 / 点播超时 / SSRC 段

### 3. 启动后端

```bash
cd solo && ./mvnw spring-boot:run
```

服务端口 `10003`。启动后 SIP 同时监听 UDP/TCP 5060，等待 ZLM hook 回调。

### 4. 启动前端

```bash
cd solo-ui && npm install && npm run dev
```

访问 `http://localhost:3000`，`/api` 已代理到 `localhost:10003`。本地体验账号 `admin / admin123`（生产环境请务必修改）。

### 5. 接入摄像头

将国标设备（海康/大华/宇视等）的 SIP 服务器指向部署机 `IP:5060`，协议 ID 填 `application.yaml` 中的 `gb28181.id`，设备注册成功后自动拉取通道目录，即可点播。

## 项目结构

```
solo-platform/
├── solo/                  # Spring Boot 后端（端口 10003）
│   └── src/main/java/com/solo/
│       ├── system/        # RBAC / 字典 / 文件 / 消息 / 日志 / 服务器
│       ├── gb28181/       # 国标 SIP 协议栈与设备/级联/回放/云台业务
│       ├── media/         # ZLM 客户端 / Hook / SSRC 池 / 拉流代理 / 推流
│       ├── onvif/         # ONVIF 摄像头
│       ├── aop/           # 权限 / 操作日志 / 限流 / 防重复提交
│       ├── config/        # JWT / Redis / MyBatis-Plus / 安全头
│       └── websocket/     # 消息推送 / Web SSH 终端
├── solo-ui/               # Vue 3 管理台（开发端口 3000）
├── sql/                   # 数据库初始化脚本
└── lib/                   # 本地依赖
```

## 文档

- [gb28181.md](gb28181.md) — 国标模块 Sprint 计划与 41 条 ADR 架构决策记录（动手改国标代码前必读）
- [CLAUDE.md](CLAUDE.md) — 仓库约定速览（日志规范、包结构、SQL 流程）

## 路线图

- [x] 设备接入 / 实时点播 / 回放 / 下载 / 云台 / 级联
- [x] 多 ZLM 负载均衡 / 拉流代理 / 推流鉴权
- [ ] 报警中心（落库 + WebSocket 推送 + 前端处置）
- [ ] 录像计划（按通道周计划定时录制）
- [ ] 监控运维大屏（设备/流路/ZLM/SSRC 指标）
- [ ] 一键部署（docker-compose：ZLM + Solo + MySQL + Redis + S3）

## License

[Apache License 2.0](LICENSE)
