import 'dart:convert';

import 'package:customer_frontend/main.dart';
import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _DishShareDeniedClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    expect(request.method, 'POST');
    expect(request.url.path, '/api/messages/dish-shares');
    return http.StreamedResponse(
      Stream.value(utf8.encode('{"code":403,"message":"无法分享到该聊天"}')),
      403,
      headers: {'content-type': 'application/json'},
    );
  }
}

void main() {
  test('anonymous users do not receive a chat destination', () {
    expect(customerNavigationLabels(loggedIn: false), isNot(contains('聊天')));
  });

  test('logged-in users receive the chat destination in the middle', () {
    expect(customerNavigationLabels(loggedIn: true), equals(['首页', '聊天', '我的']));
  });

  test('family dish sharing exposes an ineligible-target response', () async {
    final api = CustomerApi(ApiClient(httpClient: _DishShareDeniedClient(), baseUrl: 'https://example.test/api'));
    await expectLater(
      api.shareDish(conversationId: 7, dishId: 42),
      throwsA(isA<ApiException>().having((error) => error.statusCode, 'statusCode', 403)),
    );
  });
}
