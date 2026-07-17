import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/shop_api_models.dart';
import 'package:shop_frontend/pages/chat_detail_page.dart';
import 'package:shop_frontend/pages/chat_user_detail_page.dart';
import 'package:shop_frontend/widgets/skeleton_box.dart';

void main() {
  testWidgets('chat detail renders message skeletons on its first frame', (
    tester,
  ) async {
    const conversation = ChatConversation(
      id: 991001,
      type: 'DIRECT',
      status: 'ACTIVE',
      directUserId: 8,
      directUserNickname: '顾客',
    );

    await tester.pumpWidget(
      const MaterialApp(home: ChatDetailPage(conversation: conversation)),
    );

    expect(find.byType(SkeletonBox), findsWidgets);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('chat user profile renders a complete profile skeleton first', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ChatUserDetailPage(userId: 991002, nickname: '顾客'),
      ),
    );

    expect(find.byType(SkeletonBox), findsWidgets);
    expect(find.text('加载中'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
