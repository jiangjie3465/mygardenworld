# Android App 开发文档

本文件是 `android/` 原生客户端的执行计划和进度记录。与 `AGENTS.md` 冲突时，以本文件中的 Android 任务为准，并同步修正 `AGENTS.md`。

## 1. 已确定边界

- Android 16 及以上，`minSdk = 36`，`compileSdk = targetSdk = 36`。
- Kotlin + Jetpack Compose，单 `app` 模块，用包名表达分层（见第 5 节）。
- App 只连接独立部署的桌面/服务器 `gardend`，不在 Android 上运行 daemon、游戏连接或自动化逻辑。
- 后端继续监听 `127.0.0.1:50051`，由 Caddy/Nginx 提供 HTTPS 入口（`deploy/caddy/`）。
- Web 保持现有 Cookie 登录流程；移动端使用独立的 token 流程（`MobileLogin/MobileRefresh/MobileLogout`）。
- Android 端 Protobuf 代码在 Gradle 构建期从仓库根 `proto/` 生成（Java Lite），不提交生成代码。
- `debug` 构建允许 cleartext HTTP（如 `http://10.0.2.2:50051`）和用户安装的 CA；`release` 构建只信任系统 CA 且强制 HTTPS。
- 先用本机现有 android-37.1 模拟器（`xiaomi_Medium_Phone`）开发验证，最后用 Redmi K100 Pro 真机验收。
- 不修改游戏协议、runner、automation 和状态权威逻辑。

## 2. 当前进度（2026-09-03，第二次更新）

### 已完成

后端（提交 `af3e471 feat(auth): add mobile token sessions`、`20a50fb docs(deploy): add caddy https edge example`）：

- `AuthService.MobileLogin / MobileRefresh / MobileLogout` 已加入 `auth.proto`，Go 与 TS 生成代码已更新。
- `refresh_tokens` 表新增 `client_type / device_id / device_name / last_used_at / created_at`，schema 升级到 v7。
- 移动端 refresh token 通过响应体返回并每次轮换；`ValidateRefreshTokenForClient` 阻止 Web 与移动端 token 互换。
- 移动端 RPC 加入公开过程白名单，复用登录限流器。
- 测试：移动端登录成功/失败、设备字段校验、refresh 轮换保留设备信息、MobileLogout 只撤销移动端 token。
- `deploy/caddy/Caddyfile.example` 提供 HTTPS 边缘示例。

后端 P0 补齐（本轮，未提交）：

- `AuthService.ListMobileSessions / RevokeMobileSession` 与 `MobileSession` 消息；只列出/撤销当前用户自己的移动端会话，需要 Bearer 认证。
- 测试：v6 → v7 迁移；移动端 refresh 重放、过期、Web token 混用、设备撤销、用户禁用；会话列表标记当前设备且隐藏他人会话；跨用户撤销返回 NotFound。
- 所有 `.proto` 增加 `java_package = "com.mygardenworld.v1"` 与 `java_multiple_files`（与 buf managed 默认一致，Go/TS 描述符无变化）。
- `Makefile` 新增 `android:test / android:lint / android:build / android:check`。
- `AGENTS.md` 更新为 schema v7，并增加 Android 章节。

Android P1～P3（本轮，未提交）：

- Gradle Wrapper 9.3.1；`app/build.gradle.kts` 自定义 `generateProto` 任务用 Maven 上的 protoc 4.36.1 在构建期生成 Java Lite；`trimCatalog` 任务从 `web/src/lib/game/catalog.json` 生成精简目录到 assets。
- `network_security_config`：debug 允许 cleartext + 用户 CA，release 只信任系统 CA；`BuildConfig.ALLOW_INSECURE_ENDPOINTS` 控制地址校验。
- `core/network`：`ConnectClient`（`application/proto`、Bearer、401 单次刷新重放、错误 JSON 映射、传输错误映射）、`TokenAuthority`。
- `core/auth`：`TokenStore` 接口 + `KeystoreTokenStore`，`AuthSession`（登录/恢复/刷新合并/退出，`AuthState` 流）。
- `core/protocol`：`WorkspaceSocket`（OpenWorkspace 首帧鉴权、sequence 校验、resync、日志游标、4401 刷新重连、指数退避）、`WorkspaceSequenceTracker`、`WorkspaceStateMerger`、`WorkspaceRepository`（快照/补丁/日志分页归并）。
- `core/ui`：小云朵主题（浅/深色）、`SectionCard`、`StatTile`、`Badge`、`SettingRow/SwitchRow/NumberRow`、`Format`（移植自 Web dashboard-utils 的中文标签）。
- `feature/auth`：登录页与连接测试；`feature/accounts`：账号列表、启动/暂停/停止、新增账号（iOS 表单、Alipay 二维码 + 进度）；`feature/workspace`：基础/花园/订单/日志/设置五个 Tab，自动化开关、登录/断开/删除、策略编辑与保存；`feature/settings`：服务信息与退出登录。
- 前后台生命周期：`AppContainer` 通过 `ProcessLifecycleOwner` 在后台断开 WebSocket，回前台自动重连。
- JVM 单元测试 25 个：URL 归一化、Connect 客户端（MockWebServer）、AuthSession（刷新合并/拒绝/传输失败）、序列跟踪、状态归并、日志分页归并。

模拟器冒烟（android-37.1 `xiaomi_Medium_Phone`，本地 `gardend` 通过 `http://10.0.2.2:50051`）：登录成功、账号列表与 WebSocket 已连接、设置页显示服务端版本、进程重启后会话自动恢复、Alipay 二维码可获取并渲染。未验证：带真实游戏账号的工作区页面、真机、HTTPS 证书链。

### 与本文件的差距

- [ ] 未在模拟器上用真实游戏账号验证基础/花园/订单/日志页面渲染和策略保存。
- [ ] 第二期功能（第 8 节）全部未开始。
- [ ] 设备会话查看/撤销页面未实现（后端 RPC 已就绪）。
- [ ] 日志页尚未实现 Web 的“竞赛同步日志折叠”。
- [ ] release 签名配置与 R8 规则未配置。

## 3. 后端和协议

### 已有的移动端认证协议

```text
AuthService.MobileLogin(MobileLoginRequest{username, password, device_id, device_name})
  -> MobileLoginResponse{access_token, refresh_token, access_expires_at, refresh_expires_at, user}
AuthService.MobileRefresh(MobileRefreshRequest{refresh_token})
  -> MobileRefreshResponse{同上}
AuthService.MobileLogout(MobileLogoutRequest{refresh_token})
```

不变量：

- refresh token 每次刷新轮换，旧 token 立即失效，并发重放只能成功一次。
- `client_type` 区分 `web` 与 `mobile`；两类 token 不能互换。
- `device_id` 由 App 首次安装生成随机 UUID，不使用硬件标识。
- refresh token 不写入日志。
- Web 的 `Login/Refresh/Logout` Cookie 流程保持不变。

### 待补充的设备会话 RPC

```text
AuthService.ListMobileSessions(ListMobileSessionsRequest{})
  -> ListMobileSessionsResponse{repeated MobileSession sessions}
AuthService.RevokeMobileSession(RevokeMobileSessionRequest{session_id})
  -> RevokeMobileSessionResponse{}

MobileSession:
  int64 id
  string device_id
  string device_name
  google.protobuf.Timestamp created_at
  google.protobuf.Timestamp last_used_at
  google.protobuf.Timestamp expires_at
  bool current            // 与当前请求携带的 device_id 一致时为 true（可选）
```

要求：只列出当前用户自己 `client_type = 'mobile'` 的会话；撤销只能删除自己的会话；这两个 RPC 需要 Bearer 认证，不加入公开过程白名单。

### 鉴权方式

- Connect RPC：`Authorization: Bearer <access_token>`，请求体 `application/proto`，`Connect-Protocol-Version: 1`。Connect 一元错误响应始终是 JSON（`{"code","message"}`），HTTP 状态非 200。
- WebSocket `/api/workspace`：不使用 header 传 token。连接后第一帧必须是 `WorkspaceClientFrame{open: OpenWorkspace{protocol_version, access_token, selected_account_id, after_log_id}}`；服务端在 access token 过期时以自定义关闭码关闭连接，客户端应刷新 token 后重连并携带 `after_log_id` 做日志追赶。

## 4. HTTPS 部署方案

保持 `gardend` 监听 `127.0.0.1:50051`，由 Caddy 或 Nginx 对外提供 `https://your-domain.example`，反向代理到 `http://127.0.0.1:50051`。

- 使用正规 HTTPS 证书，推荐 Let's Encrypt。
- 代理必须支持 WebSocket Upgrade，并转发 `X-Forwarded-Proto: https`。
- Web 端继续使用现有 CORS 配置；Android 不依赖 CORS 和浏览器 Cookie。
- 开发环境：模拟器通过 `http://10.0.2.2:50051` 直连本机 `gardend`（仅 debug 构建允许），或通过本机 Caddy + mkcert 证书走 HTTPS 并在模拟器安装用户 CA。
- 生产 App（release）不信任自签名证书和用户 CA。

## 5. Android 工程结构

单模块，包结构：

```text
android/
  gradlew, gradle/wrapper/          Gradle 9.3.1
  app/
    build.gradle.kts                AGP 9.1.0, Compose, protobuf 插件（Java Lite，源为 ../../proto）
    src/main/java/com/silkage/mygardenworld/
      core/network/                 URL 归一化、OkHttp、Connect 二进制客户端、认证拦截、刷新合并
      core/auth/                    TokenStore(Keystore)、AuthSession、设备标识
      core/protocol/                WorkspaceSocket 帧编解码、sequence、resync、日志游标、状态归并
      core/ui/                      主题（小云朵深色）、通用组件、设置行、空态
      feature/accounts/             账号列表、新增 iOS 账号、Alipay QR 登录、连接/断开/删除
      feature/workspace/            八个工作区页面与 ViewModel
      feature/settings/             服务地址、连接测试、设备会话、退出登录
    src/debug/res/xml/network_security_config.xml    允许 cleartext + 用户 CA
    src/main/res/xml/network_security_config.xml     仅系统 CA
    src/test/                       JVM 单元测试
```

工程要求：

- Kotlin、Jetpack Compose、Material 3、Navigation Compose。
- 使用 Android Studio 内置 JDK 21。
- Android Keystore 保护 refresh token；access token 只保存在内存。
- 服务地址可配置，接受 `https://host`、`https://host:port`；debug 额外接受 `http://`。
- 不使用 WebView 包装 Web 页面。
- 复用“小云朵”品牌、中文文案、深色主题和现有工作区概念。

## 6. 网络和协议层

- OkHttp + OkHttp WebSocket；Protobuf Java Lite。
- Connect 一元调用：`POST {base}/mygardenworld.v1.{Service}/{Method}`，`Content-Type: application/proto`。
- 401 / `unauthenticated` 时单次刷新并重放原请求；刷新并发合并（一个 Mutex + 结果共享）。
- 网络切换、超时、断线重连（指数退避，上限 30s）。
- `/api/workspace`：`WorkspaceClientFrame` / `WorkspaceServerFrame` 二进制编解码；`sequence` 严格递增，缺口即触发 `resync`；`snapshot` 替换、`patch` 按域合并、`cleared_domains` 清空；日志页面 `next_before_id / has_more_after / gap_detected` 处理。
- App 进入后台断开 WebSocket，回到前台自动重连并携带 `after_log_id`。
- 离线状态禁用所有修改操作按钮。

## 7. MVP（第一期）

- 登录、刷新、退出登录。
- 服务地址配置和连接测试。
- 账号列表、账号切换。
- iOS 账号添加、连接、断开、删除。
- Alipay QR 登录和进度展示（`StartAlipayLogin` + `WatchAlipayLogin` 帧）。
- 基础、花园、订单、日志工作区。
- 自动化启停。
- 策略读取和保存（`GetPolicy/SetPolicy`）。
- 账号连接状态、错误和加载状态。

页面约定：顶部显示当前服务与账号；底部 `NavigationBar` 切换主要工作区；日志和表格用稳定的 `LazyColumn`；适配深色模式、字体放大和窄屏；Union 页面必须展示“未确认成员资格”状态。

## 8. 完整功能（第二期）

- 公会、活动（仅 `4002`、`4003`）、仓库、统计工作区。
- 兑换码中心（`RedeemExchangeService`）。
- 管理员用户管理（`AdminService`）。
- 设备会话查看和撤销（依赖第 3 节新增 RPC）。
- 自动化异常通知作为后续扩展，不阻塞。

## 9. 执行顺序

- [x] P0 后端补齐：设备会话 RPC、v6→v7 迁移测试、移动端认证补充测试、`AGENTS.md`、`Makefile` Android 入口。
- [x] P1 工程基础：Gradle Wrapper、protobuf 生成、包结构、network_security_config、主题、单元测试骨架，`./gradlew test lint assembleDebug` 通过。
- [x] P2 网络与协议：Connect 二进制客户端、认证拦截与刷新合并、WorkspaceSocket、状态归并，配套 JVM 测试。
- [x] P3 MVP：登录/设置/账号/基础/花园/订单/日志/自动化/策略（已实现，待真实账号验证）。
- [ ] P4 完整功能：公会/活动/仓库/统计/兑换码/管理员/设备会话。
- [ ] P5 验收：模拟器清单、真机清单、仓库 `make check`。

## 10. 测试与验收

模拟器（android-37.1，后续可补 API 36 镜像）：登录和 token 刷新；HTTPS 证书校验；WebSocket 连接、断线、重连和 resync；多账号切换；八个工作区基本渲染；旋转屏幕和进后台恢复；弱网、超时和服务端 401。

Redmi K100 Pro：Wi-Fi 与移动网络切换；长时间后台恢复；实际 HTTPS 访问远程 `gardend`；QR 登录；深色主题和字体缩放；性能、内存和耗电。

Android 工程命令：

```sh
cd android && ./gradlew test lint assembleDebug
```

仓库整体仍需通过：

```sh
make proto-check
make check
```

完成标准：模拟器可以通过 HTTPS 登录独立部署的 `gardend` 并完成 MVP 所有操作；真机通过相同验收流程；Web 现有功能和认证流程无回归。
