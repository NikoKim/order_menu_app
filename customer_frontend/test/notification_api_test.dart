import 'dart:convert';

import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _NotificationClient extends http.BaseClient {
  final requests = <http.BaseRequest>[];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    final path = request.url.path;
    final body = switch (path) {
      '/api/messages/notifications' =>
        '{"code":0,"data":{"unreadCount":1,"notifications":[{"id":4,"userId":2,"type":"ORDER_STATUS","title":"Order","content":"Ready","createdAt":"2026-07-11T08:00:00"}]}}',
      '/api/messages/notifications/unread-count' => '{"code":0,"data":3}',
      _ => '{"code":0,"data":null}',
    };
    return http.StreamedResponse(
      Stream.value(utf8.encode(body)),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

void main() {
  test('loads notification list and exposes read operations', () async {
    final client = _NotificationClient();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );

    final list = await api.notifications();
    final unread = await api.unreadNotificationCount();
    await api.markNotificationRead(4);
    await api.markAllNotificationsRead();

    expect(list.notifications.single.title, 'Order');
    expect(list.unreadCount, 1);
    expect(unread, 3);
    expect(
      client.requests.map((request) => '${request.method} ${request.url.path}'),
      containsAll([
        'GET /api/messages/notifications',
        'GET /api/messages/notifications/unread-count',
        'PUT /api/messages/notifications/4/read',
        'PUT /api/messages/notifications/read-all',
      ]),
    );
  });
}
