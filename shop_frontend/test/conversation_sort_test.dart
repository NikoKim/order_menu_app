import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/shop_api_models.dart';

void main() {
  test('conversation preference response preserves its conversation id', () {
    final response = ChatConversation.fromJson({
      'conversationId': 9,
      'pinned': false,
      'pinnedAt': null,
      'muted': true,
      'hidden': false,
    });

    expect(response.id, 9);
    expect(response.pinned, isFalse);
  });

  test('pinned conversations are grouped before recent conversations', () {
    final conversations = [
      ChatConversation.fromJson({
        'id': 1,
        'type': 'DIRECT',
        'status': 'ACTIVE',
        'lastMessageAt': '2026-07-13T10:00:00',
      }),
      ChatConversation.fromJson({
        'id': 2,
        'type': 'DIRECT',
        'status': 'ACTIVE',
        'pinned': true,
        'pinnedAt': '2026-07-13T08:00:00',
      }),
    ];

    final sorted = sortChatConversations(conversations);

    expect(sorted.map((item) => item.id), [2, 1]);
  });

  test('stale GET cannot rebound a confirmed unpin operation', () {
    final overrides = <int, bool>{9: false};
    final stale = mergePinnedConversationOverrides([
      ChatConversation.fromJson({
        'id': 9,
        'type': 'DIRECT',
        'status': 'ACTIVE',
        'pinned': true,
      }),
    ], overrides);

    expect(stale.conversations.single.pinned, isFalse);
    expect(stale.confirmedIds, isEmpty);

    final confirmed = mergePinnedConversationOverrides([
      ChatConversation.fromJson({
        'id': 9,
        'type': 'DIRECT',
        'status': 'ACTIVE',
        'pinned': false,
      }),
    ], overrides);

    expect(confirmed.conversations.single.pinned, isFalse);
    expect(confirmed.confirmedIds, {9});
  });

  test('a message received while deleting keeps the conversation visible', () {
    final before = ChatConversation.fromJson({
      'id': 7,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'muted': true,
      'unreadCount': 2,
      'messageVersion': 4,
      'lastMessageAt': '2026-07-15T10:00:00',
      'lastMessageContent': '旧消息',
    });
    final after = ChatConversation.fromJson({
      'id': 7,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'muted': true,
      'unreadCount': 3,
      'messageVersion': 5,
      'lastMessageAt': '2026-07-15T10:00:01',
      'lastMessageContent': '新消息',
    });

    const hidden = ConversationHideResult(hidden: true, messageVersion: 4);
    const rejected = ConversationHideResult(hidden: false, messageVersion: 5);

    expect(shouldRemoveConversationAfterHide(hidden, after), isFalse);
    expect(shouldRemoveConversationAfterHide(hidden, before), isTrue);
    expect(shouldRemoveConversationAfterHide(rejected, before), isFalse);
  });

  test('stale essence response cannot rebound a confirmed unpin operation', () {
    ChatMessage essence(bool pinned) => ChatMessage.fromJson({
      'id': 21,
      'conversationId': 7,
      'senderUserId': 3,
      'type': 'TEXT',
      'content': '精华',
      'createdAt': '2026-07-15T10:00:00',
      'essence': true,
      'essencePinned': pinned,
    });
    final overrides = <int, bool>{21: false};

    final stale = mergeEssencePinnedOverrides([essence(true)], overrides);
    expect(stale.items.single.essencePinned, isFalse);
    expect(stale.confirmedIds, isEmpty);

    final confirmed = mergeEssencePinnedOverrides([essence(false)], overrides);
    expect(confirmed.items.single.essencePinned, isFalse);
    expect(confirmed.confirmedIds, {21});
  });
}
