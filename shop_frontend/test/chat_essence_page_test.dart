import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shop_frontend/pages/chat_essence_page.dart';
import 'package:shop_frontend/services/shop_api_service.dart';

void main() {
  testWidgets('unexpected pin failure always re-enables the essence action', (
    tester,
  ) async {
    var pinAttempts = 0;
    final api = ShopApiService(
      baseUrl: 'http://example.test/api',
      token: 'token-1',
      client: MockClient((request) async {
        if (request.method == 'PUT' && request.url.path.endsWith('/pin')) {
          pinAttempts++;
          throw StateError('transport failed');
        }
        if (request.url.path.endsWith('/essences')) {
          return _ok({
            'items': [
              {
                'message': {
                  'id': 21,
                  'conversationId': 7,
                  'senderUserId': 3,
                  'senderNickname': '成员',
                  'type': 'TEXT',
                  'content': '精华内容',
                  'createdAt': '2026-07-15T10:00:00',
                },
                'pinned': false,
                'likeCount': 0,
                'liked': false,
              },
            ],
            'unreadCount': 0,
          });
        }
        if (request.url.path.endsWith('/users/me')) {
          return _ok({'id': 1, 'nickname': '商家', 'role': 'SHOP'});
        }
        if (request.url.path.endsWith('/family-groups/7')) {
          return _ok({
            'id': 7,
            'name': '家庭群',
            'status': 'ACTIVE',
            'members': [
              {'userId': 1, 'nickname': '商家', 'role': 'OWNER'},
            ],
          });
        }
        return _ok(null);
      }),
    );
    await tester.pumpWidget(
      MaterialApp(home: ChatEssencePage(conversationId: 7, api: api)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('置顶'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byTooltip('置顶'), findsOneWidget);

    await tester.tap(find.byTooltip('置顶'));
    await tester.pumpAndSettle();
    expect(pinAttempts, 2);
    await tester.pump(const Duration(seconds: 3));
  });
}

http.Response _ok(Object? data) => http.Response(
  jsonEncode({'code': 0, 'message': 'success', 'data': data}),
  200,
  headers: {'content-type': 'application/json'},
);
