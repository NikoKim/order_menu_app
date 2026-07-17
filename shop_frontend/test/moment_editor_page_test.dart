import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/moment_models.dart';
import 'package:shop_frontend/pages/moment_editor_page.dart';

MomentItem _editableMoment() => const MomentItem(
  id: 18,
  authorId: 7,
  authorName: '测试商家',
  title: '已经保存的标题',
  content: '已经保存的动态正文',
  createdAt: '2026-07-16T10:00:00',
  visibility: 'PRIVATE',
  topics: [MomentTopic(id: 3, name: '今日推荐', momentCount: 12)],
  dishId: 9,
  dishName: '招牌炒饭',
  dishPrice: 28,
  dishDescription: '每日现炒',
  dishSalesCount: 86,
  shopId: 2,
);

void main() {
  Future<void> pumpEditor(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        home: MomentEditorPage(initial: _editableMoment(), loadTopics: false),
      ),
    );
    await tester.pump();
  }

  testWidgets('restores content, visibility, topic and linked dish', (
    tester,
  ) async {
    await pumpEditor(tester);

    expect(find.text('已经保存的标题'), findsOneWidget);
    expect(find.text('已经保存的动态正文'), findsOneWidget);
    expect(find.text('#今日推荐'), findsOneWidget);
    expect(find.text('招牌炒饭'), findsOneWidget);
    expect(find.text('¥28.00 · 点击更换'), findsOneWidget);

    final privateOption = tester.widget<Material>(
      find.ancestor(of: find.text('私密'), matching: find.byType(Material)).first,
    );
    expect(privateOption.color, const Color(0xFFFFF0E2));
  });

  testWidgets('supports changing visibility and clearing restored settings', (
    tester,
  ) async {
    await pumpEditor(tester);

    await tester.tap(find.text('相关'));
    await tester.pump();
    final relatedOption = tester.widget<Material>(
      find.ancestor(of: find.text('相关'), matching: find.byType(Material)).first,
    );
    expect(relatedOption.color, const Color(0xFFFFF0E2));

    final topicChip = tester.widget<InputChip>(find.byType(InputChip));
    topicChip.onDeleted!();
    await tester.pump();
    expect(find.text('#今日推荐'), findsNothing);

    await tester.tap(find.byTooltip('清除关联菜品'));
    await tester.pump();
    expect(find.text('招牌炒饭'), findsNothing);
    expect(find.text('选择一个店内菜品'), findsOneWidget);
  });

  testWidgets('uses the customer composer sections and 92px image tile', (
    tester,
  ) async {
    await pumpEditor(tester);

    expect(find.text('动态内容'), findsOneWidget);
    expect(find.text('谁可以看到'), findsOneWidget);
    expect(find.text('话题'), findsOneWidget);
    expect(find.text('添加菜品'), findsOneWidget);
    expect(find.text('图片'), findsOneWidget);

    final sectionTitles = ['动态内容', '图片', '添加菜品', '话题', '谁可以看到'];
    final positions = sectionTitles
        .map((title) => tester.getTopLeft(find.text(title)).dy)
        .toList();
    expect(positions, orderedEquals([...positions]..sort()));

    final addImageInk = find.ancestor(
      of: find.text('添加图片'),
      matching: find.byType(Ink),
    );
    expect(tester.getSize(addImageInk), const Size(92, 92));
  });
}
