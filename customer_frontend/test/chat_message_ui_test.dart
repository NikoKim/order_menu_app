import 'dart:convert';

import 'package:customer_frontend/main.dart';
import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/pages/chat_detail_page.dart';
import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

class _LongChatClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    Object? data;
    if (request.url.path == '/api/messages/conversations/7/messages') {
      data = {
        'conversation': {
          'id': 7,
          'type': 'DIRECT',
          'status': 'ACTIVE',
          'directUserId': 2,
          'directUserNickname': '小周',
        },
        'messages': List.generate(80, (index) {
          final id = index + 1;
          return {
            'id': id,
            'conversationId': 7,
            'senderUserId': id.isEven ? 1 : 2,
            'type': 'TEXT',
            'content': id == 1 ? '最早的屏幕外目标' : '消息 $id',
            'createdAt': '2026-07-15T09:${index.toString().padLeft(2, '0')}:00',
            if (id == 80) ...{
              'quotedMessageId': 1,
              'quotedContent': '引用预览',
              'quotedSenderName': '小周',
            },
          };
        }),
      };
    } else if (request.url.path == '/api/messages/blocks/users/2') {
      data = {'userId': 2, 'blockedByMe': false, 'blockedMe': false};
    }
    return http.StreamedResponse(
      Stream.value(utf8.encode(jsonEncode({'code': 0, 'data': data}))),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

ChatMessage _message({
  String type = 'TEXT',
  String content = '',
  String? imageUrl,
  int? quotedMessageId,
}) => ChatMessage(
  id: 1,
  conversationId: 1,
  senderUserId: 1,
  type: type,
  content: content,
  createdAt: '2026-07-15T09:00:00',
  imageUrl: imageUrl,
  quotedMessageId: quotedMessageId,
  quotedContent: quotedMessageId == null ? null : '被引用的内容',
  quotedSenderName: quotedMessageId == null ? null : '小周',
);

void main() {
  testWidgets('菜品分享卡片保留左侧视觉间距', (tester) async {
    final message = ChatMessage(
      id: 1,
      conversationId: 1,
      senderUserId: 1,
      type: 'DISH_SHARE',
      content: '',
      createdAt: '2026-07-15T09:00:00',
      dishId: 3,
      shopId: 2,
      dishName: '番茄炒蛋',
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: DishShareCard(message: message)),
      ),
    );

    final card = tester.widget<Container>(
      find
          .descendant(
            of: find.byType(DishShareCard),
            matching: find.byType(Container),
          )
          .first,
    );
    expect(card.margin, const EdgeInsets.only(left: 8));
  });

  testWidgets('带文字图片使用气泡而纯图片保持无气泡', (tester) async {
    await tester.pumpWidget(
      AppScope(
        services: AppServices(apiClient: ApiClient()),
        child: MaterialApp(
          home: Scaffold(
            body: ListView(
              children: [
                ChatImageCard(
                  key: const ValueKey('with-text'),
                  message: _message(
                    type: 'IMAGE',
                    content: '晚餐照片',
                    imageUrl: '/image.jpg',
                  ),
                  mine: true,
                ),
                ChatImageCard(
                  key: const ValueKey('image-only'),
                  message: _message(type: 'IMAGE', imageUrl: '/image.jpg'),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    final withText = tester.widget<Container>(
      find
          .descendant(
            of: find.byKey(const ValueKey('with-text')),
            matching: find.byType(Container),
          )
          .first,
    );
    final imageOnly = tester.widget<Container>(
      find
          .descendant(
            of: find.byKey(const ValueKey('image-only')),
            matching: find.byType(Container),
          )
          .first,
    );
    expect((withText.decoration! as BoxDecoration).color, isNotNull);
    expect((imageOnly.decoration! as BoxDecoration).color, Colors.transparent);
  });

  testWidgets('超长最新消息仍将末尾锚定在输入区一侧', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 360));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              Expanded(
                child: ScrollablePositionedList.builder(
                  reverse: true,
                  itemCount: 2,
                  itemBuilder: (_, index) => index == 0
                      ? const SizedBox(
                          height: 520,
                          child: Align(
                            alignment: Alignment.bottomCenter,
                            child: Text('最新消息末尾'),
                          ),
                        )
                      : const SizedBox(height: 48, child: Text('旧消息')),
                ),
              ),
              const SizedBox(height: 56, child: Text('输入区')),
            ],
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('最新消息末尾'), findsOneWidget);
    expect(
      tester.getBottomLeft(find.text('最新消息末尾')).dy,
      lessThanOrEqualTo(tester.getTopLeft(find.text('输入区')).dy),
    );
  });

  test('emoji 集合覆盖常用表情、手势、食物和活动', () {
    expect(customerChatEmojis.length, greaterThanOrEqualTo(100));
    expect(customerChatEmojis, containsAll(['😀', '👍', '🍜', '🎉', '❤️']));
  });

  testWidgets('菜品卡片消息也显示可点击的引用预览', (tester) async {
    var tapped = false;
    final message = ChatMessage(
      id: 2,
      conversationId: 1,
      senderUserId: 1,
      type: 'DISH_SHARE',
      content: '',
      createdAt: '2026-07-15T09:00:00',
      dishId: 3,
      shopId: 2,
      dishName: '番茄炒蛋',
      quotedMessageId: 1,
      quotedContent: '被引用的文字',
      quotedSenderName: '小周',
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ChatMessageRow(
            message: message,
            mine: true,
            groupChat: false,
            onQuoteTap: () => tapped = true,
          ),
        ),
      ),
    );

    expect(find.byType(QuotedMessagePreview), findsOneWidget);
    expect(find.text('被引用的文字'), findsOneWidget);
    await tester.tap(find.byType(QuotedMessagePreview));
    expect(tapped, isTrue);
  });

  test('屏幕外引用目标的可见性判断不会误判为已删除', () {
    expect(
      chatVisualIndexIsVisible(20, const [
        ItemPosition(index: 0, itemLeadingEdge: 0, itemTrailingEdge: 1),
      ]),
      isFalse,
    );
    expect(
      chatVisualIndexIsVisible(20, const [
        ItemPosition(index: 20, itemLeadingEdge: .2, itemTrailingEdge: .7),
      ]),
      isTrue,
    );
  });

  testWidgets('点击引用可跳到真实列表中的屏幕外消息并高亮', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final services = AppServices(
      apiClient: ApiClient(
        httpClient: _LongChatClient(),
        baseUrl: 'https://example.test/api',
      ),
    );
    services.auth.user = const CustomerUser(
      id: 1,
      nickname: '我',
      role: 'CUSTOMER',
    );
    const conversation = ChatConversation(
      id: 7,
      type: 'DIRECT',
      status: 'ACTIVE',
      directUserId: 2,
      directUserNickname: '小周',
    );
    await tester.pumpWidget(
      AppScope(
        services: services,
        child: const MaterialApp(
          home: ChatDetailPage(conversation: conversation),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('最早的屏幕外目标').hitTestable(), findsNothing);
    await tester.tap(find.byType(QuotedMessagePreview));
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.text('最早的屏幕外目标').hitTestable(), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
