import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/pages/chat_detail_page.dart';
import 'package:shop_frontend/models/shop_api_models.dart';
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

  test('off-screen quote targets are distinguished from visible targets', () {
    final positions = [
      const ItemPosition(index: 0, itemLeadingEdge: .7, itemTrailingEdge: 1),
      const ItemPosition(index: 1, itemLeadingEdge: .2, itemTrailingEdge: .7),
    ];

    expect(chatVisualIndexIsVisible(1, positions), isTrue);
    expect(chatVisualIndexIsVisible(8, positions), isFalse);
  });

  test(
    'chat images only omit bubbles when they contain a live image alone',
    () {
      ChatMessage image({String content = '', bool expired = false}) =>
          ChatMessage.fromJson({
            'id': 1,
            'conversationId': 2,
            'senderUserId': 3,
            'type': 'IMAGE',
            'content': content,
            'imageUrl': expired ? null : '/messages/2/image.jpg',
            'imageExpired': expired,
            'createdAt': '2026-07-15T10:00:00',
          });

      expect(chatImageUsesBubble(image()), isFalse);
      expect(chatImageUsesBubble(image(content: '一起看看')), isTrue);
      expect(chatImageUsesBubble(image(expired: true)), isTrue);
    },
  );

  test('merchant emoji choices are rich and cover common categories', () {
    expect(chatEmojiChoices.length, greaterThanOrEqualTo(96));
    expect(chatEmojiChoices, containsAll(['😀', '👍', '❤️', '🍜', '🎉', '🚀']));
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

  testWidgets('many long messages return to the composer-side bottom', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 420));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = ItemScrollController();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              SizedBox(
                height: 300,
                child: ScrollablePositionedList.builder(
                  itemScrollController: controller,
                  reverse: true,
                  itemCount: 80,
                  itemBuilder: (_, index) => SizedBox(
                    height: index == 0 ? 160 : 72,
                    child: Text(index == 0 ? '最新长消息' : '消息$index'),
                  ),
                ),
              ),
              const SizedBox(height: 120, child: Text('输入区')),
            ],
          ),
        ),
      ),
    );
    await tester.pump();
    controller.jumpTo(index: 70);
    await tester.pump();
    controller.jumpTo(index: 0, alignment: 0);
    await tester.pumpAndSettle();

    expect(find.text('最新长消息'), findsOneWidget);
    expect(tester.getBottomLeft(find.text('最新长消息')).dy, lessThanOrEqualTo(300));
    expect(tester.getBottomLeft(find.text('最新长消息')).dy, greaterThan(250));
  });
}
