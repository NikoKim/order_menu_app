# Chat Realtime And Read State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver reliable realtime chat refresh, correct per-conversation unread state, stable conversation-list presentation, family auto-join behavior, and established-conversation reply permission.

**Architecture:** WebSocket messages invalidate API-backed state rather than carrying authoritative UI models. The backend owns read positions and permission decisions; each Flutter shell owns the aggregate unread badge while list/detail pages refresh their relevant API data.

**Tech Stack:** Java 17, Spring Boot, MyBatis Plus, JUnit 5/Mockito, Flutter/Dart, `web_socket_channel`, Flutter widget tests.

## Global Constraints

- Opening the chat tab must never mark conversations read.
- Only a successful messages request for one conversation advances that conversation's read position.
- Do not add a polling loop or a new runtime dependency.
- Preserve cached UI state when reconciliation fails.
- Keep the existing one-message-before-reply restriction.

---

### Task 1: Backend Private Reply And Family Membership Rules

**Files:**
- Modify: `backend/src/main/java/com/order/menu/module/message/service/MessageService.java`
- Test: `backend/src/test/java/com/order/menu/module/message/service/MessageServiceTest.java`
- Test: `backend/src/test/java/com/order/menu/module/message/service/FamilyGroupServiceTest.java`

**Interfaces:**
- Consumes: `sendMessage(Long, Long, SendMessageRequest)` and `onFamilyMemberApproved(FamilyMember)`.
- Produces: directional established-contact permission based on message history and active/pending membership based on `autoJoinFamilyChat`.

- [ ] Add a failing service test where user A disables private chat, A previously messages B, and B can reply.
- [ ] Add failing family tests proving auto-join users are active and opt-out users remain pending.
- [ ] Run `mvn -Dtest=MessageServiceTest,FamilyGroupServiceTest test` from `backend` and confirm the new private reply test fails with `FORBIDDEN`.
- [ ] Change the recipient permission check so a disabled target accepts a message only after that target previously sent in the conversation; leave new-conversation and repeated-first-message guards intact.
- [ ] Run the targeted backend tests and confirm they pass.

### Task 2: Socket Connection And Reconciliation Signals

**Files:**
- Modify: `customer_frontend/lib/services/customer_websocket_service.dart`
- Modify: `shop_frontend/lib/services/websocket_service.dart`
- Test: `customer_frontend/test/customer_websocket_service_test.dart`
- Create: `shop_frontend/test/websocket_service_test.dart`

**Interfaces:**
- Produces: a broadcast connection-state stream whose connected event occurs only after `channel.ready` succeeds.
- Consumes: existing token/configuration and module message streams.

- [ ] Add failing tests for connected emission after handshake and another connected emission after reconnection.
- [ ] Run the two targeted Flutter test files and confirm failure because no connection stream exists.
- [ ] Await `WebSocketChannel.ready`, publish connection state changes, and keep the existing heartbeat and reconnect policy.
- [ ] Run targeted socket tests and confirm they pass.

### Task 3: Correct Aggregate Unread State

**Files:**
- Modify: `customer_frontend/lib/main.dart`
- Modify: `shop_frontend/lib/main.dart`
- Test: `customer_frontend/test/chat_entry_test.dart`
- Test: `shop_frontend/test/chat_entry_test.dart`

**Interfaces:**
- Consumes: chat module events, connection-state events, and conversation list APIs.
- Produces: bottom navigation badge derived from unread counts of non-muted conversations.

- [ ] Add failing source/widget tests proving selecting the chat destination does not fetch every conversation's messages or force the badge to zero.
- [ ] Run both targeted chat entry tests and confirm failure against `_markAllChatsRead` behavior.
- [ ] Remove tab-level mark-all-read logic and refresh aggregate unread state on chat events, reconnect, and return from a detail page.
- [ ] Run both targeted tests and confirm they pass.

### Task 4: Detail Page Realtime Read Behavior

**Files:**
- Modify: `customer_frontend/lib/pages/chat_detail_page.dart`
- Modify: `shop_frontend/lib/pages/chat_detail_page.dart`
- Test: `customer_frontend/test/chat_entry_test.dart`
- Test: `shop_frontend/test/chat_entry_test.dart`

**Interfaces:**
- Consumes: `CHAT` events containing `conversationId` and the messages API that marks one conversation read.
- Produces: event-filtered detail refresh that preserves cached messages on failure.

- [ ] Add failing tests for ignoring other conversation events and refreshing the current conversation event.
- [ ] Run targeted tests and confirm the event behavior is missing or incorrect.
- [ ] Keep the existing conversation-id filter, add reconnect reconciliation, and ensure successful reload is the only read transition.
- [ ] Run targeted tests and confirm they pass.

### Task 5: Stable Conversation List Layout And Time

**Files:**
- Modify: `customer_frontend/lib/models.dart`
- Modify: `customer_frontend/lib/pages/chat_list_page.dart`
- Modify: `shop_frontend/lib/models/shop_api_models.dart`
- Modify: `shop_frontend/lib/pages/chat_list_page.dart`
- Test: `customer_frontend/test/chat_models_test.dart`
- Test: `shop_frontend/test/chat_models_test.dart`

**Interfaces:**
- Produces: shared-equivalent `formatChatListTime(DateTime?)` behavior in each client and a constrained trailing column with time and badge.
- Consumes: `lastMessageAt`, `unreadCount`, and `muted` from conversation responses.

- [ ] Add failing formatting tests for today, same year, earlier year, and null timestamps.
- [ ] Run targeted model tests and confirm the formatter is absent.
- [ ] Implement formatters and update both list tiles to show time and an independent badge without a chevron.
- [ ] Add widget assertions that customer title and preview remain present when the badge is visible.
- [ ] Run targeted model and widget tests and confirm they pass.

### Task 6: Family Group Realtime Visibility

**Files:**
- Modify if required by failing tests: `backend/src/main/java/com/order/menu/module/message/service/MessageService.java`
- Modify: `customer_frontend/lib/pages/family_group_info_page.dart`
- Modify: `customer_frontend/lib/pages/chat_list_page.dart`
- Test: `backend/src/test/java/com/order/menu/module/message/service/FamilyGroupServiceTest.java`
- Test: `customer_frontend/test/chat_entry_test.dart`

**Interfaces:**
- Consumes: `MEMBER_UPDATED` and `CONVERSATION_UPDATED` chat events.
- Produces: immediate list appearance after active auto-join or successful manual join; pending users remain absent.

- [ ] Add failing tests for the active-member push and chat-list refresh after manual membership activation.
- [ ] Run targeted backend and customer tests and confirm failure.
- [ ] Ensure membership activation pushes after commit and the customer list reconciles on the event.
- [ ] Run targeted tests and confirm they pass.

### Task 7: Full Verification

**Files:**
- Verify all modified files.

**Interfaces:**
- Consumes: all prior task outputs.
- Produces: verified backend and client builds/tests.

- [ ] Run `mvn test` from `backend`.
- [ ] Run `flutter test` and `flutter analyze` from `customer_frontend`.
- [ ] Run `flutter test` and `flutter analyze` from `shop_frontend`.
- [ ] Review `git diff --check` and `git diff` against every design requirement.

