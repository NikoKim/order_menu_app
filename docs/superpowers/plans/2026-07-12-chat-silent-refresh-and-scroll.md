# Chat Silent Refresh And Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make chat list/detail reconciliation silent and race-safe, auto-scroll details to the newest message, and enforce the initial-message limit across message types.

**Architecture:** API responses remain authoritative, but successful data is cached in page state and never replaced by a loading screen during background refresh. Monotonic generations reject stale responses, and detail pages schedule bottom scrolling after rendered message changes.

**Tech Stack:** Java 17, Spring Boot, MyBatis Plus, JUnit 5/Mockito, Flutter/Dart, Flutter widget/unit tests.

## Global Constraints

- Show a blocking loader only when no cached chat data exists.
- Ignore API responses older than the most recently started request.
- Only opening a specific conversation marks it read.
- Apply the first-message limit to text and dish shares.

---

### Task 1: Direct Message Limit

**Files:**
- Modify: `backend/src/main/java/com/order/menu/module/message/service/MessageService.java`
- Test: `backend/src/test/java/com/order/menu/module/message/service/MessageServiceTest.java`
- Test: `backend/src/test/java/com/order/menu/module/message/service/FamilyGroupServiceTest.java`

- [ ] Add failing tests for a second dish share before reply and for a send after reply.
- [ ] Run targeted tests and verify the dish-share case fails.
- [ ] Apply the direct-message guard in the shared dish path without changing family-group behavior.
- [ ] Run targeted tests and verify success.

### Task 2: Race-Safe Conversation Lists

**Files:**
- Create: `customer_frontend/lib/services/latest_request.dart`
- Create: `shop_frontend/lib/services/latest_request.dart`
- Modify: `customer_frontend/lib/pages/chat_list_page.dart`
- Modify: `shop_frontend/lib/pages/chat_list_page.dart`
- Test: `customer_frontend/test/latest_request_test.dart`
- Test: `shop_frontend/test/latest_request_test.dart`

- [ ] Add failing tests proving an older generation cannot replace a newer generation.
- [ ] Implement a small request-generation guard.
- [ ] Replace list `FutureBuilder` refresh swapping with cached state and silent guarded reloads.
- [ ] Clear the selected conversation's unread count optimistically before opening its detail page, then reconcile silently.
- [ ] Run targeted tests.

### Task 3: Silent Detail Refresh And Auto-Scroll

**Files:**
- Modify: `customer_frontend/lib/pages/chat_detail_page.dart`
- Modify: `shop_frontend/lib/pages/chat_detail_page.dart`
- Test: `customer_frontend/test/latest_request_test.dart`
- Test: `shop_frontend/test/latest_request_test.dart`

- [ ] Add tests for accepting only the latest detail response.
- [ ] Keep cached messages visible during reconciliation.
- [ ] Add and dispose `ScrollController` instances.
- [ ] Schedule bottom scrolling after first load, send append, and incoming-message refresh.
- [ ] Run targeted chat tests.

### Task 4: Full Verification

**Files:**
- Verify all modified files.

- [ ] Run `mvn test` from `backend`.
- [ ] Run `flutter test` and non-fatal-warning analysis from both Flutter projects.
- [ ] Run `git diff --check` and review every requested behavior.
