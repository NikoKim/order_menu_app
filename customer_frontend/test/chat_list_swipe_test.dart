import 'dart:async';
import 'dart:convert';

import 'package:customer_frontend/main.dart';
import 'package:customer_frontend/pages/chat_list_page.dart';
import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _ConversationClient extends http.BaseClient {
  bool pinned = true;
  bool hidden = false;
  String otherPreview = '旧消息';
  String firstPreview = '原消息';
  String firstLastMessageAt = '2026-07-15T09:00:00';
  int firstMessageVersion = 4;
  int firstUnreadCount = 0;
  int preferenceUpdates = 0;
  int deletes = 0;
  int stalePinnedReadsAfterUpdate = 0;
  int _stalePinnedReadsRemaining = 0;
  Completer<void>? preferenceGate;
  Completer<void>? deleteGate;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    Object? data;
    if (request.method == 'GET' &&
        request.url.path == '/api/messages/conversations') {
      final listPinned = _stalePinnedReadsRemaining > 0 ? true : pinned;
      if (_stalePinnedReadsRemaining > 0) _stalePinnedReadsRemaining--;
      data = <Object>[
        if (!hidden)
          {
            'id': 7,
            'type': 'DIRECT',
            'status': 'ACTIVE',
            'directUserNickname': '小周',
            'lastMessageContent': firstPreview,
            'lastMessageAt': firstLastMessageAt,
            'messageVersion': firstMessageVersion,
            'unreadCount': firstUnreadCount,
            'muted': true,
            'pinned': listPinned,
            'pinnedAt': listPinned ? '2026-07-13T09:00:00' : null,
          },
        {
          'id': 8,
          'type': 'DIRECT',
          'status': 'ACTIVE',
          'directUserNickname': '小吴',
          'lastMessageContent': otherPreview,
          'pinned': false,
        },
      ];
    } else if (request.method == 'PUT' &&
        request.url.path == '/api/messages/conversations/7/preferences') {
      preferenceUpdates++;
      final body = jsonDecode(await request.finalize().bytesToString());
      await preferenceGate?.future;
      pinned = body['pinned'] == true;
      _stalePinnedReadsRemaining = stalePinnedReadsAfterUpdate;
      data = {
        'conversationId': 7,
        'muted': false,
        'hidden': false,
        'pinned': pinned,
      };
    } else if (request.method == 'DELETE' &&
        request.url.path == '/api/messages/conversations/7') {
      deletes++;
      final expectedMessageVersion = int.tryParse(
        request.url.queryParameters['expectedMessageVersion'] ?? '',
      );
      expect(request.url.queryParameters['guarded'], 'true');
      await deleteGate?.future;
      final hideApplied = expectedMessageVersion == firstMessageVersion;
      if (hideApplied) hidden = true;
      data = {'hidden': hideApplied, 'messageVersion': firstMessageVersion};
    }
    return http.StreamedResponse(
      Stream.value(utf8.encode(jsonEncode({'code': 0, 'data': data}))),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

Future<void> _pumpChatList(
  WidgetTester tester,
  _ConversationClient client,
) async {
  final services = AppServices(
    apiClient: ApiClient(
      httpClient: client,
      baseUrl: 'https://example.test/api',
    ),
  );
  await tester.pumpWidget(
    AppScope(
      services: services,
      child: const MaterialApp(home: ChatListPage()),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _revealActions(WidgetTester tester) async {
  await tester.drag(find.text('小周'), const Offset(-180, 0));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('左滑后点击取消置顶只提交一次并保持取消状态', (tester) async {
    final client = _ConversationClient();
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    await tester.tap(find.text('取消置顶'));
    await tester.pumpAndSettle();

    expect(client.preferenceUpdates, 1);
    expect(client.pinned, isFalse);
    expect(find.text('置顶会话'), findsNothing);

    await tester.pump(const Duration(seconds: 4));
    await tester.pumpAndSettle();
    expect(client.preferenceUpdates, 1);
    expect(find.text('置顶会话'), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('左滑后点击删除会话能命中后方操作按钮', (tester) async {
    final client = _ConversationClient()..pinned = false;
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    await tester.tap(find.text('删除').first);
    await tester.pumpAndSettle();

    expect(client.deletes, 1);
    expect(find.text('小周'), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('取消置顶请求未完成时其他会话仍能静默刷新', (tester) async {
    final gate = Completer<void>();
    addTearDown(() {
      if (!gate.isCompleted) gate.complete();
    });
    final client = _ConversationClient()..preferenceGate = gate;
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    await tester.tap(find.text('取消置顶'));
    await tester.pump();
    client.otherPreview = '新消息';

    await tester.pump(const Duration(seconds: 3));
    await tester.pump();

    expect(find.text('新消息'), findsOneWidget);
    expect(client.preferenceUpdates, 1);

    gate.complete();
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('取消置顶后不会被紧随的旧列表快照弹回', (tester) async {
    final client = _ConversationClient()..stalePinnedReadsAfterUpdate = 1;
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    await tester.tap(find.text('取消置顶'));
    await tester.pumpAndSettle();
    expect(find.text('置顶会话'), findsNothing);

    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    expect(find.text('置顶会话'), findsNothing);
    expect(client.preferenceUpdates, 1);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('删除慢请求发出后立即隐藏会话并阻止再次操作', (tester) async {
    final gate = Completer<void>();
    addTearDown(() {
      if (!gate.isCompleted) gate.complete();
    });
    final client = _ConversationClient()
      ..pinned = false
      ..deleteGate = gate;
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    await tester.tap(find.text('删除').first);
    await tester.pump();

    expect(client.deletes, 1);
    expect(find.text('小周'), findsNothing);

    gate.complete();
    await tester.pumpAndSettle();
    expect(client.deletes, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('删除的免打扰会话在新消息到达后立即重新出现', (tester) async {
    final gate = Completer<void>();
    addTearDown(() {
      if (!gate.isCompleted) gate.complete();
    });
    final client = _ConversationClient()
      ..pinned = false
      ..deleteGate = gate;
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    await tester.tap(find.text('删除').first);
    await tester.pump();
    expect(find.text('小周'), findsNothing);

    client
      ..firstPreview = '删除后的新消息'
      ..firstLastMessageAt = '2026-07-15T09:01:00'
      ..firstMessageVersion = 5
      ..firstUnreadCount = 1;
    await tester.pump(const Duration(seconds: 3));
    await tester.pump();

    expect(find.text('小周'), findsOneWidget);
    expect(find.text('删除后的新消息'), findsOneWidget);

    gate.complete();
    await tester.pumpAndSettle();
    expect(find.text('小周'), findsOneWidget);
    expect(find.text('删除后的新消息'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('左滑菜单在点击其他区域后自动收起', (tester) async {
    final client = _ConversationClient()..pinned = false;
    await _pumpChatList(tester, client);

    await _revealActions(tester);
    expect(find.text('置顶').hitTestable(), findsOneWidget);

    await tester.tap(find.byType(TextField));
    await tester.pumpAndSettle();

    expect(find.text('置顶').hitTestable(), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
