import 'package:admin_frontend/main.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('admin app starts at login page when signed out', (tester) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const AdminApp());
    await tester.pump();

    expect(find.text('管理员登录'), findsOneWidget);
    expect(find.text('登录'), findsOneWidget);
  });
}
