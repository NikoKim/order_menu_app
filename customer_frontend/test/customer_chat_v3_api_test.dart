import 'dart:convert';
import 'dart:async';

import 'package:customer_frontend/services/api_client.dart';
import 'package:customer_frontend/services/push_device_registration.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _V3Client extends http.BaseClient {
  final requests = <http.BaseRequest>[];
  final bodies = <String?>[];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    bodies.add(request is http.Request ? request.body : null);
    final path = request.url.path;
    final Object? data = switch (path) {
      '/api/messages/users/7/profile' => {
        'userId': 7,
        'nickname': '小周',
        'role': 'CUSTOMER',
        'phoneVisible': false,
      },
      '/api/messages/conversations/9/preferences' => {
        'conversationId': 9,
        'muted': true,
        'hidden': false,
        'pinned': true,
      },
      '/api/messages/conversations/9' => {'hidden': true, 'messageVersion': 6},
      '/api/messages/shop-shares' => {
        'id': 3,
        'conversationId': 9,
        'senderUserId': 1,
        'type': 'SHOP_SHARE',
        'content': '',
        'createdAt': '2026-07-13T10:00:00',
        'sharedShopId': 5,
      },
      '/api/messages/family-groups/9/essences' => {
        'unreadCount': 0,
        'items': <Object>[],
      },
      '/api/messages/family-groups/9/essences/latest-unread' => {
        'message': {
          'id': 8,
          'conversationId': 9,
          'senderUserId': 2,
          'type': 'TEXT',
          'content': '精华消息',
          'createdAt': '2026-07-13T10:00:00',
        },
        'pinned': true,
        'likeCount': 4,
        'liked': true,
      },
      _ => null,
    };
    return http.StreamedResponse(
      Stream.value(utf8.encode(jsonEncode({'code': 0, 'data': data}))),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

class _PushProvider implements PushTokenProvider {
  _PushProvider(this.token);
  final String? token;
  final refreshes = StreamController<String>.broadcast();

  @override
  Future<String?> getToken() async => token;

  @override
  Stream<String> get onTokenRefresh => refreshes.stream;
}

void main() {
  test(
    'profile and conversation preference APIs use frozen contracts',
    () async {
      final client = _V3Client();
      final api = CustomerApi(
        ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
      );

      final profile = await api.chatUserProfile(7, conversationId: 9);
      final preferences = await api.updateConversationPreferences(
        9,
        muted: true,
        pinned: true,
      );
      final hidden = await api.hideConversation(9, expectedMessageVersion: 6);

      expect(profile.phone, isNull);
      expect(client.requests.first.url.queryParameters['conversationId'], '9');
      expect(preferences.pinned, isTrue);
      expect(jsonDecode(client.bodies[1]!), {'muted': true, 'pinned': true});
      expect(client.requests.last.method, 'DELETE');
      expect(client.requests.last.url.queryParameters, {
        'guarded': 'true',
        'expectedMessageVersion': '6',
      });
      expect(hidden.hidden, isTrue);
    },
  );

  test('shop share essence and push APIs use message endpoints', () async {
    final client = _V3Client();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );

    final message = await api.shareShop(conversationId: 9, shopId: 5);
    await api.chatEssences(9);
    final latestEssence = await api.latestUnreadEssence(9);
    await api.setEssenceLiked(9, 3, true);
    await api.registerPushDevice(token: 'device-token', platform: 'ANDROID');

    expect(message.isShopShare, isTrue);
    expect(latestEssence?.id, 8);
    expect(latestEssence?.essencePinned, isTrue);
    expect(
      client.requests.map((request) => request.url.path),
      containsAll([
        '/api/messages/shop-shares',
        '/api/messages/family-groups/9/essences',
        '/api/messages/family-groups/9/essences/3/like',
        '/api/messages/push-devices',
      ]),
    );
  });

  test(
    'push registration skips missing config and forwards token refreshes',
    () async {
      final client = _V3Client();
      final api = CustomerApi(
        ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
      );
      final missing = PushDeviceRegistration(
        provider: const NoopPushTokenProvider(),
      );
      await missing.sync(api);
      expect(client.requests, isEmpty);

      final provider = _PushProvider('first-token');
      final registration = PushDeviceRegistration(provider: provider);
      await registration.sync(api);
      provider.refreshes.add('second-token');
      await Future<void>.delayed(Duration.zero);

      expect(client.requests, hasLength(3));
      expect(client.requests[1].method, 'DELETE');
      expect(
        client.requests[1].url.path,
        endsWith('/push-devices/first-token'),
      );
      expect(
        jsonDecode(client.bodies[0]!),
        containsPair('token', 'first-token'),
      );
      expect(
        jsonDecode(client.bodies[2]!),
        containsPair('token', 'second-token'),
      );
      await provider.refreshes.close();
    },
  );
}
