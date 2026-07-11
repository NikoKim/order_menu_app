import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/pages/chat_list_page.dart';

void main() {
  testWidgets('chat list exposes the start conversation command', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: ChatListPage()));

    expect(find.byTooltip('发起私聊'), findsOneWidget);
  });
}
