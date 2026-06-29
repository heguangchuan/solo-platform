# Sprint 0 代码 vs wvp-pro 对照评审

> 日期：2026-06-29
> 范围：当前 solo-platform 的 GB28181 模块（仅 Sprint 0 已交付的代码） vs wvp-pro 对应实现

## 总评

参考 wvp-pro 源码后，发现当前 Sprint 0 代码**至少有 8 处影响实际跑通的问题**。这些不是"风格优化"，是"不修就跑不通真实设备"的硬伤。下面逐条列出。

---

## 1. 【高危】Digest 鉴权算法不完整

**当前 solo 代码** (`SipDigestUtils#md5Digest`)：
```java
response = MD5( HA1 : nonce : HA2 )
```

**wvp 实现** (`DigestServerAuthenticationHelper#doAuthenticatePlainTextPassword`)：
```java
// qop=auth 模式（绝大多数国标设备走这个分支）
response = MD5( HA1 : nonce : nc : cnonce : qop : HA2 )
```

**影响**：
- 海康/大华/宇视设备**全部走 qop=auth**，我们这套实现会判定鉴权失败 → 设备永远注册不上
- 因为我们在 401 挑战里就发了 `qop=auth`，设备会按 qop=auth 算 response，但我们按无 qop 校验，必错

**修复**：完整实现 RFC 2617 — 取出 `qop` / `nc` / `cnonce`，按 qop 分支选择算法。可以直接借鉴 wvp 的 `DigestServerAuthenticationHelper`（公有方法 + NIST 源码风格）。

---

## 2. 【高危】没有自定义 SIP 字符集解析器

**问题**：JAIN-SIP 默认用 **ISO-8859-1** 读 SIP 报文，碰到中文 / GB2312 设备名/地址会变乱码（设备目录里的"#1 摄像头"会成乱码字符）。

**wvp 实现**：
- `GBStringMsgParser`：强制按 UTF-8 解码 SIP 行 + header
- `GbStringMsgParserFactory`：实现 `MessageParserFactory`，通过 `sipStack.setMessageParserFactory(...)` 注入

**修复**：必须把这两个类抄过来（接口/类是 JAIN-SIP 公开 SPI，本身是模板代码）。

---

## 3. 【高危】SIP 协议栈关键属性缺失

**当前 solo**（`SipLayer#startSipStack`）只设置了 6 个属性，且部分是错的：
- `AUTOMATIC_DIALOG_SUPPORT=ON`  ← **wvp 是 `off`**！开了之后 INVITE/SUBSCRIBE 的 Dialog 会被自动维护，反而导致重发/状态混乱
- 没设 `DELIVER_UNSOLICITED_NOTIFY` ← 上级平台发不带订阅的 NOTIFY 会收不到
- 没设 `COMPUTE_CONTENT_LENGTH_FROM_MESSAGE_BODY` ← 部分设备 Content-Length 写错时报文会丢
- 没设 `REENTRANT_LISTENER` ← 不能并发处理多请求
- 没用 `NioMessageProcessorFactory` ← 默认是阻塞 IO，大量设备并发会卡

**wvp 必备属性清单**（`DefaultProperties`）：
```java
javax.sip.AUTOMATIC_DIALOG_SUPPORT=off
gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY=true
gov.nist.javax.sip.AUTOMATIC_DIALOG_ERROR_HANDLING=false
gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED=true
gov.nist.javax.sip.DELIVER_TERMINATED_EVENT_FOR_NULL_DIALOG=true
gov.nist.javax.sip.COMPUTE_CONTENT_LENGTH_FROM_MESSAGE_BODY=true
gov.nist.javax.sip.RELEASE_REFERENCES_STRATEGY=Normal
gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT=60
gov.nist.javax.sip.REENTRANT_LISTENER=true
gov.nist.javax.sip.THREAD_AUDIT_INTERVAL_IN_MILLISECS=30000
gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY=gov.nist.javax.sip.stack.NioMessageProcessorFactory
gov.nist.javax.sip.STACK_LOGGER=com.solo.gb28181.sip.SlfStackLogger
gov.nist.javax.sip.SERVER_LOGGER=com.solo.gb28181.sip.SlfServerLogger
```

---

## 4. 【中危】可以省掉 `log4j-over-slf4j` 桥接

**当前方案**：引入 `org.slf4j:log4j-over-slf4j` 让 JAIN-SIP 内部 log4j 1.x 调用转到 SLF4J。

**wvp 更优做法**：实现 `gov.nist.core.StackLogger` 接口（直接桥接到 SLF4J 的 `LocationAwareLogger`），通过 `props.setProperty("gov.nist.javax.sip.STACK_LOGGER", "...")` 注入。

**收益**：
- 少一个依赖
- 日志位置正确（caller location）
- 可以按需开关 SIP 日志（生产关闭，调试打开）

**实现**：参照 `wvp/gb28181/conf/StackLoggerImpl.java`，~130 行。

---

## 5. 【高危】SipLayer 启动时机不对

**当前 solo**：用 `@PostConstruct`
**wvp**：用 `CommandLineRunner` + `@Order(10)`

**为什么 wvp 这么做**：
- `@PostConstruct` 在依赖 Bean 还可能未完全装配时就跑了
- `CommandLineRunner` 保证 Spring 上下文已经完全 ready
- `@Order(10)` 控制启动顺序（后于 DB/Redis 等基础设施）

**还有**：wvp 支持**逗号分隔多 IP 监听**，自动检测本机所有非 loopback / 非 docker 网卡。我们这个版本只支持单 IP。

---

## 6. 【高危】REGISTER 处理器漏掉关键场景

**当前 solo `RegisterRequestProcessor`** 只处理两种情况：
1. 无 Authorization → 401 挑战
2. 有 Authorization → MD5 校验 → 200/403

**wvp 处理器多了 4 个关键分支**：

**分支 A：注册续订**（同 Call-Id 重复 REGISTER，**不走 401 挑战**）
- 设备每小时会主动重新 REGISTER 续订，如果每次都挑战会增加无谓握手
- 用 `device.sipTransactionInfo.callId == request.callId` 判断是续订

**分支 B：注销（Expires=0）**
- 当前代码处理了，但流程不对 — 注销不应该走 Digest 挑战；wvp 在最开始就判断 Expires=0，直接走注销逻辑

**分支 C：NAT 真实地址**（从 `Via.received/rport` 取，回落到 `request.peerPacketSource`）
- 设备如果在 NAT 后面，`From` 头里的 IP 是内网 IP，必须取 NAT 后地址回包

**分支 D：响应里要带 `Date` 头**（GB-SIPDate 格式）
- jain-sip 默认 `SIPDate` 格式 `Mon, 29 Jun 2026 23:30:00 GMT`
- 国标要求 `2026-06-29T23:30:00.000`
- 部分设备用这个头校时，缺失会导致设备显示时间错乱

**分支 E：设备级密码**
- 每台设备可以单独配置密码（数据库字段），平台默认密码是回落

---

## 7. 【中危】MESSAGE 处理器太简陋

**当前 solo**：直接在 `MessageRequestProcessor.process()` 里 `switch(cmdType)` 分发，硬编码所有 Cmd 类型。

**wvp 设计**：
- 用一个**两级 Map**注册：`MessageType → CmdType → Handler`
- 每个 `<Cmd>` 子命令是独立的 `IMessageHandler` 实现（一个文件一个）
- 用 `@InitializingBean#afterPropertiesSet()` 自动注册到父处理器

**为什么这么做**：
- GB28181 的 `Cmd*Type` 有 30+ 种（Catalog、DeviceInfo、DeviceStatus、RecordInfo、Alarm、MobilePosition、Broadcast、Snapshot、MediaStatus、PresetQuery、Bookmark、Subscribe…）
- 每个 cmd 有自己的 XML 结构、数据库表、业务流程
- 全堆在一个 switch 里两周后没人能维护

**还有更隐蔽的坑**：
- **海康设备发 Keepalive 时 From 字段不是设备 ID**，而是通道 ID
- wvp 通过 `SsrcTransaction` 反查"当前 Call-Id 关联的是哪个 deviceId"来兼容
- 我们当前代码直接用 `From.user` 当 deviceId，海康设备的 Keepalive 会全部丢

---

## 8. 【中危】Keepalive 处理没用队列削峰

**当前 solo**：每收到一个 Keepalive 就同步 `updateById`，1000 台设备同时心跳会打爆 DB。

**wvp 实现**：
- `ConcurrentLinkedQueue` 接收
- `@Scheduled(fixedDelay=100)` 定时器每 100ms 批量处理
- 队列满（默认 50000）时拒绝新消息

**修复**：照搬 wvp 模式，简单可靠。

---

## 9. 【低危】XML 解析没有海康兼容处理

**当前 solo `XmlUtils`**：用 `DocumentHelper.parseText` 直接解析。

**海康的两个坑**：
1. **`Address` 字段含 `<` 字符**破坏 XML 结构 → wvp 检测到解析失败后兜底删除 `<Address` 整行重试
2. **未转义 `&` 字符** → wvp 扫一遍 raw bytes，把不在 `&lt;/&gt;/&amp;/&apos;/&quot;` 白名单内的 `&` 移除

我们当前实现会**完全失败**，整条 Catalog 报文丢弃。

---

## 10. 【中危】回复响应没经过统一 sender

**当前 solo**：直接 `serverTransaction.sendResponse(...)`。

**wvp 实现**：所有出站消息（Response 或主动发起的 Request）走 `SIPSender#transmitRequest(localAddr, msg)`。

**为什么**：
- 多 IP 监听时要找到正确的 Provider 回包（同一台设备只能用接收方网卡回）
- 统一打日志（出向报文）
- 后续做 SIP Subscribe 回调时（异步等响应），需要这个统一入口

---

## 必须重写/补强的清单

| # | 类 | 严重度 | 动作 |
|---|---|---|---|
| 1 | `SipDigestUtils` | 高危 | 重写：支持 qop=auth + cnonce + nc |
| 2 | 新增 `GbStringMsgParser` + `GbStringMsgParserFactory` | 高危 | 抄 wvp（UTF-8 解析） |
| 3 | 新增 `DefaultSipProperties`（拆出来） | 高危 | 13 个关键属性配置 |
| 4 | 新增 `SlfStackLogger` + `SlfServerLogger` | 中危 | 替代 log4j-over-slf4j 桥接 |
| 5 | `SipLayer` | 高危 | 重写：CommandLineRunner / Order / 多 IP / NIO |
| 6 | `RegisterRequestProcessor` | 高危 | 重写：续订/注销/NAT/Date/设备级密码 |
| 7 | `MessageRequestProcessor` + 新增 `IMessageHandler` 接口 + 各 cmd handler | 高危 | 重构为两级分发 |
| 8 | `KeepaliveNotifyMessageHandler`（替代当前内联逻辑） | 中危 | 队列削峰 |
| 9 | `XmlUtils` | 低危 | 海康兼容（未转义 & + 残破 Address） |
| 10 | 新增 `SipSender` | 中危 | 统一出站入口 |
| 11 | 新增 `GbSipDate` | 中危 | 国标时间格式 Date 头 |
| 12 | 新增 `SipRequestProcessorParent`（抽象基类） | 中危 | responseAck / getRootElement 等共用方法 |

新增 / 重写 13 个类，**保留 1 个**（`Gb28181Properties`、`GbDevice` 实体可以保留）。

---

## 重写后的包结构（替换当前）

```
com.solo.gb28181
├── config
│   └── Gb28181Properties.java         保留（已实现）
├── sip
│   ├── SipLayer.java                  重写
│   ├── SipProperties.java             新增（SIP Stack 属性工厂）
│   ├── SipProcessorObserver.java      增强（Call-Id 订阅回调机制）
│   ├── SipSender.java                 新增
│   ├── parser/
│   │   ├── GbStringMsgParser.java     新增
│   │   └── GbStringMsgParserFactory.java  新增
│   └── log/
│       ├── SlfStackLogger.java        新增
│       └── SlfServerLogger.java       新增
├── auth
│   └── DigestServerAuthenticationHelper.java   新增（替代 SipDigestUtils）
├── processor
│   ├── SipRequestProcessor.java       保留（接口）
│   ├── SipResponseProcessor.java      保留（接口）
│   ├── SipRequestProcessorBase.java   新增（共用 responseAck/getRootElement）
│   └── request/
│       ├── RegisterRequestProcessor.java   重写
│       ├── MessageRequestProcessor.java    重写（两级分发）
│       └── message/
│           ├── IMessageHandler.java
│           ├── notify/
│           │   └── KeepaliveNotifyMessageHandler.java   新增
│           └── （后续 Sprint 再加 Catalog/DeviceInfo/Alarm…）
├── utils
│   ├── SipUtils.java                  增强（NAT 地址、Subject 解析、PTZ 控制码）
│   ├── XmlUtils.java                  增强（海康兼容）
│   └── GbSipDate.java                 新增
├── entity / mapper / service / controller / request   保留
```

---

## 建议执行顺序

如果同意按 wvp 经验重做 Sprint 0：

1. **第一步（基础设施）**：SipProperties、GbStringMsgParser、SlfStackLogger、SipSender、Util 类 → 编译通过
2. **第二步（REGISTER 链路）**：DigestServerAuthenticationHelper、SipRequestProcessorBase、RegisterRequestProcessor → 设备能注册
3. **第三步（MESSAGE 链路）**：MessageRequestProcessor 两级分发、IMessageHandler、KeepaliveNotifyMessageHandler → 心跳能落库
4. **第四步**：清理废弃类（旧的 SipDigestUtils、SipFactoryHolder、log4j-over-slf4j 依赖）
5. **第五步**：写一份"如何用 sipp 或国标模拟器自测"的脚本

预计代码量：**新增/重写 ~13 个文件，约 1500 行代码**。
预计耗时：1 天可以做完，3 天内能用真机/模拟器跑通注册。

---

## 是否同意按这个计划重做

请你拍板：

- **方案 A（推荐）**：按上面清单全部重写 Sprint 0。耗时 1 天代码 + 2 天调试。
- **方案 B**：只修最严重的 3 项（Digest 鉴权、Parser、SIP 属性），其余暂缓。耗时半天。
- **方案 C**：保持现状，进入 Sprint 1，把这些坑留到出问题时再改。**强烈不推荐**。
