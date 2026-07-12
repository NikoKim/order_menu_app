# Chat Silent Refresh And Scroll Design

## Goal

Keep conversation summaries and unread badges current without visible loading flashes, automatically position chat details at the newest message, and apply the one-message-before-reply rule to every direct-chat message type.

## State And Refresh

Conversation list and detail pages keep successful API data as local state. Only the first load without cached data shows a progress indicator. WebSocket events, reconnection, sends, and read changes perform background reconciliation while the existing UI remains visible.

Every page increments a request generation before loading. A response updates state only when its generation is still current, preventing a slower stale request from restoring old previews or unread badges.

Conversation list rendering reads directly from stable local state rather than a changing `FutureBuilder` snapshot. Opening a conversation immediately clears that conversation's unread count in the local list; the successful detail read and a silent server reconciliation then confirm the value. Older list responses cannot restore the cleared badge.

## Detail Scrolling

Both detail pages own a `ScrollController`. After the first successful load, an optimistic outgoing append, or a successful incoming-message reconciliation, a post-frame callback animates or jumps to `maxScrollExtent`. Empty conversations remain stable. Controllers and subscriptions are disposed with the page.

## Read State

Loading a specific detail remains the only action that advances `lastReadMessageId`. The resulting server conversation event updates list and shell badge state through silent reconciliation. Cached data remains on failure and stale responses are ignored.

## Direct Message Limit

The conversation initiator may create only one outgoing message until the other participant replies, regardless of either user's private-chat setting. The rule covers text messages and dish-share messages. Once the recipient replies, normal conversation continues. Established-contact reply permission remains unchanged.

## Testing

- Backend tests verify the first-message limit for both text and dish shares and verify sends after a reply.
- Flutter tests cover request-generation behavior and scroll scheduling helpers.
- Full backend and both Flutter suites plus analysis run after implementation.
