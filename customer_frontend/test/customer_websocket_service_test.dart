import 'package:customer_frontend/services/customer_websocket_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses a websocket message with a numeric timestamp', () {
    final message = CustomerWsMessage.fromJson({
      'module': 'ORDER',
      'action': 'ACCEPTED',
      'data': {'orderId': 7},
      'timestamp': 123,
    });

    expect(message.module, 'ORDER');
    expect(message.action, 'ACCEPTED');
    expect(message.data['orderId'], 7);
    expect(message.timestamp, 123);
  });

  test('uses unknown defaults and an empty data map for malformed fields', () {
    final message = CustomerWsMessage.fromJson({
      'data': 'not an object',
      'timestamp': 'invalid',
    });

    expect(message.module, 'UNKNOWN');
    expect(message.action, 'UNKNOWN');
    expect(message.data, isEmpty);
    expect(message.timestamp, isNull);
  });
}
