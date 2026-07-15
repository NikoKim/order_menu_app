import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shop_frontend/services/shop_api_service.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('login sends SHOP role and stores token', () async {
    final service = ShopApiService(
      baseUrl: 'http://example.test/api',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.toString(), 'http://example.test/api/auth/login');
        expect(jsonDecode(request.body), {
          'nickname': 'shop',
          'password': 'secret',
          'role': 'SHOP',
        });
        return http.Response(
          jsonEncode({
            'code': 0,
            'message': 'success',
            'data': {
              'token': 'abc',
              'user': {'id': 1, 'nickname': 'shop', 'role': 'SHOP'},
            },
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final result = await service.login(nickname: 'shop', password: 'secret');

    expect(result.token, 'abc');
    expect(service.token, 'abc');
    final preferences = await SharedPreferences.getInstance();
    expect(preferences.getString('shop_auth_token'), 'abc');
  });

  test('authorized requests use bearer token and parse pages', () async {
    final service = ShopApiService(
      baseUrl: 'http://example.test/api',
      token: 'token-1',
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.toString(), contains('/api/shop/dishes'));
        expect(request.headers['Authorization'], 'Bearer token-1');
        return http.Response(
          jsonEncode({
            'code': 0,
            'message': 'success',
            'data': {
              'records': [
                {
                  'id': 1,
                  'shopId': 2,
                  'categoryId': 3,
                  'name': '牛肉饭',
                  'price': 26,
                  'stock': 9,
                  'salesCount': 7,
                  'status': 'ON_SALE',
                  'sortOrder': 1,
                },
              ],
              'page': 1,
              'pageSize': 100,
              'total': 1,
            },
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final result = await service.fetchDishes();

    expect(result.total, 1);
    expect(result.records.single.name, '牛肉饭');
    expect(result.records.single.isOnSale, isTrue);
  });

  test('conversation preferences use the frozen message contract', () async {
    final service = ShopApiService(
      baseUrl: 'http://example.test/api',
      token: 'token-1',
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(
          request.url.toString(),
          'http://example.test/api/messages/conversations/9/preferences',
        );
        expect(jsonDecode(request.body), {
          'muted': true,
          'hidden': null,
          'pinned': true,
        });
        return http.Response(
          jsonEncode({
            'code': 0,
            'message': 'success',
            'data': {
              'conversationId': 9,
              'muted': true,
              'hidden': false,
              'pinned': true,
            },
          }),
          200,
        );
      }),
    );

    await service.updateConversationPreferences(9, muted: true, pinned: true);
  });

  test('guarded conversation deletion includes the message version', () async {
    final service = ShopApiService(
      baseUrl: 'http://example.test/api',
      token: 'token-1',
      client: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/api/messages/conversations/9');
        expect(request.url.queryParameters, {
          'guarded': 'true',
          'expectedMessageVersion': '12',
        });
        return http.Response(
          jsonEncode({
            'code': 0,
            'data': {'hidden': true, 'messageVersion': 12},
          }),
          200,
        );
      }),
    );

    final result = await service.deleteConversation(
      9,
      expectedMessageVersion: 12,
    );

    expect(result.hidden, isTrue);
    expect(result.messageVersion, 12);
  });

  test(
    'guarded empty conversation deletion omits the snapshot parameter',
    () async {
      final service = ShopApiService(
        baseUrl: 'http://example.test/api',
        token: 'token-1',
        client: MockClient((request) async {
          expect(request.url.queryParameters, {'guarded': 'true'});
          return http.Response(
            jsonEncode({
              'code': 0,
              'data': {'hidden': false, 'messageVersion': 0},
            }),
            200,
          );
        }),
      );

      final result = await service.deleteConversation(9);

      expect(result.hidden, isFalse);
    },
  );

  test('push token deletion encodes the token in the route', () async {
    final service = ShopApiService(
      baseUrl: 'http://example.test/api',
      token: 'token-1',
      client: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(
          request.url.toString(),
          'http://example.test/api/messages/push-devices/token%2Fwith%20space',
        );
        return http.Response(jsonEncode({'code': 0, 'data': null}), 200);
      }),
    );

    await service.unregisterPushDevice('token/with space');
  });
}
