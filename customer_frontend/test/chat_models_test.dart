import 'package:customer_frontend/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('conversation parses preview and unread badge fields', () {
    final conversation = ChatConversation.fromJson({
      'id': 7,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'lastMessageContent': '刚刚发出的消息',
      'unreadCount': 3,
      'muted': false,
    });

    expect(conversation.lastMessageContent, '刚刚发出的消息');
    expect(conversation.unreadCount, 3);
    expect(conversation.showUnreadBadge, isTrue);
  });

  test('muted conversation hides unread badge', () {
    final conversation = ChatConversation.fromJson({
      'id': 8,
      'type': 'FAMILY_GROUP',
      'status': 'ACTIVE',
      'unreadCount': 5,
      'muted': true,
    });

    expect(conversation.showUnreadBadge, isFalse);
  });

  test('marking a conversation read clears only its unread state', () {
    final conversation = ChatConversation.fromJson({
      'id': 8,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'directUserNickname': 'Alice',
      'lastMessageContent': 'hello',
      'unreadCount': 5,
    });

    final read = conversation.markedRead();

    expect(read.unreadCount, 0);
    expect(read.showUnreadBadge, isFalse);
    expect(read.title, 'Alice');
    expect(read.lastMessageContent, 'hello');
  });
  test('parses a direct conversation using its other participant', () {
    final conversation = ChatConversation.fromJson({
      'id': 7,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'directUserId': 18,
      'directUserNickname': 'Alice',
      'directUserAvatarUrl': '/avatars/alice.png',
    });

    expect(conversation.id, 7);
    expect(conversation.title, 'Alice');
    expect(conversation.isActive, isTrue);
  });

  test('parses a dish share message with its immutable snapshot', () {
    final message = ChatMessage.fromJson({
      'id': 9,
      'conversationId': 7,
      'senderUserId': 1,
      'type': 'DISH_SHARE',
      'content': 'dish:42',
      'createdAt': '2026-07-11T08:00:00',
      'senderNickname': '幸福小馆',
      'senderAvatarUrl': '/avatars/shop.png',
      'dishName': '鱼香肉丝',
      'dishImageUrl': '/dishes/42.png',
      'dishDescription': '酸甜微辣，下饭首选',
      'dishPrice': 28.8,
      'dishSales': 136,
    });

    expect(message.isDishShare, isTrue);
    expect(message.content, 'dish:42');
    expect(message.senderNickname, '幸福小馆');
    expect(message.senderAvatarUrl, '/avatars/shop.png');
    expect(message.dishDescription, '酸甜微辣，下饭首选');
    expect(message.dishSales, 136);
  });

  test('messages within one minute share the preceding timestamp', () {
    final first = DateTime.parse('2026-07-11T08:00:00');
    final close = DateTime.parse('2026-07-11T08:00:59');
    final later = DateTime.parse('2026-07-11T08:01:00');

    expect(shouldShowChatTimestamp(null, first), isTrue);
    expect(shouldShowChatTimestamp(first, close), isFalse);
    expect(shouldShowChatTimestamp(first, later), isTrue);
  });

  test('formats conversation list timestamps relative to today', () {
    final now = DateTime(2026, 7, 12, 18, 30);

    expect(formatChatListTime('2026-07-12T08:05:00', now: now), '08:05');
    expect(formatChatListTime('2026-07-11T08:05:00', now: now), '07/11');
    expect(formatChatListTime('2025-07-11T08:05:00', now: now), '2025/07/11');
    expect(formatChatListTime(null, now: now), '');
  });
}
