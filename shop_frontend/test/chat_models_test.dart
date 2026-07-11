import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/shop_api_models.dart';
import 'package:shop_frontend/services/api_config.dart';

void main() {
  test('conversation exposes its direct chat peer and group state', () {
    final direct = ChatConversation.fromJson({
      'id': 4,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'directUserNickname': '顾客甲',
    });
    final group = FamilyGroup.fromJson({
      'id': 8,
      'shopId': 3,
      'name': '午餐家庭群',
      'status': 'ACTIVE',
      'members': [],
    });

    expect(direct.title, '顾客甲');
    expect(direct.isDirect, isTrue);
    expect(group.canManage, isTrue);
  });

  test('conversation retains preview and unread badge state', () {
    final conversation = ChatConversation.fromJson({
      'id': 5,
      'type': 'FAMILY_GROUP',
      'status': 'ACTIVE',
      'lastMessageContent': 'Dinner is ready',
      'unreadCount': 3,
      'muted': false,
    });

    expect(conversation.lastMessageContent, 'Dinner is ready');
    expect(conversation.unreadCount, 3);
    expect(conversation.shouldShowUnreadBadge, isTrue);
  });

  test('muted conversation suppresses unread badge', () {
    final conversation = ChatConversation.fromJson({
      'id': 6,
      'type': 'FAMILY_GROUP',
      'status': 'ACTIVE',
      'unreadCount': 2,
      'muted': true,
    });

    expect(conversation.shouldShowUnreadBadge, isFalse);
  });

  test('family group creation is available only with two approved members', () {
    expect(FamilyGroupEligibility.fromMembers(1).canCreate, isFalse);
    expect(FamilyGroupEligibility.fromMembers(2).canCreate, isTrue);
  });

  test(
    'chat messages retain dish share details and identify nearby timestamps',
    () {
      final dishShare = ChatMessage.fromJson({
        'id': 9,
        'conversationId': 4,
        'senderUserId': 3,
        'type': 'DISH_SHARE',
        'dishId': 12,
        'shopId': 7,
        'dishName': 'Test dish',
        'dishImageUrl': 'https://example.test/dish.png',
        'dishPrice': 26.5,
        'createdAt': '2026-07-11T10:00:30',
      });

      expect(dishShare.isDishShare, isTrue);
      expect(dishShare.dishName, 'Test dish');
      expect(dishShare.dishPrice, 26.5);
      expect(
        shouldShowChatTimestamp(
          DateTime.parse('2026-07-11T10:00:30'),
          DateTime.parse('2026-07-11T10:00:00'),
        ),
        isFalse,
      );
      expect(
        shouldShowChatTimestamp(
          DateTime.parse('2026-07-11T10:01:01'),
          DateTime.parse('2026-07-11T10:00:00'),
        ),
        isTrue,
      );
    },
  );

  test('chat media resolves relative backend paths', () {
    expect(
      ApiConfig.resolveImageUrl('/images/dish.png'),
      'https://order-menu-backend.zsj-unknown.top/images/dish.png',
    );
    expect(
      ApiConfig.resolveImageUrl('images/avatar.png'),
      'https://order-menu-backend.zsj-unknown.top/images/avatar.png',
    );
    expect(
      ApiConfig.resolveImageUrl('https://cdn.example.test/avatar.png'),
      'https://cdn.example.test/avatar.png',
    );
  });
}
