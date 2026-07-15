import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/shop_api_models.dart';
import 'package:shop_frontend/services/api_config.dart';

void main() {
  test('family member status can be updated optimistically', () {
    const member = FamilyMemberInfo(
      id: 7,
      userId: 8,
      nickname: '顾客',
      status: 'PENDING',
    );

    final approved = member.withStatus('APPROVED');

    expect(approved.status, 'APPROVED');
    expect(approved.id, member.id);
    expect(approved.nickname, member.nickname);
  });

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

  test('muted conversation keeps unread badge for gray rendering', () {
    final conversation = ChatConversation.fromJson({
      'id': 6,
      'type': 'FAMILY_GROUP',
      'status': 'ACTIVE',
      'unreadCount': 2,
      'muted': true,
    });

    expect(conversation.shouldShowUnreadBadge, isTrue);
    expect(conversation.muted, isTrue);
  });

  test('marking a conversation read clears only its unread state', () {
    final conversation = ChatConversation.fromJson({
      'id': 6,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'directUserNickname': 'Alice',
      'lastMessageContent': 'hello',
      'unreadCount': 2,
    });

    final read = conversation.markedRead();

    expect(read.unreadCount, 0);
    expect(read.shouldShowUnreadBadge, isFalse);
    expect(read.title, 'Alice');
    expect(read.lastMessageContent, 'hello');
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

  test('formats conversation list timestamps relative to today', () {
    final now = DateTime(2026, 7, 12, 18, 30);

    expect(formatChatListTime('2026-07-12T08:05:00', now: now), '08:05');
    expect(formatChatListTime('2026-07-11T08:05:00', now: now), '07/11');
    expect(formatChatListTime('2025-07-11T08:05:00', now: now), '2025/07/11');
    expect(formatChatListTime(null, now: now), '');
  });

  test('parses message lifecycle and group approval fields', () {
    final message = ChatMessage.fromJson({
      'id': 10,
      'conversationId': 4,
      'senderUserId': 3,
      'type': 'TEXT',
      'content': '回复',
      'createdAt': '2026-07-12T08:05:00',
      'quotedMessageId': 9,
      'quotedContent': '原消息',
      'recalledAt': '2026-07-12T08:06:00',
    });
    final group = FamilyGroup.fromJson({
      'id': 8,
      'shopId': 3,
      'name': '家庭群聊',
      'status': 'ACTIVE',
      'joinApprovalRequired': true,
      'members': [],
    });
    expect(message.quotedMessageId, 9);
    expect(message.isRecalled, isTrue);
    expect(group.joinApprovalRequired, isTrue);
    expect(familyMemberStatusLabel('PENDING'), '待处理');
  });

  test('formats chat message time using 24 hour rules', () {
    final now = DateTime(2026, 7, 12, 18);
    expect(
      formatChatMessageTime(DateTime(2026, 7, 12, 8, 5), now: now),
      '08:05',
    );
    expect(
      formatChatMessageTime(DateTime(2026, 7, 11, 8, 5), now: now),
      '昨天 08:05',
    );
  });

  test('chat block state distinguishes both blocking directions', () {
    final state = ChatBlockState.fromJson({
      'userId': 18,
      'blockedByMe': true,
      'blockedMe': false,
    });

    expect(state.userId, 18);
    expect(state.blockedByMe, isTrue);
    expect(state.blockedMe, isFalse);
  });

  test('conversation parses pin and merchant profile metadata', () {
    final conversation = ChatConversation.fromJson({
      'id': 20,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'pinned': true,
      'pinnedAt': '2026-07-13T09:00:00',
      'directUserRole': 'SHOP',
      'directShopId': 3,
      'directShopName': '暖橙小店',
    });

    expect(conversation.pinned, isTrue);
    expect(conversation.directUserRole, 'SHOP');
    expect(conversation.directShopName, '暖橙小店');
  });

  test('conversation and hide result parse the guarded message version', () {
    final conversation = ChatConversation.fromJson({
      'id': 20,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'messageVersion': 17,
    });
    final result = ConversationHideResult.fromJson({
      'hidden': false,
      'messageVersion': 18,
    });

    expect(conversation.messageVersion, 17);
    expect(result.hidden, isFalse);
    expect(result.messageVersion, 18);
  });

  test('optimistic unpin clears the pin timestamp', () {
    final conversation = ChatConversation.fromJson({
      'id': 20,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'pinned': true,
      'pinnedAt': '2026-07-13T09:00:00',
    });

    final unpinned = conversation.withPreferences(pinned: false);

    expect(unpinned.pinned, isFalse);
    expect(unpinned.pinnedAt, isNull);
  });

  test('image and essence metadata are retained by chat messages', () {
    final message = ChatMessage.fromJson({
      'id': 21,
      'conversationId': 4,
      'senderUserId': 3,
      'type': 'IMAGE_SHARE',
      'imageUrl': '/chat/21.jpg',
      'imageExpiresAt': '2026-08-13T09:00:00',
      'essence': true,
      'essenceLabel': '群公告',
      'essenceLikes': 5,
      'essenceLikedByMe': true,
      'createdAt': '2026-07-13T09:00:00',
    });

    expect(message.isImage, isTrue);
    expect(message.imageUrl, '/chat/21.jpg');
    expect(message.isEssence, isTrue);
    expect(message.essenceLikes, 5);
  });

  test('essence pin metadata can be toggled optimistically', () {
    final message = ChatMessage.fromJson({
      'id': 21,
      'conversationId': 4,
      'senderUserId': 3,
      'type': 'TEXT',
      'content': '精华',
      'essence': true,
      'essencePinned': true,
      'createdAt': '2026-07-13T09:00:00',
    });

    expect(message.withEssencePinned(false).essencePinned, isFalse);
  });

  test('chat user profile distinguishes merchants and public shop details', () {
    final profile = ChatUserProfile.fromJson({
      'userId': 8,
      'nickname': '店主',
      'role': 'SHOP',
      'shopId': 3,
      'shopName': '暖橙小店',
      'joinedFamilies': ['午餐家庭'],
    });

    expect(profile.isMerchant, isTrue);
    expect(profile.shopName, '暖橙小店');
    expect(profile.joinedFamilies, ['午餐家庭']);
  });

  test('essence list parses nested frozen-contract items', () {
    final list = ChatEssenceList.fromJson({
      'unreadCount': 1,
      'items': [
        {
          'pinned': true,
          'liked': true,
          'likeCount': 4,
          'message': {
            'id': 31,
            'conversationId': 9,
            'senderUserId': 2,
            'type': 'TEXT',
            'content': '重要消息',
            'createdAt': '2026-07-13T10:00:00',
          },
        },
      ],
    });

    expect(list.unreadCount, 1);
    expect(list.items.single.isEssence, isTrue);
    expect(list.items.single.essencePinned, isTrue);
    expect(list.items.single.essenceLikedByMe, isTrue);
    expect(list.items.single.essenceLikes, 4);
  });

  test('hidden user profile privacy fields are not exposed', () {
    final profile = ChatUserProfile.fromJson({
      'userId': 8,
      'nickname': '用户',
      'phone': '13800000000',
      'phoneVisible': false,
      'joinedFamiliesVisible': false,
      'joinedFamilies': [
        {'shopName': '不应显示'},
      ],
      'shopProfile': {
        'shopId': 3,
        'shopName': '公开店铺',
        'shopLogoUrl': '/logo.png',
      },
    });

    expect(profile.phone, isNull);
    expect(profile.joinedFamilies, isEmpty);
    expect(profile.shopName, '公开店铺');
  });
}
