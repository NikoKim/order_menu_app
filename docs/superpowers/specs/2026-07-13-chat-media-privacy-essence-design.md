# Chat Media, Privacy, and Essence Design

## Scope

This increment extends the existing customer/shop chat stack without replacing its REST and WebSocket architecture. It covers the 21 requested behaviors: unified chat styling, animated contextual actions, privacy groups, shop identity, system-style recalls, blocked-send presentation, entry animation, richer share cards, emoji, shop sharing, expiring image messages, group essence, soft conversation deletion/pinning/muting, and mobile push registration.

## Data Model

- User privacy stores `allowPrivateChat`, `autoJoinFamilyGroup`, `showJoinedFamilies`, and `showPhone`.
- Conversation membership stores per-actor `muted`, `hiddenAt`, `pinnedAt`, and `lastEssenceReadAt`. A hidden conversation becomes visible when a later incoming message exists.
- Chat messages add `IMAGE_SHARE` and `SHOP_SHARE` snapshots. Image messages may contain text and one uploaded image. Expired images keep their message row and expose an expired state.
- Group essence records the source message, creator, creation time, optional pin order/time, and likes in a separate table. Only the shop owner can add, remove, or pin essence; members can like it.
- Push device registrations map an authenticated actor to a platform token. Notification creation dispatches to registered devices through an environment-configured push gateway.

## Media Lifecycle

Images are uploaded through a conversation-scoped multipart endpoint. The server validates image content, compresses payloads larger than 5 MB, writes them under `/www/server/SpringBoot/order_menu_image/message_chat/{conversationId}/`, and returns an immutable URL plus an expiry timestamp. A daily Asia/Shanghai 03:00 scheduler deletes images whose upload timestamp is at least 30 days old and removes empty conversation directories. Message serialization reports an expired image instead of a broken URL.

## Interaction Design

Chat/list/notification surfaces use a light gray page background with white rounded content regions. Long press uses an anchored horizontal overlay above the selected bubble. The bubble scales down, briefly overshoots, then settles while the menu enters. Recalled messages render as centered system rows without avatars. Failed blocked messages place the error icon on the bubble's left and the warning as a centered system line.

Emoji selection uses an in-app grid beside the composer and deliberately dismisses the software keyboard. Focusing the text field closes the grid and restores the keyboard. Selected images appear below the unsent text and are sent together with the text, while empty text remains valid when an image exists.

## Sharing Rules

Dish cards retain their immutable snapshots but use a more polished chat-specific layout. Shop cards can only target conversations belonging to that shop: the shop's own family group, and direct conversations with eligible family members when the shop is a family shop. The server enforces these rules rather than trusting the client picker.

## Essence Flow

Essence indicators appear beside source bubbles. The group information page links to an essence page. Unread essence produces a dismissible banner on chat entry; only opening the essence page marks it read. Pinned essence items are grouped first in pin-time order and carry a visible pin label. When an owner marks another member's message as essence, that member receives a notification.

## Push Delivery

The existing notification unread count remains authoritative. Frontends register and refresh device tokens; the backend attempts a mobile push for each notification that would produce a red badge. Credentials and provider endpoint are supplied through deployment environment variables so secrets are not committed.

## Verification

Backend service/controller tests cover permissions, visibility restoration, expiry boundaries, essence ordering/likes, privacy redaction, and share restrictions. Flutter model/API/widget tests cover menus, settings groups, cards, muted badges, swipe actions, composer media, banners, and independent customer/shop rendering. Full Maven and Flutter suites plus static analysis run after integration.
