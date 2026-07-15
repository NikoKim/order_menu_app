import 'package:customer_frontend/widgets/chat_action_overlay.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('long press shows a horizontal action menu above the target', (
    tester,
  ) async {
    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: ChatActionOverlay(
              actions: const [
                ChatAction('quote', '引用', Icons.format_quote),
                ChatAction('delete', '删除', Icons.delete_outline),
              ],
              onSelected: (value) => selected = value,
              child: const SizedBox(width: 120, height: 48, child: Text('气泡')),
            ),
          ),
        ),
      ),
    );

    await tester.longPress(find.text('气泡'));
    await tester.pumpAndSettle();

    expect(find.text('引用'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
    final quote = tester.getCenter(find.text('引用'));
    final delete = tester.getCenter(find.text('删除'));
    expect((quote.dy - delete.dy).abs(), lessThan(2));

    await tester.tap(find.text('引用'));
    await tester.pump();
    expect(selected, 'quote');
    expect(find.text('删除'), findsNothing);
  });

  testWidgets('tapping outside dismisses the menu', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: ChatActionOverlay(
              actions: const [ChatAction('delete', '删除', Icons.delete_outline)],
              onSelected: (_) {},
              child: const SizedBox(width: 120, height: 48, child: Text('气泡')),
            ),
          ),
        ),
      ),
    );

    await tester.longPress(find.text('气泡'));
    await tester.pumpAndSettle();
    await tester.tapAt(const Offset(10, 10));
    await tester.pump();

    expect(find.text('删除'), findsNothing);
  });
}
