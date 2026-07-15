import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/pages/chat_user_detail_page.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('conversation preserves the guarded delete message version', () {
    final conversation = ChatConversation.fromJson({
      'id': 1,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'messageVersion': 12,
    });

    expect(conversation.messageVersion, 12);
    expect(conversation.markedRead().messageVersion, 12);
    expect(conversation.withPreferences(pinned: true).messageVersion, 12);
  });

  test('optimistic unpin clears the pin timestamp', () {
    final conversation = ChatConversation.fromJson({
      'id': 1,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'pinned': true,
      'pinnedAt': '2026-07-13T09:00:00',
    });

    final unpinned = conversation.withPreferences(pinned: false);

    expect(unpinned.pinned, isFalse);
    expect(unpinned.pinnedAt, isNull);
  });

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

  test('parses an order share card snapshot', () {
    final message = ChatMessage.fromJson({
      'id': 12,
      'conversationId': 7,
      'senderUserId': 1,
      'type': 'ORDER_SHARE',
      'content': 'order:42',
      'createdAt': '2026-07-12T12:00:00',
      'orderId': 42,
      'orderNo': 'NO42',
      'orderCreatedAt': '2026-07-12T11:00:00',
      'orderStatus': 'COMPLETED',
      'orderShopName': '幸福小馆',
      'orderItems': [
        {
          'dishName': '鱼香肉丝',
          'dishImageUrl': '/dishes/fish.png',
          'unitPrice': 28.8,
          'quantity': 2,
          'subtotalAmount': 57.6,
          'selectedOptions': '少辣',
        },
      ],
      'orderTotalAmount': 57.6,
      'orderRemark': '少辣',
    });

    expect(message.isOrderShare, isTrue);
    expect(message.orderId, 42);
    expect(message.orderItems.single.quantity, 2);
    expect(message.orderItems.single.unitPrice, 28.8);
    expect(message.orderItems.single.selectedOptions, '少辣');
    expect(message.orderTotalAmount, 57.6);
  });

  test('parses a complete quoted card instead of reducing it to text', () {
    final message = ChatMessage.fromJson({
      'id': 13,
      'conversationId': 7,
      'senderUserId': 1,
      'type': 'TEXT',
      'content': '这个订单',
      'createdAt': '2026-07-12T12:00:00',
      'quotedMessageId': 12,
      'quotedMessage': {
        'id': 12,
        'type': 'ORDER_SHARE',
        'content': 'order:42',
        'orderId': 42,
        'orderNo': 'NO42',
        'orderShopName': '幸福小馆',
        'orderItems': [
          {'dishName': '鱼香肉丝', 'quantity': 2},
        ],
        'orderTotalAmount': 57.6,
      },
    });

    expect(message.quotedMessage?.type, 'ORDER_SHARE');
    expect(message.quotedMessage?.orderNo, 'NO42');
    expect(message.quotedMessage?.orderItems.single.dishName, '鱼香肉丝');
  });

  test('parses chat block relationship in both directions', () {
    final relationship = ChatBlock.fromJson({
      'userId': 18,
      'blockedByMe': true,
      'blockedMe': false,
    });

    expect(relationship.userId, 18);
    expect(relationship.blockedByMe, isTrue);
    expect(relationship.blockedMe, isFalse);
    expect(chatBlockActionLabel(relationship), '取消拉黑');
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

  test('parses quote recall and shop snapshot fields', () {
    final message = ChatMessage.fromJson({
      'id': 10,
      'conversationId': 7,
      'senderUserId': 1,
      'type': 'TEXT',
      'content': '回复',
      'createdAt': '2026-07-12T08:05:00',
      'shopName': '幸福小馆',
      'quotedMessageId': 9,
      'quotedContent': '原消息',
      'quotedSenderName': '顾客甲',
      'recalledAt': '2026-07-12T08:06:00',
    });
    expect(message.shopName, '幸福小馆');
    expect(message.quotedMessageId, 9);
    expect(message.isRecalled, isTrue);
  });

  test('formats chat message time using today yesterday and date', () {
    final now = DateTime(2026, 7, 12, 18);
    expect(
      formatChatMessageTime(DateTime(2026, 7, 12, 8, 5), now: now),
      '08:05',
    );
    expect(
      formatChatMessageTime(DateTime(2026, 7, 11, 8, 5), now: now),
      '昨天 08:05',
    );
    expect(
      formatChatMessageTime(DateTime(2026, 7, 10, 8, 5), now: now),
      '7月10日 08:05',
    );
  });
}
