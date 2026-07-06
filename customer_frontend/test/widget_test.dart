import 'package:customer_frontend/main.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('customer app shows home and login menus when signed out', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const OrderMenuCustomerApp());
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump();

    expect(find.text('首页'), findsOneWidget);
    expect(find.text('登录'), findsWidgets);
    expect(find.text('我的'), findsNothing);
  });
}
