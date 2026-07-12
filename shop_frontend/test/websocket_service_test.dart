import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/services/websocket_service.dart';

void main() {
  test('exposes connection changes for reconciliation after reconnect', () {
    expect(ShopWebSocketService.instance.connectionStream, isA<Stream<bool>>());
  });
}
