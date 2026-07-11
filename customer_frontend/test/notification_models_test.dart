import 'package:customer_frontend/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses notifications and treats a missing read time as unread', () {
    final notification = UserNotification.fromJson({
      'id': 12,
      'userId': 3,
      'type': 'ORDER_STATUS',
      'title': 'Order updated',
      'content': 'Your order is ready.',
      'payload': '{"orderId":9}',
      'createdAt': '2026-07-11T08:00:00',
    });

    expect(notification.id, 12);
    expect(notification.isUnread, isTrue);
    expect(notification.title, 'Order updated');
  });
}
