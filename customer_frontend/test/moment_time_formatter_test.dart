import 'package:customer_frontend/utils/moment_time_formatter.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime.utc(2026, 7, 16, 4);

  test('formats relative time at minute and hour boundaries', () {
    expect(formatMomentTime('2026-07-16T11:59:59', now: now), '刚刚');
    expect(formatMomentTime('2026-07-16T11:59:00', now: now), '1分钟前');
    expect(formatMomentTime('2026-07-16T11:01:00', now: now), '59分钟前');
    expect(formatMomentTime('2026-07-16T11:00:00', now: now), '1小时前');
    expect(formatMomentTime('2026-07-15T12:01:00', now: now), '23小时前');
    expect(formatMomentTime('2026-07-15T12:00:00', now: now), '昨天 12:00');
  });

  test('formats yesterday and absolute calendar dates', () {
    expect(formatMomentTime('2026-07-15T11:59:59', now: now), '昨天 11:59');
    expect(formatMomentTime('2026-07-15T08:05:00', now: now), '昨天 08:05');
    expect(formatMomentTime('2026-07-10T08:05:00', now: now), '07-10 08:05');
    expect(
      formatMomentTime('2025-12-31T23:59:00', now: now),
      '2025-12-31 23:59',
    );
  });

  test(
    'keeps invalid values and clamps small future clock skew to zero minutes',
    () {
      expect(formatMomentTime('not-a-date', now: now), 'not-a-date');
      expect(formatMomentTime('2026-07-16T12:02:00', now: now), '刚刚');
      expect(formatMomentTime('2026-07-16T03:00:00Z', now: now), '1小时前');
      expect(formatMomentTime('2026-07-16T11:00:00+08:00', now: now), '1小时前');
    },
  );

  test('parses China timestamps into exact polling windows', () {
    final after = DateTime.utc(2026, 7, 16, 4, 0, 0);
    final through = DateTime.utc(2026, 7, 16, 4, 0, 10);
    expect(
      momentPublishedInWindow(
        '2026-07-16T12:00:05',
        after: after,
        through: through,
      ),
      isTrue,
    );
    expect(
      momentPublishedInWindow(
        '2026-07-16T04:00:05Z',
        after: after,
        through: through,
      ),
      isTrue,
    );
    expect(
      momentPublishedInWindow(
        '2026-07-16T12:00:00+08:00',
        after: after,
        through: through,
      ),
      isFalse,
    );
  });

  test(
    'ignores legacy upload updates when deciding whether a moment was edited',
    () {
      expect(
        shouldShowMomentEdited(
          editedAt: '2026-07-31T19:45:01+08:00',
          publishedAt: '2026-07-31T19:44:52+08:00',
          createdAt: '2026-07-31T19:44:52+08:00',
          status: 'PUBLISHED',
        ),
        isFalse,
      );
      expect(
        shouldShowMomentEdited(
          editedAt: '2026-08-24T11:26:03+08:00',
          publishedAt: '2026-08-24T09:27:31+08:00',
          createdAt: '2026-08-24T09:27:31+08:00',
          status: 'PUBLISHED',
        ),
        isTrue,
      );
    },
  );
}
