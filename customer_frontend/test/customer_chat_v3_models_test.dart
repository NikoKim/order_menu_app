import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/pages/shop_share_page.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('ordinary shops can target every active direct conversation', () {
    final conversations = [
      ChatConversation.fromJson({
        'id': 1,
        'type': 'DIRECT',
        'status': 'ACTIVE',
        'directUserId': 7,
      }),
      ChatConversation.fromJson({
        'id': 2,
        'type': 'DIRECT',
        'status': 'INACTIVE',
        'directUserId': 8,
      }),
    ];

    final targets = eligibleShopShareTargets(
      conversations,
      familyShop: false,
      familyConversationId: null,
      familyMemberIds: const {},
    );

    expect(targets.map((item) => item.id), [1]);
  });

  test('customer privacy fields are decoded', () {
    final user = CustomerUser.fromJson({
      'id': 1,
      'nickname': '小林',
      'role': 'CUSTOMER',
      'showJoinedFamilies': false,
      'showPhone': false,
    });

    expect(user.showJoinedFamilies, isFalse);
    expect(user.showPhone, isFalse);
  });

  test('conversation preferences expose pinned hidden and muted badge', () {
    final conversation = ChatConversation.fromJson({
      'id': 9,
      'type': 'DIRECT',
      'status': 'ACTIVE',
      'pinned': true,
      'pinnedAt': '2026-07-13T10:00:00',
      'hidden': true,
      'muted': true,
      'mutedBadge': true,
      'unreadCount': 3,
    });

    expect(conversation.pinned, isTrue);
    expect(conversation.hidden, isTrue);
    expect(conversation.mutedBadge, isTrue);
    expect(conversation.showMutedBadge, isTrue);
  });

  test('image shop and essence fields are decoded on a message', () {
    final message = ChatMessage.fromJson({
      'id': 2,
      'conversationId': 9,
      'senderUserId': 1,
      'type': 'IMAGE_SHARE',
      'content': '看看这个',
      'createdAt': '2026-07-13T10:00:00',
      'imageUrl': '/message_chat/9/a.jpg',
      'imageExpiresAt': '2026-08-13T03:00:00',
      'imageExpired': false,
      'essence': true,
      'essencePinned': true,
      'essenceLikeCount': 8,
    });

    expect(message.isImageShare, isTrue);
    expect(message.imageUrl, '/message_chat/9/a.jpg');
    expect(message.essence, isTrue);
    expect(message.essencePinned, isTrue);
    expect(message.essenceLikeCount, 8);
  });

  test('chat profile includes merchant identity and shop information', () {
    final profile = ChatUserProfile.fromJson({
      'userId': 4,
      'nickname': '店长',
      'role': 'SHOP',
      'phone': '13800000000',
      'phoneVisible': true,
      'joinedFamiliesVisible': true,
      'shopProfile': {
        'shopId': 6,
        'shopName': '暖食小馆',
        'shopLogoUrl': '/logo.jpg',
      },
    });

    expect(profile.isMerchant, isTrue);
    expect(profile.shopName, '暖食小馆');
  });

  test('essence list unwraps message snapshots and item metadata', () {
    final list = ChatEssenceList.fromJson({
      'unreadCount': 1,
      'items': [
        {
          'message': {
            'id': 12,
            'conversationId': 9,
            'senderUserId': 2,
            'type': 'TEXT',
            'content': '重要消息',
            'createdAt': '2026-07-13T10:00:00',
          },
          'pinned': true,
          'liked': true,
          'likeCount': 3,
        },
      ],
    });

    expect(list.items.single.id, 12);
    expect(list.items.single.essence, isTrue);
    expect(list.items.single.essencePinned, isTrue);
    expect(list.items.single.essenceLikedByMe, isTrue);
    expect(list.items.single.essenceLikeCount, 3);
  });
}
