import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/services/pending_order_request_store.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('reuses a matching pending request across store instances', () async {
    var sequence = 0;
    final now = DateTime.utc(2026, 7, 22, 12);
    PendingOrderRequestStore store() => PendingOrderRequestStore(
      clock: () => now,
      requestIdFactory: () => 'request-${++sequence}',
    );

    final first = await store().resolve('same-payload');
    final restored = await store().resolve('same-payload');
    final changed = await store().resolve('changed-payload');

    expect(first, 'request-1');
    expect(restored, first);
    expect(changed, 'request-2');
  });

  test('expires old requests and clears only the completed request', () async {
    var sequence = 0;
    var now = DateTime.utc(2026, 7, 22, 12);
    final store = PendingOrderRequestStore(
      clock: () => now,
      requestIdFactory: () => 'request-${++sequence}',
    );

    final first = await store.resolve('payload');
    now = now.add(const Duration(minutes: 31));
    final expired = await store.resolve('payload');
    await store.clear(first);
    expect(await store.resolve('payload'), expired);

    await store.clear(expired);
    expect(await store.resolve('payload'), 'request-3');
  });

  test(
    'payload fingerprint is canonical and includes mutable order fields',
    () {
      const dish = Dish(id: 7, shopId: 3, name: 'dish', price: 12);
      const first = CartItem(
        dish: dish,
        quantity: 2,
        selectedOptionIds: [9, 4],
      );
      const reorderedOptions = CartItem(
        dish: dish,
        quantity: 2,
        selectedOptionIds: [4, 9],
      );

      final original = PendingOrderRequestStore.fingerprint(
        shopId: 3,
        items: const [first],
        remark: 'less salt',
      );
      final same = PendingOrderRequestStore.fingerprint(
        shopId: 3,
        items: const [reorderedOptions],
        remark: 'less salt',
      );
      final changed = PendingOrderRequestStore.fingerprint(
        shopId: 3,
        items: const [first],
        remark: 'no salt',
      );

      expect(same, original);
      expect(changed, isNot(original));
    },
  );
}
