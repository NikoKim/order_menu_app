import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:customer_frontend/pages/chat_detail_page.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

void main() {
  test('latest-message tracking scrolls for new ids but not deletions', () {
    final coordinator = ChatScrollCoordinator();

    expect(coordinator.observeLatestMessage(10), isTrue);
    expect(coordinator.observeLatestMessage(10), isFalse);
    expect(coordinator.observeLatestMessage(9), isFalse);
    expect(coordinator.observeLatestMessage(11), isTrue);
  });

  test('equal-size replacement with a newer id still requests bottom', () {
    final coordinator = ChatScrollCoordinator();

    coordinator.observeLatestMessage(20);

    expect(coordinator.observeLatestMessage(21), isTrue);
  });

  test('quote navigation defers bottom until highlighting ends', () {
    final coordinator = ChatScrollCoordinator();
    coordinator.beginQuoteNavigation();

    expect(coordinator.requestBottom(), isFalse);
    expect(coordinator.endQuoteNavigation(), isTrue);
    expect(coordinator.endQuoteNavigation(), isFalse);
  });

  testWidgets('keyboard inset corrections are debounced to one callback', (
    tester,
  ) async {
    var corrections = 0;
    final settler = ChatKeyboardInsetSettler(
      onSettled: () => corrections++,
      delay: const Duration(milliseconds: 80),
    );
    addTearDown(settler.dispose);

    settler.schedule();
    await tester.pump(const Duration(milliseconds: 40));
    settler.schedule();
    await tester.pump(const Duration(milliseconds: 79));
    expect(corrections, 0);
    await tester.pump(const Duration(milliseconds: 1));
    expect(corrections, 1);
  });

  test(
    'reverse chat list maps newest chronological message to visual zero',
    () {
      expect(chatVisualIndexFromChronologicalIndex(0, 5), 4);
      expect(chatVisualIndexFromChronologicalIndex(4, 5), 0);
    },
  );

  test('quote target maps back to its reverse-list visual index', () {
    expect(chatVisualIndexFromChronologicalIndex(2, 5), 2);
    expect(chatVisualIndexFromChronologicalIndex(1, 5), 3);
  });

  testWidgets('reverse list anchors visual zero at the input-side bottom', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 320));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ScrollablePositionedList.builder(
            reverse: true,
            itemCount: 3,
            itemBuilder: (_, visualIndex) => SizedBox(
              height: 48,
              child: Text(['newest', 'middle', 'oldest'][visualIndex]),
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(
      tester.getCenter(find.text('newest')).dy,
      greaterThan(tester.getCenter(find.text('oldest')).dy),
    );
    expect(tester.getBottomLeft(find.text('newest')).dy, greaterThan(270));
  });
}
