import 'dart:async';

import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _HangingClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) =>
      Completer<http.StreamedResponse>().future;
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
}
