# Chat Media, Privacy, and Essence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the requested privacy, media, sharing, essence, conversation management, animation, styling, and push-notification behavior across backend, customer, and shop applications.

**Architecture:** Extend the current message REST/WebSocket contract with per-member conversation preferences, snapshot message types, expiring server media, and group essence resources. Keep UI behavior in reusable Flutter widgets so customer and shop surfaces stay visually and behaviorally aligned.

**Tech Stack:** Spring Boot 3.3, MyBatis-Plus, Flyway, scheduled jobs, Flutter/Dart, REST, WebSocket, image_picker.

## Global Constraints

- Image files live in `/www/server/SpringBoot/order_menu_image/message_chat/{conversationId}/`.
- Each image expires independently at the first 03:00 Asia/Shanghai run after 30 full days.
- Server authorization enforces privacy, shop-sharing, essence-owner, and membership rules.
- New pages use light gray backgrounds and white rounded content regions.
- Existing user changes in the dirty worktree must be preserved.

---

### Task 1: Backend schema and message contracts

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__chat_media_privacy_essence.sql`
- Modify: `backend/src/main/java/com/order/menu/module/message/**`
- Test: `backend/src/test/java/com/order/menu/module/message/service/MessageServiceTest.java`

- [ ] Write failing tests for privacy redaction, hidden/pinned/muted conversations, image/shop snapshots, and owner-only essence operations.
- [ ] Add Flyway schema and typed DTO/entity/mapper contracts.
- [ ] Implement APIs and WebSocket payloads, then make the focused tests pass.
- [ ] Add image compression/expiry cleanup and push-device dispatch tests.
- [ ] Run `mvn test` and preserve a zero-failure report.

### Task 2: Customer privacy, list, and sharing

**Files:**
- Modify: `customer_frontend/lib/pages/profile_page.dart`
- Modify: `customer_frontend/lib/pages/chat_settings_page.dart`
- Modify: `customer_frontend/lib/pages/chat_list_page.dart`
- Modify: `customer_frontend/lib/pages/shop_order_page.dart`
- Modify: `customer_frontend/lib/services/api_client.dart`
- Test: `customer_frontend/test/**`

- [ ] Write failing model/API tests for the four privacy switches and conversation preferences.
- [ ] Rename the entry to `隐私设置` and render the two requested setting groups.
- [ ] Add swipe delete/pin, muted gray badges, and shop-share conversation filtering.
- [ ] Run focused tests and static analysis.

### Task 3: Shared customer chat interaction

**Files:**
- Modify: `customer_frontend/lib/pages/chat_detail_page.dart`
- Modify: `customer_frontend/lib/pages/chat_user_detail_page.dart`
- Create: `customer_frontend/lib/widgets/chat_action_overlay.dart`
- Create: `customer_frontend/lib/widgets/chat_composer.dart`
- Test: `customer_frontend/test/**`

- [ ] Write failing widget/model tests for anchored actions, emoji/media composition, recall rows, failed placement, and entry animation.
- [ ] Implement the horizontal overlay and scale/overshoot transition.
- [ ] Implement image selection/upload, mixed text-image send, expired rendering, quote/recall/delete, and mute controls.
- [ ] Add gray/white rounded layout and polished dish/shop/image cards.
- [ ] Run customer tests and analysis.

### Task 4: Shop chat, identity, and conversation preferences

**Files:**
- Modify: `shop_frontend/lib/pages/chat_list_page.dart`
- Modify: `shop_frontend/lib/pages/chat_detail_page.dart`
- Modify: `shop_frontend/lib/pages/chat_user_detail_page.dart`
- Modify: `shop_frontend/lib/pages/message_center_page.dart`
- Modify: `shop_frontend/lib/pages/shop_profile_page.dart`
- Test: `shop_frontend/test/**`

- [ ] Write failing tests for shop labels, layout, swipe behavior, mute, menus, emoji/media, and card rendering.
- [ ] Implement shop identity/store information and the shared chat interactions.
- [ ] Enforce the shop restriction that its own family conversation cannot be hidden.
- [ ] Run shop tests and analysis.

### Task 5: Essence experience

**Files:**
- Create: `shop_frontend/lib/pages/group_essence_page.dart`
- Modify: `shop_frontend/lib/pages/family_group_management_page.dart`
- Modify: `shop_frontend/lib/pages/chat_detail_page.dart`
- Create: `customer_frontend/lib/pages/group_essence_page.dart`
- Modify: `customer_frontend/lib/pages/family_group_info_page.dart`
- Modify: `customer_frontend/lib/pages/chat_detail_page.dart`
- Test: `customer_frontend/test/**`, `shop_frontend/test/**`

- [ ] Write failing tests for unread banners, read semantics, ordering, pin labels, likes, and owner actions.
- [ ] Implement both essence pages and group-info routes.
- [ ] Add source-bubble indicators and chat-entry banners.
- [ ] Verify member notifications and all focused tests.

### Task 6: Push registration and integration verification

**Files:**
- Modify: `customer_frontend/lib/main.dart`
- Modify: `shop_frontend/lib/main.dart`
- Modify: `backend/src/main/resources/application.yml`
- Test: backend and both Flutter test suites

- [ ] Add push-token registration lifecycle with deployment-configured credentials.
- [ ] Run `mvn test`.
- [ ] Run `flutter test --no-pub` for customer and shop.
- [ ] Run Dart analysis for both apps and `git diff --check`.
