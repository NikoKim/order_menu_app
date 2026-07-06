import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shop_frontend/main.dart';

void main() {
  testWidgets('shop app starts at login page when signed out', (tester) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const ShopFrontendApp());
    await tester.pump();

    expect(find.text('商家端登录'), findsOneWidget);
    expect(find.text('工作台'), findsNothing);
  });
}
