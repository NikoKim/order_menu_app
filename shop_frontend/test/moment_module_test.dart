import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/models/moment_models.dart';
import 'package:shop_frontend/models/shop_api_models.dart';
import 'package:shop_frontend/pages/chat_list_page.dart';
import 'package:shop_frontend/pages/moment_pages.dart';
import 'package:shop_frontend/widgets/moment_image_viewer.dart';
import 'package:shop_frontend/widgets/moment_reaction_picker.dart';

void main() {
  test('shop new moment notification filters mine, seen and old posts', () {
    final after = DateTime.utc(2026, 7, 16, 4, 0, 0);
    final through = DateTime.utc(2026, 7, 16, 4, 0, 10);

    bool candidate({
      bool mine = false,
      bool viewed = false,
      bool known = false,
      String publishedAt = '2026-07-16T12:00:05+08:00',
    }) => shouldQueueMomentAsNew(
      mine: mine,
      viewed: viewed,
      known: known,
      publishedAt: publishedAt,
      after: after,
      through: through,
    );

    expect(candidate(), isTrue);
    expect(candidate(mine: true), isFalse);
    expect(candidate(viewed: true), isFalse);
    expect(candidate(known: true), isFalse);
    expect(candidate(publishedAt: '2026-07-16T11:59:59+08:00'), isFalse);
  });

  testWidgets('shop image preview opens without a new scaffold', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: MomentImagePreview(
              key: const ValueKey('shop-image-preview'),
              images: const ['/moment.jpg'],
              index: 0,
              resolveUrl: (url) => 'https://example.test$url',
              child: const SizedBox.square(dimension: 120),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('shop-image-preview')));
    await tester.pump(const Duration(milliseconds: 180));
    expect(find.byKey(const ValueKey('moment-image-viewer')), findsOneWidget);
    expect(find.byKey(const ValueKey('moment-image-zoom')), findsOneWidget);
    expect(find.byType(AppBar), findsNothing);

    await tester.tapAt(const Offset(8, 8));
    await tester.pump(const Duration(milliseconds: 180));
    expect(find.byKey(const ValueKey('moment-image-viewer')), findsNothing);
  });

  test('shop hashtag search resolves only an exact topic name', () {
    const topics = [
      MomentTopic(id: 1, name: '美食探店'),
      MomentTopic(id: 2, name: '美食'),
    ];

    expect(findExactShopMomentTopic(topics, '#美食')?.id, 2);
    expect(findExactShopMomentTopic(topics, '#不存在'), isNull);
  });

  testWidgets('chat and moment switching preserves the related tab state', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: ChatListPage()));
    await tester.pump();

    await tester.tap(find.text('动态').first);
    await tester.pump();
    final paneFinder = find.byKey(const ValueKey('shop-moment-pane'));
    final paneElement = tester.element(paneFinder);
    await tester.tap(find.text('相关'));
    await tester.pump();
    expect(
      tester
          .widget<TabBar>(
            find.descendant(of: paneFinder, matching: find.byType(TabBar)),
          )
          .controller
          ?.index,
      1,
    );

    await tester.tap(find.text('聊天').first);
    await tester.pump();
    await tester.tap(find.text('动态').first);
    await tester.pump();

    expect(tester.element(paneFinder), same(paneElement));
    expect(
      tester
          .widget<TabBar>(
            find.descendant(of: paneFinder, matching: find.byType(TabBar)),
          )
          .controller
          ?.index,
      1,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('reaction picker shows every configured emoji and count', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => showMomentReactionPicker(
              context,
              counts: const {'😀': 3},
              selected: const {'😀'},
            ),
            child: const Text('打开回应'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('打开回应'));
    await tester.pumpAndSettle();

    expect(momentReactionEmojis.length, 38);
    expect(find.text('😀'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    final selectedMaterial = tester.widget<Material>(
      find.ancestor(of: find.text('😀'), matching: find.byType(Material)).first,
    );
    expect(selectedMaterial.color, const Color(0xFFFFE2B8));
    await tester.scrollUntilVisible(
      find.text('⏰'),
      180,
      scrollable: find.descendant(
        of: find.byType(GridView),
        matching: find.byType(Scrollable),
      ),
    );
    expect(find.text('⏰'), findsOneWidget);
    expect(find.text('添加回应'), findsNothing);
  });

  testWidgets('shop moment cards match the borderless customer surface', (
    tester,
  ) async {
    const moment = MomentItem(
      id: 9,
      authorId: 7,
      authorName: '商家',
      title: '无边框动态',
      content: '动态内容',
      createdAt: '2026-07-16T10:00:00',
    );
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: MomentCard(moment: moment)),
      ),
    );
    final body = tester.widget<Container>(
      find
          .byWidgetPredicate(
            (widget) =>
                widget is Container &&
                widget.padding == const EdgeInsets.fromLTRB(16, 15, 16, 8),
          )
          .first,
    );
    final decoration = body.decoration! as BoxDecoration;
    expect(decoration.border, isNull);
    expect(decoration.boxShadow, isNull);
    final surface = tester.widget<Material>(
      find
          .ancestor(of: find.text('无边框动态'), matching: find.byType(Material))
          .first,
    );
    expect(surface.color, Colors.white);
  });

  testWidgets('shop moment actions use the customer action palette', (
    tester,
  ) async {
    const moment = MomentItem(
      id: 91,
      authorId: 7,
      authorName: '商家',
      title: '动态标题',
      content: '动态内容',
      createdAt: '2026-07-16T10:00:00',
      liked: true,
      likeCount: 3,
      commentCount: 2,
    );
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: MomentCard(moment: moment)),
      ),
    );

    expect(
      tester.widget<Icon>(find.byIcon(Icons.favorite_rounded)).color,
      const Color(0xFFE64646),
    );
    for (final icon in [
      Icons.chat_bubble_outline_rounded,
      Icons.add_reaction_outlined,
      Icons.ios_share_rounded,
    ]) {
      expect(
        tester.widget<Icon>(find.byIcon(icon)).color,
        const Color(0xFF646A70),
      );
    }
    expect(find.byType(TextButton), findsNothing);
  });

  testWidgets('detail comment action invokes the composer focus callback', (
    tester,
  ) async {
    var tapped = false;
    const moment = MomentItem(
      id: 92,
      authorId: 7,
      authorName: '商家',
      title: '动态标题',
      content: '动态内容',
      createdAt: '2026-07-16T10:00:00',
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MomentCard(
            moment: moment,
            full: true,
            onCommentTap: () => tapped = true,
          ),
        ),
      ),
    );

    await tester.tap(find.byIcon(Icons.chat_bubble_outline_rounded));
    expect(tapped, isTrue);
  });

  testWidgets('hot comment is rendered below all four moment actions', (
    tester,
  ) async {
    const moment = MomentItem(
      id: 10,
      authorId: 7,
      authorName: '商家',
      title: '动态标题',
      content: '动态内容',
      createdAt: '2026-07-16T10:00:00',
      commentCount: 1,
      hotComment: MomentComment(
        id: 11,
        userId: 8,
        nickname: '顾客',
        content: '热门评论内容',
        createdAt: '2026-07-16T10:01:00',
        merchant: true,
        author: true,
        likeCount: 9,
        replies: [
          MomentComment(
            id: 12,
            userId: 9,
            nickname: '回复用户',
            content: '第一条回复',
            createdAt: '2026-07-16T10:02:00',
          ),
          MomentComment(
            id: 13,
            userId: 10,
            nickname: '其他用户',
            content: '第二条回复',
            createdAt: '2026-07-16T10:03:00',
          ),
        ],
      ),
    );
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: MomentCard(moment: moment)),
      ),
    );

    expect(find.text('热门评论'), findsOneWidget);
    expect(find.byIcon(Icons.local_fire_department_rounded), findsOneWidget);
    expect(find.text('商家'), findsWidgets);
    expect(find.text('作者'), findsOneWidget);
    expect(find.text('第一条回复'), findsOneWidget);
    expect(find.text('第二条回复'), findsNothing);
    final accent = tester.widget<Ink>(
      find
          .ancestor(
            of: find.byIcon(Icons.local_fire_department_rounded),
            matching: find.byType(Ink),
          )
          .first,
    );
    final accentDecoration = accent.decoration! as BoxDecoration;
    expect(
      (accentDecoration.border! as Border).left,
      const BorderSide(color: Color(0xFFF2A04B), width: 2),
    );
    expect(
      tester.getTopLeft(find.text('热门评论')).dy,
      greaterThan(
        tester.getBottomLeft(find.byIcon(Icons.ios_share_rounded)).dy,
      ),
    );
  });

  group('moment chat shares', () {
    ChatMessage message(String type) => ChatMessage(
      id: 1,
      conversationId: 2,
      senderUserId: 3,
      type: type,
      content: '',
      createdAt: '2026-07-15T10:00:00',
    );

    test('accepts moment and moment-comment message types', () {
      expect(message('MOMENT_SHARE').isMomentShare, isTrue);
      expect(message('MOMENT_COMMENT_SHARE').isMomentShare, isTrue);
      expect(message('TEXT').isMomentShare, isFalse);
    });

    test('keeps complete moment data in quoted snapshots', () {
      final parsed = ChatMessage.fromJson({
        'id': 8,
        'conversationId': 2,
        'senderUserId': 3,
        'type': 'TEXT',
        'content': 'reply',
        'createdAt': '2026-07-15T10:00:00',
        'quotedMessage': {
          'id': 7,
          'type': 'MOMENT_COMMENT_SHARE',
          'content': 'share',
          'sharedMomentId': 42,
          'sharedMomentCommentId': 99,
          'sharedMomentTitle': '今天的新品',
          'sharedMomentPreview': '完整动态摘要',
          'sharedCommentPreview': '这条评论值得分享',
          'sharedMomentAuthorName': '小店',
          'sharedMomentAuthorAvatar': '/avatar.png',
          'sharedCardIcon': 'COMMENT',
        },
      });

      expect(parsed.quotedMessage?.isMomentShare, isTrue);
      expect(parsed.quotedMessage?.sharedMomentId, 42);
      expect(parsed.quotedMessage?.sharedMomentCommentId, 99);
      expect(parsed.quotedMessage?.sharedMomentPreview, '完整动态摘要');
      expect(parsed.quotedMessage?.sharedCommentPreview, '这条评论值得分享');
      expect(parsed.quotedMessage?.sharedCardIcon, 'COMMENT');
    });
  });

  test(
    'moment parsing uses published time and hides first-publish edit time',
    () {
      final firstPublish = MomentItem.fromJson({
        'id': 1,
        'authorId': 2,
        'title': '标题内容',
        'content': '正文',
        'createdAt': '2026-07-01T08:00:00',
        'publishedAt': '2026-07-15T10:00:00',
        'editedAt': '2026-07-15T10:00:01',
      });
      final edited = MomentItem.fromJson({
        'id': 1,
        'authorId': 2,
        'title': '标题内容',
        'content': '正文',
        'publishedAt': '2026-07-15T10:00:00',
        'editedAt': '2026-07-15T11:00:00',
      });

      expect(firstPublish.createdAt, '2026-07-15T10:00:00');
      expect(firstPublish.editedAt, isNull);
      expect(edited.editedAt, '2026-07-15T11:00:00');
    },
  );

  test('topic and rich reference counts match the final DTO', () {
    final topic = MomentTopic.fromJson({
      'id': 3,
      'name': '今天吃什么',
      'userCount': 12,
      'momentCount': 25,
    });
    final moment = MomentItem.fromJson({
      'id': 1,
      'authorId': 2,
      'title': '标题内容',
      'content': '正文',
      'reference': {
        'shopId': 7,
        'shopName': '小店',
        'shopImageUrl': '/shop.png',
        'dishId': 8,
        'dishName': '招牌菜',
        'dishPrice': 28.5,
        'dishDescription': '每日现做',
        'dishSalesCount': 66,
      },
      'hotComment': {
        'id': 31,
        'author': {'id': 9, 'nickname': '热门用户', 'role': 'SHOP'},
        'content': '这条来自动态接口',
        'createdAt': '2026-07-15T12:00:00',
        'likeCount': 8,
        'replies': [
          {
            'id': 32,
            'author': {'id': 10, 'nickname': '回复用户'},
            'content': '热门回复',
            'createdAt': '2026-07-15T12:05:00',
          },
        ],
      },
    });

    expect(topic.userCount, 12);
    expect(topic.momentCount, 25);
    expect(moment.shopImageUrl, '/shop.png');
    expect(moment.dishPrice, 28.5);
    expect(moment.dishDescription, '每日现做');
    expect(moment.dishSalesCount, 66);
    expect(moment.hotComment?.id, 31);
    expect(moment.hotComment?.merchant, isTrue);
    expect(moment.hotComment?.replies.single.id, 32);
  });

  test('finds a highlighted root or preview reply container', () {
    final reply = _comment(12, parentId: 10);
    final roots = [
      _comment(10, replies: [reply]),
      _comment(20),
    ];

    expect(findMomentCommentContainerId(roots, 10), 10);
    expect(findMomentCommentContainerId(roots, 12), 10);
    expect(findMomentCommentContainerId(roots, 99), isNull);
  });

  test('viewport detection requires horizontal and vertical intersection', () {
    const viewport = Size(400, 800);
    expect(
      momentRectIntersectsViewport(
        const Rect.fromLTWH(10, 100, 200, 200),
        viewport,
        topInset: 56,
      ),
      isTrue,
    );
    expect(
      momentRectIntersectsViewport(
        const Rect.fromLTWH(410, 100, 100, 100),
        viewport,
      ),
      isFalse,
    );
    expect(
      momentRectIntersectsViewport(
        const Rect.fromLTWH(10, 810, 100, 100),
        viewport,
      ),
      isFalse,
    );
  });

  test('selector follows scroll delta and ignores short lists', () {
    expect(
      nextMomentHeaderCollapseExtent(
        current: 0,
        scrollDelta: 20,
        maxScrollExtent: 800,
      ),
      20,
    );
    expect(
      nextMomentHeaderCollapseExtent(
        current: 20,
        scrollDelta: 80,
        maxScrollExtent: 800,
      ),
      60,
    );
    expect(
      nextMomentHeaderCollapseExtent(
        current: 60,
        scrollDelta: -17,
        maxScrollExtent: 800,
      ),
      43,
    );
    expect(
      nextMomentHeaderCollapseExtent(
        current: 43,
        scrollDelta: -80,
        maxScrollExtent: 800,
      ),
      0,
    );
    expect(
      nextMomentHeaderCollapseExtent(
        current: 40,
        scrollDelta: 10,
        maxScrollExtent: 299,
      ),
      0,
      reason: 'a short list must keep the selector fully expanded',
    );
    expect(
      nextMomentHeaderCollapseExtent(
        current: 0,
        scrollDelta: 12,
        maxScrollExtent: 300,
      ),
      12,
      reason: 'the 300px boundary starts the continuous collapse',
    );
  });

  test('hot comments use likes then newest publish time', () {
    final ranked = rankMomentComments([
      _comment(1, likeCount: 2, createdAt: '2026-07-15T10:00:00'),
      _comment(2, likeCount: 4, createdAt: '2026-07-15T09:00:00'),
      _comment(3, likeCount: 4, createdAt: '2026-07-15T11:00:00'),
    ]);

    expect(ranked.map((item) => item.id), [3, 2, 1]);
  });

  test('edit time is shown only after a published post was really edited', () {
    MomentItem item({required String status, required String? editedAt}) =>
        MomentItem(
          id: 1,
          authorId: 2,
          authorName: '商家',
          title: '标题',
          content: '内容',
          createdAt: '2026-07-16T10:00:00',
          editedAt: editedAt,
          status: status,
        );

    expect(
      shouldShowMomentEdited(
        item(status: 'PUBLISHED', editedAt: '2026-07-16T10:00:03'),
      ),
      isTrue,
    );
    expect(
      shouldShowMomentEdited(
        item(status: 'PUBLISHED', editedAt: '2026-07-16T10:00:01'),
      ),
      isFalse,
    );
    expect(
      shouldShowMomentEdited(
        item(status: 'DRAFT', editedAt: '2026-07-16T11:00:00'),
      ),
      isFalse,
    );
  });

  testWidgets('reply actions wrap without overflowing a narrow screen', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(240, 220));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              const Row(
                children: [
                  Text('店主'),
                  MomentRoleBadge(text: '商家'),
                  MomentRoleBadge(text: '作者'),
                ],
              ),
              MomentReplyActionBar(
                createdAt: '2026-07-15T10:00:00',
                liked: true,
                likeCount: 999,
                canDelete: true,
                onReply: () {},
                onLike: () {},
                onShare: () {},
                onDelete: () {},
              ),
            ],
          ),
        ),
      ),
    );

    expect(find.text('商家'), findsOneWidget);
    expect(find.text('作者'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

MomentComment _comment(
  int id, {
  int? parentId,
  List<MomentComment> replies = const [],
  int likeCount = 0,
  String createdAt = '2026-07-15T10:00:00',
}) => MomentComment(
  id: id,
  userId: id,
  nickname: 'user$id',
  content: 'comment$id',
  createdAt: createdAt,
  parentId: parentId,
  replies: replies,
  likeCount: likeCount,
);
