import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/shop_api_models.dart';
import 'package:shop_frontend/pages/chat_detail_page.dart';

void main() {
  ChatMessage quotedCard(String type) => ChatMessage.fromJson({
    'id': type.hashCode,
    'conversationId': 7,
    'senderUserId': 2,
    'type': type,
    'content': type == 'IMAGE_SHARE' ? '' : '卡片内容',
    'createdAt': '2026-07-15T10:00:00',
    'dishName': '招牌菜',
    'orderShopName': '测试店铺',
    'orderNo': 'A001',
    'imageExpired': type == 'IMAGE_SHARE',
    'sharedShopName': '分享店铺',
    'quotedMessageId': 99,
    'quotedSenderName': '引用用户',
    'quotedMessage': {'id': 99, 'type': 'TEXT', 'content': '被引用的消息'},
  });

  for (final type in [
    'DISH_SHARE',
    'ORDER_SHARE',
    'IMAGE_SHARE',
    'SHOP_SHARE',
  ]) {
    testWidgets('$type renders and opens its quoted-message preview', (
      tester,
    ) async {
      var quoteTaps = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ShopChatMessageContent(
              message: quotedCard(type),
              isMine: false,
              onQuoteTap: () => quoteTaps++,
            ),
          ),
        ),
      );

      expect(find.text('被引用的消息'), findsOneWidget);
      await tester.tap(find.text('被引用的消息'));
      expect(quoteTaps, 1);
    });
  }

  testWidgets('quoted image tap locates the original message', (tester) async {
    var quoteTaps = 0;
    final message = ChatMessage.fromJson({
      'id': 301,
      'conversationId': 7,
      'senderUserId': 2,
      'type': 'TEXT',
      'content': 'reply',
      'createdAt': '2026-07-15T10:00:00',
      'quotedMessageId': 299,
      'quotedSenderName': 'sender',
      'quotedMessage': {
        'id': 299,
        'type': 'IMAGE_SHARE',
        'content': '',
        'imageUrl': '/quoted.jpg',
      },
    });
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ShopChatMessageContent(
            message: message,
            isMine: false,
            onQuoteTap: () => quoteTaps++,
          ),
        ),
      ),
    );

    final imageGesture = tester.widget<GestureDetector>(
      find.byKey(const ValueKey('chat-image-interaction-299')),
    );
    expect(imageGesture.onTap, isNull);
    await tester.tap(find.byType(Image));
    await tester.pump();
    tester.takeException();

    expect(quoteTaps, 1);
    expect(find.text('查看图片'), findsNothing);
  });
}
