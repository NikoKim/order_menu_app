import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/widgets/app_notice.dart';

void main() {
  testWidgets('central notice replaces the previous notice', (tester) async {
    late BuildContext pageContext;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            pageContext = context;
            return const Scaffold();
          },
        ),
      ),
    );

    AppNotice.show(pageContext, '第一条提示');
    await tester.pump();
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(find.text('第一条提示'), findsOneWidget);

    AppNotice.show(pageContext, '第二条提示');
    await tester.pump();
    expect(find.text('第一条提示'), findsNothing);
    expect(find.text('第二条提示'), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
  });
}
