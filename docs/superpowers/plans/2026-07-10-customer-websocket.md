# 顾客端 WebSocket 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 顾客端在订单、反馈和家庭状态变化时通过 WebSocket 自动刷新相应页面。

**Architecture:** 新增顾客端共享 WebSocket 服务，复用商家端的消息格式和连接策略，连接 `/ws/customer?token=...`。`CustomerShell` 跟随认证状态管理连接生命周期，页面按模块订阅消息并调用其既有 HTTP 加载方法。

**Tech Stack:** Flutter、Dart、`web_socket_channel`、`flutter_test`。

## Global Constraints

- 初始加载和手动刷新继续使用现有 HTTP API。
- 只消费 `ORDER`、`FEEDBACK`、`FAMILY` 推送；未知消息和 `SYSTEM/PONG` 安全忽略。
- 登录成功或会话恢复后连接；登出时断开；断线时有限递增延迟重连。

---

### Task 1: 顾客 WebSocket 服务

**Files:            
- Create: `customer_frontend/lib/services/customer_websocket_service.dart`
- Test: `customer_frontend/test/customer_websocket_service_test.dart`

**Interfaces:**
- Consumes: `ApiClient.defaultBaseUrl` 和已登录 JWT。
- Produces: `CustomerWsMessage.fromJson(Map<String, dynamic>)`、`CustomerWebSocketService.messageStream`、`streamForModule(String)`、`connect(String token)`、`disconnect()`。

- [ ] **Step 1: 编写失败测试**

```dart
test('parses a websocket message with a numeric timestamp', () {
  final message = CustomerWsMessage.fromJson({
    'module': 'ORDER', 'action': 'ACCEPTED', 'data': {'orderId': 7},
    'timestamp': 123,
  });
  expect(message.module, 'ORDER');
  expect(message.data['orderId'], 7);
  expect(message.timestamp, 123);
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `flutter test test/customer_websocket_service_test.dart`

Expected: FAIL，因为 `CustomerWsMessage` 尚未定义。

- [ ] **Step 3: 实现最小服务**

创建消息模型和服务；用 `ApiClient.defaultBaseUrl` 的 scheme/host 构建 `ws` 或 `wss` 基址并追加 `/ws/customer` 和 token 查询参数；解析 JSON 后过滤 `SYSTEM/PONG`；每 25 秒发送 `SYSTEM/PING`；非手动断开按 3、5、7、9、11、13、15 秒重连。

- [ ] **Step 4: 运行服务测试**

Run: `flutter test test/customer_websocket_service_test.dart`

Expected: PASS。

### Task 2: 将连接绑定到顾客认证会话

**Files:**
- Modify: `customer_frontend/lib/main.dart`
- Modify: `customer_frontend/lib/services/api_client.dart`
- Test: `customer_frontend/test/widget_test.dart`

**Interfaces:**
- Consumes: `AuthController.isLoggedIn` 与 `ApiClient.token`。
- Produces: 认证状态变化后只有登录态维持 WS 连接，登出立即关闭。

- [ ] **Step 1: 编写失败组件测试**

```dart
testWidgets('signed-out app does not create a customer websocket connection', (tester) async {
  SharedPreferences.setMockInitialValues({});
  await tester.pumpWidget(const OrderMenuCustomerApp());
  await tester.pump();
  expect(CustomerWebSocketService.instance.isConnected, isFalse);
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `flutter test test/widget_test.dart`

Expected: FAIL，因为 `CustomerWebSocketService` 尚未接入应用生命周期。

- [ ] **Step 3: 实现生命周期管理**

在 `CustomerShell` 的认证监听中，当 `isReady && isLoggedIn && api.token` 非空时调用 `connect(token)`；当未登录时调用 `disconnect()`；在 shell `dispose` 中取消订阅并断开。不得把 token 写入新的偏好设置键。

- [ ] **Step 4: 运行组件测试**

Run: `flutter test test/widget_test.dart`

Expected: PASS。

### Task 3: 用实时事件刷新三个顾客页面

**Files:**
- Modify: `customer_frontend/lib/pages/orders_page.dart`
- Modify: `customer_frontend/lib/pages/my_feedback_page.dart`
- Modify: `customer_frontend/lib/pages/my_families_page.dart`

**Interfaces:**
- Consumes: `CustomerWebSocketService.instance.streamForModule`。
- Produces: `ORDER`、`FEEDBACK` 或 `FAMILY` 推送到达时调用各页面既有 `_reload()`；页面销毁时取消订阅。

- [ ] **Step 1: 编写失败页面测试**

```dart
testWidgets('orders page reloads when an ORDER websocket message arrives', (tester) async {
  // 使用可计数的 CustomerApi 替身构建 OrdersPage。
  // 推送 ORDER/ACCEPTED 后断言 orders() 调用次数从 1 增为 2。
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `flutter test test/orders_page_websocket_test.dart`

Expected: FAIL，因为页面尚未订阅消息流。

- [ ] **Step 3: 实现订阅与清理**

三个 `State` 均持有 `StreamSubscription<CustomerWsMessage>?`：订单页接受 `CREATED`、`CANCELLED`、`ACCEPTED`、`REJECTED`、`COMPLETED`；反馈页接受 `CREATED`、`REPLIED`；家庭页接受 `MEMBER_APPLIED`、`MEMBER_APPROVED`、`MEMBER_REJECTED`。收到支持的 action 后，若页面仍挂载则调用 `_reload()`；在 `dispose` 取消订阅。删除订单页原有的 10 秒轮询。

- [ ] **Step 4: 运行页面测试**

Run: `flutter test test/orders_page_websocket_test.dart`

Expected: PASS。

### Task 4: 全量验证

**Files:**
- Modify: `customer_frontend/test/customer_websocket_service_test.dart`
- Modify: `customer_frontend/test/orders_page_websocket_test.dart`

- [ ] **Step 1: 格式化修改文件**

Run: `dart format lib test`

Expected: 所有修改后的 Dart 文件已格式化。

- [ ] **Step 2: 静态检查**

Run: `flutter analyze`

Expected: No issues found.

- [ ] **Step 3: 运行完整测试**

Run: `flutter test`

Expected: All tests passed.
