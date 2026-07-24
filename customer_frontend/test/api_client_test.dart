import 'dart:async';
import 'dart:convert';

import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _HangingClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) =>
      Completer<http.StreamedResponse>().future;
}

class _RecordingClient extends http.BaseClient {
  http.BaseRequest? request;
  String? body;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    this.request = request;
    if (request is http.Request) body = request.body;
    final data =
        request.url.path.endsWith('/conversations/7/typing') &&
            request.method == 'GET'
        ? {
            'userIds': [18, '21'],
          }
        : request.url.path.endsWith('/conversations/7/messages')
        ? {
            'conversation': {'id': 7, 'type': 'DIRECT', 'status': 'ACTIVE'},
            'messages': <Object?>[],
            'hasMore': true,
            'nextBeforeId': 45,
          }
        : request.url.path.endsWith('/orders')
        ? {'id': 42, 'orderNo': 'OM-42'}
        : request.url.path.endsWith('/order-shares')
        ? {
            'id': 12,
            'conversationId': 7,
            'senderUserId': 1,
            'type': 'ORDER_SHARE',
            'content': 'order:42',
            'createdAt': '2026-07-13T08:00:00',
            'orderId': 42,
          }
        : request.url.path.endsWith('/conversations/7')
        ? {'hidden': true, 'messageVersion': 4}
        : request.method == 'GET'
        ? {'userId': 18, 'blockedByMe': true, 'blockedMe': false}
        : null;
    return http.StreamedResponse(
      Stream.value(utf8.encode(jsonEncode({'code': 0, 'data': data}))),
      200,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );
  }
}

void main() {
  test('times out instead of waiting forever for an unreachable API', () async {
    final api = ApiClient(
      httpClient: _HangingClient(),
      requestTimeout: const Duration(milliseconds: 10),
    );

    await expectLater(
      api.get<Object?>('/shops', (json) => json),
      throwsA(isA<TimeoutException>()),
    );
  });

  test('customer chat block APIs use the user-scoped endpoints', () async {
    final client = _RecordingClient();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );

    final ChatBlock relationship = await api.chatBlock(18);
    expect(relationship.blockedByMe, isTrue);
    expect(client.request?.url.path, '/api/messages/blocks/users/18');

    await api.blockChatUser(18);
    expect(client.request?.method, 'POST');
    expect(client.request?.url.path, '/api/messages/blocks/users/18');

    await api.unblockChatUser(18);
    expect(client.request?.method, 'DELETE');
    expect(client.request?.url.path, '/api/messages/blocks/users/18');
  });

  test(
    'order creation sends the caller-stable idempotency identifier',
    () async {
      final client = _RecordingClient();
      final api = CustomerApi(
        ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
      );

      await api.createOrder(
        clientRequestId: '8a71804e-cf67-4a61-a385-b52df15069cf',
        shopId: 9,
        items: const [],
      );

      expect(client.request?.url.path, '/api/orders');
      expect(
        jsonDecode(client.body!)['clientRequestId'],
        '8a71804e-cf67-4a61-a385-b52df15069cf',
      );
    },
  );

  test(
    'order share sends both the conversation and order identifiers',
    () async {
      final client = _RecordingClient();
      final api = CustomerApi(
        ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
      );

      final message = await api.shareOrder(conversationId: 7, orderId: 42);

      expect(message.isOrderShare, isTrue);
      expect(client.request?.url.path, '/api/messages/order-shares');
      expect(jsonDecode(client.body!), {'conversationId': 7, 'orderId': 42});
    },
  );

  test('conversation delete uses a guarded message-version token', () async {
    final client = _RecordingClient();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );

    final result = await api.hideConversation(7, expectedMessageVersion: 4);

    expect(client.request?.url.path, '/api/messages/conversations/7');
    expect(client.request?.url.queryParameters, {
      'guarded': 'true',
      'expectedMessageVersion': '4',
    });
    expect(result.hidden, isTrue);
    expect(result.messageVersion, 4);
  });

  test('chat pagination and explicit read use cursor contracts', () async {
    final client = _RecordingClient();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );

    final page = await api.chatMessages(7, beforeId: 80, limit: 25);

    expect(client.request?.url.path, '/api/messages/conversations/7/messages');
    expect(client.request?.url.queryParameters, {
      'beforeId': '80',
      'limit': '25',
    });
    expect(page.hasMore, isTrue);
    expect(page.nextBeforeId, 45);

    await api.markConversationRead(7, 99);
    expect(client.request?.method, 'PUT');
    expect(client.request?.url.path, '/api/messages/conversations/7/read');
    expect(jsonDecode(client.body!), {'lastReadMessageId': 99});
  });

  test('presence and typing APIs use the realtime chat contracts', () async {
    final client = _RecordingClient();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );

    await api.heartbeatPresence();
    expect(client.request?.method, 'PUT');
    expect(client.request?.url.path, '/api/messages/presence/online');

    await api.setConversationTyping(7, true);
    expect(client.request?.method, 'PUT');
    expect(client.request?.url.path, '/api/messages/conversations/7/typing');
    expect(jsonDecode(client.body!), {'active': true});

    final users = await api.typingUsers(7);
    expect(client.request?.method, 'GET');
    expect(client.request?.url.path, '/api/messages/conversations/7/typing');
    expect(users, {18, 21});
  });
}
