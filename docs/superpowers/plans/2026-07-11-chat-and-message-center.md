# 聊天与消息中心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在顾客端和商家端交付具备权限控制的私聊、家庭群聊、菜品分享和统一消息中心。

**Architecture:** 后端以 `message` 模块持久化会话、成员、聊天消息、系统通知；REST 负责写入与权限校验，既有 WebSocket 推送变更。两个 Flutter 客户端通过 HTTP 加载聊天/消息数据，并订阅 WS 事件刷新。

**Tech Stack:** Spring Boot 3、Java 17、MyBatis-Plus、MySQL、Flutter、Dart、`web_socket_channel`。

## Global Constraints

- 账户的 `allowPrivateChat` 和顾客的 `autoJoinFamilyChat` 默认均为 `true`。
- 私聊首条消息后必须等待对方回复；该规则仅由服务端判定。
- 家庭店铺菜品的分享接收方必须是已审批的同家庭成员或该家庭群。
- 解散家庭群只禁止发言并保留历史；移出群只影响群成员身份，不影响家庭成员身份。
- WebSocket 用于推送，不承载聊天写操作。

---

### Task 1: 消息数据模型与账户权限

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__v1_3_messages_and_chat.sql`
- Modify: `backend/src/main/java/com/order/menu/module/user/{entity/User.java,dto/UserDtos.java,service/UserService.java,controller/UserController.java}`
- Test: `backend/src/test/java/com/order/menu/module/user/service/UserServiceTest.java`

**Interfaces:**
- Produces `allowPrivateChat` and `autoJoinFamilyChat` account fields plus tables `conversations`, `conversation_members`, `chat_messages`, `user_notifications`.

- [ ] Write a failing account-settings test that updates both flags and asserts default values are true for a new user.
- [ ] Run `mvn -Dtest=UserServiceTest test`; expect compilation failure for missing fields.
- [ ] Add migration columns/tables and extend user request/response/service methods.
- [ ] Run `mvn -Dtest=UserServiceTest test`; expect PASS.

### Task 2: 会话、私聊和消息 API

**Files:**
- Create: `backend/src/main/java/com/order/menu/module/message/{entity/Conversation.java,entity/ConversationMember.java,entity/ChatMessage.java,entity/UserNotification.java,mapper/ConversationMapper.java,mapper/ConversationMemberMapper.java,mapper/ChatMessageMapper.java,mapper/UserNotificationMapper.java,dto/MessageDtos.java,service/MessageService.java,controller/MessageController.java}`
- Modify: `backend/src/main/java/com/order/menu/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/order/menu/module/message/service/MessageServiceTest.java`

**Interfaces:**
- Produces `POST /api/messages/direct/users/{userId}`, `POST /api/messages/direct/shops/{shopId}`, `GET /api/messages/users?nickname=`, `GET/POST /api/messages/conversations/{id}/messages`, and `GET /api/messages/conversations`.

- [ ] Write failing tests for recipient private-chat denial, duplicate direct-conversation reuse, and initiator blocked after one unanswered message.
- [ ] Run `mvn -Dtest=MessageServiceTest test`; expect failures from absent service/API.
- [ ] Implement transactional conversation resolution, role-safe customer search, shop-owner lookup, authorization and message persistence.
- [ ] Run `mvn -Dtest=MessageServiceTest test`; expect PASS.

### Task 3: 家庭群、群资料、免打扰与菜品分享

**Files:**
- Modify: `backend/src/main/java/com/order/menu/module/message/{dto/MessageDtos.java,service/MessageService.java,controller/MessageController.java}`
- Modify: `backend/src/main/java/com/order/menu/module/family/service/FamilyService.java`
- Modify: `backend/src/main/java/com/order/menu/module/dish/service/DishService.java`
- Test: `backend/src/test/java/com/order/menu/module/message/service/FamilyGroupServiceTest.java`

**Interfaces:**
- Produces family group create/dissolve, member invitation approval/rejoin/leave/remove, group profile/update, mute, and dish-share operations.

- [ ] Write failing tests for two-member creation threshold, auto-join opt-out invitation, removal without family removal, dissolved-group send rejection, and cross-family dish-share rejection.
- [ ] Run `mvn -Dtest=FamilyGroupServiceTest test`; expect failures.
- [ ] Implement one group per shop, family lifecycle hooks, per-member mute, owner-only group edit/removal and dish snapshot messages.
- [ ] Run `mvn -Dtest=FamilyGroupServiceTest test`; expect PASS.

### Task 4: 系统消息与 WebSocket 推送

**Files:**
- Modify: `backend/src/main/java/com/order/menu/websocket/{WsSessionRegistry.java,WsPushService.java}`
- Modify: `backend/src/main/java/com/order/menu/module/{order/service/OrderService.java,feedback/service/FeedbackService.java,family/service/FamilyService.java}`
- Modify: `backend/src/main/java/com/order/menu/module/message/{service/MessageService.java,controller/MessageController.java}`
- Test: `backend/src/test/java/com/order/menu/module/message/service/NotificationServiceTest.java`

**Interfaces:**
- Produces persisted notifications and `CHAT/MESSAGE_CREATED`, `CHAT/CONVERSATION_UPDATED`, `CHAT/MEMBER_UPDATED`, `NOTIFICATION/CREATED` events addressed by user identity.

- [ ] Write failing tests for customer order/feedback/family notifications and shop order/family/feedback notifications.
- [ ] Run `mvn -Dtest=NotificationServiceTest test`; expect failures.
- [ ] Add user-addressable WS fan-out, notification creation/read APIs and service hooks.
- [ ] Run `mvn -Dtest=NotificationServiceTest test`; expect PASS.

### Task 5: 顾客端聊天与消息中心

**Files:**
- Create: `customer_frontend/lib/pages/{chat_list_page.dart,chat_detail_page.dart,message_center_page.dart,chat_settings_page.dart}`
- Modify: `customer_frontend/lib/{main.dart,models.dart,services/api_client.dart,pages/profile_page.dart,pages/shop_order_page.dart,pages/dish_detail_page.dart,services/customer_websocket_service.dart}`
- Test: `customer_frontend/test/{chat_models_test.dart,chat_entry_test.dart}`

**Interfaces:**
- Consumes Task 2-4 APIs and WS events.
- Produces bottom chat tab, shop-contact entry, customer search, settings, notifications, family group UI and dish-sharing picker.

- [ ] Write failing tests for anonymous users lacking a chat tab and a family dish share rejecting an ineligible target response.
- [ ] Run `flutter test test/chat_models_test.dart test/chat_entry_test.dart`; expect failures.
- [ ] Add models/facade/pages, WS refresh subscriptions, unread badges and share picker.
- [ ] Run the two tests; expect PASS.

### Task 6: 商家端聊天与消息中心

**Files:**
- Create: `shop_frontend/lib/pages/{chat_list_page.dart,chat_detail_page.dart,message_center_page.dart}`
- Modify: `shop_frontend/lib/{main.dart,models/shop_api_models.dart,services/shop_api_service.dart,pages/dashboard_page.dart,pages/shop_profile_page.dart,pages/shop_family_page.dart}`
- Test: `shop_frontend/test/{chat_models_test.dart,chat_entry_test.dart}`

**Interfaces:**
- Consumes Task 2-4 APIs and existing `ShopWebSocketService`.
- Produces bottom chat tab, dashboard shortcut, private-chat permission settings, notification center and family group management.

- [ ] Write failing tests for the dashboard chat shortcut and group-management eligibility state.
- [ ] Run `flutter test test/chat_models_test.dart test/chat_entry_test.dart`; expect failures.
- [ ] Add API models, chat/message pages, navigation/badges, settings and group operations.
- [ ] Run the two tests; expect PASS.

### Task 7: 端到端验证

**Files:**
- Modify: test files created in Tasks 1-6.

- [ ] Run `mvn test` in `backend`; expect all tests pass.
- [ ] Run `flutter analyze && flutter test` in `customer_frontend`; expect no new diagnostics and all tests pass.
- [ ] Run `flutter analyze && flutter test` in `shop_frontend`; expect no new diagnostics and all tests pass.
