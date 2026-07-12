# Chat Realtime And Read State Design

## Goal

Make customer and shop chat update in real time, keep bottom navigation unread badges accurate, define reads per conversation detail page, align the customer conversation list with the shop layout, honor family auto-join preferences, and permit replies in an established private conversation.

## Architecture

WebSocket remains an invalidation channel rather than the source of truth. A chat event identifies changed server state; each Flutter client reloads its conversation list, and an open detail page reloads only when the event belongs to that conversation. A successful socket connection or reconnection triggers a reconciliation refresh so events missed while disconnected do not leave stale state.

The server remains authoritative for unread counts through `ConversationMember.lastReadMessageId`. Loading a specific conversation marks only that conversation through the existing messages endpoint. Opening the chat tab must not fetch every conversation or clear its badge.

## Realtime Behavior

- Customer and shop clients expose socket connection events in addition to module messages.
- The application shell refreshes total chat unread state after chat events and after socket reconnection.
- Conversation list pages refresh after chat events.
- A detail page refreshes only for its own `conversationId`.
- Failed refreshes keep the previously displayed content and unread count.

## Read Semantics

- A message is read only after the user opens its specific conversation and the messages request succeeds.
- Entering the chat tab does not mark any conversation read.
- New messages received while their detail page is open are fetched immediately and become read through the successful messages request.
- Muted conversations still retain their server unread counts but do not contribute to the bottom navigation badge.

## Conversation List UI

The customer list follows the shop list hierarchy: avatar on the left, name and last-message preview in the center, and message time plus an independent unread badge on the right. No chevron is shown. The right column has stable constraints so a badge cannot remove or cover the title and preview. Shop and customer lists use the same time formatting behavior.

## Family Group Membership

When a shop creates or restores an active family group, approved family members with `autoJoinFamilyChat=true` become active conversation members and see the group in chat immediately. Approved members with the preference disabled remain pending, do not see the group in their active conversation list, and join through the existing family information action. Membership activation pushes a chat event so the group appears without restarting the app.

## Private Chat Permission

Disabling private chat prevents another user from creating a new direct conversation. It does not close established conversations. When sending to a user who currently disables private chat, the send is allowed only if that target user has previously sent at least one message in the same conversation. This permits replies to a user who initiated contact while preserving the block against unsolicited first contact. The existing rule that an initiator cannot send repeated messages before the first reply remains unchanged.

## Error Handling

Socket connection is considered established only after the WebSocket handshake completes. Disconnects schedule the existing bounded reconnect. Reconnect events trigger server reconciliation. API refresh failure preserves cached UI state; send failures keep the input and show the existing friendly error.

## Testing

- Backend service tests cover per-conversation reads, automatic and pending family membership, and reply permission after prior target contact.
- Flutter unit/widget tests cover time formatting, unread presentation without a chevron, no tab-level mark-all-read call, socket connection/reconnection events, and event filtering by conversation.
- Full backend and both Flutter test suites are run after targeted tests.

