import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/pages/chat_list_page.dart';

void main() {
  testWidgets('shop chat list does not expose a start conversation command', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: ChatListPage()));

    expect(find.byTooltip('发起私聊'), findsNothing);
  });

  testWidgets('shop chat list uses the home-style light gray background', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: ChatListPage()));

    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold));
    expect(scaffold.backgroundColor, const Color(0xFFF5F6F8));
  });

  testWidgets('revealed pin action receives the tap', (tester) async {
    var pinTaps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            height: 88,
            child: ConversationSwipeTile(
              pinned: false,
              canDelete: true,
              onPin: () => pinTaps++,
              onDelete: () {},
              child: const ColoredBox(
                color: Colors.white,
                child: Center(child: Text('会话')),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.drag(find.text('会话'), const Offset(-180, 0));
    await tester.pumpAndSettle();
    await tester.tap(find.text('置顶'));

    expect(pinTaps, 1);
  });

  testWidgets('revealed delete action receives the tap', (tester) async {
    var deleteTaps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            height: 88,
            child: ConversationSwipeTile(
              pinned: false,
              canDelete: true,
              onPin: () {},
              onDelete: () => deleteTaps++,
              child: const ColoredBox(
                color: Colors.white,
                child: Center(child: Text('会话')),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.drag(find.text('会话'), const Offset(-180, 0));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));

    expect(deleteTaps, 1);
  });

  testWidgets('tapping outside an opened conversation closes its actions', (
    tester,
  ) async {
    final controller = ConversationSwipeController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              SizedBox(
                height: 88,
                child: ConversationSwipeTile(
                  itemId: 1,
                  controller: controller,
                  pinned: false,
                  canDelete: true,
                  onPin: () {},
                  onDelete: () {},
                  child: const ColoredBox(
                    color: Colors.white,
                    child: Center(child: Text('会话一')),
                  ),
                ),
              ),
              const SizedBox(height: 88, child: Center(child: Text('列表空白'))),
            ],
          ),
        ),
      ),
    );

    await tester.drag(find.text('会话一'), const Offset(-180, 0));
    await tester.pumpAndSettle();
    expect(controller.value, 1);

    await tester.tap(find.text('列表空白'));
    await tester.pumpAndSettle();
    expect(controller.value, isNull);
  });

  testWidgets('opening another conversation closes the previous one', (
    tester,
  ) async {
    final controller = ConversationSwipeController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              for (final id in [1, 2])
                SizedBox(
                  height: 88,
                  child: ConversationSwipeTile(
                    itemId: id,
                    controller: controller,
                    pinned: false,
                    canDelete: true,
                    onPin: () {},
                    onDelete: () {},
                    child: ColoredBox(
                      color: Colors.white,
                      child: Center(child: Text('会话$id')),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );

    await tester.drag(find.text('会话1'), const Offset(-180, 0));
    await tester.pumpAndSettle();
    expect(controller.value, 1);

    await tester.drag(find.text('会话2'), const Offset(-180, 0));
    await tester.pumpAndSettle();
    expect(controller.value, 2);
    expect(
      tester
          .widget<AnimatedContainer>(find.byKey(const ValueKey('swipe-card-1')))
          .transform
          ?.storage[12],
      0,
    );
  });
}
