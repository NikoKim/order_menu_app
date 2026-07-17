import 'dart:async';
import 'dart:convert';

import 'package:customer_frontend/main.dart';
import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/moment_models.dart';
import 'package:customer_frontend/pages/chat_list_page.dart';
import 'package:customer_frontend/pages/moment_detail_page.dart';
import 'package:customer_frontend/pages/moment_feed_page.dart';
import 'package:customer_frontend/pages/moment_privacy_page.dart';
import 'package:customer_frontend/services/api_client.dart';
import 'package:customer_frontend/widgets/moment_card.dart';
import 'package:customer_frontend/widgets/moment_image_viewer.dart';
import 'package:customer_frontend/widgets/ui_utils.dart';
import 'package:customer_frontend/widgets/moment_reaction_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _MomentFeedClient extends http.BaseClient {
  int deleteCount = 0;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    Object? data;
    if (request.method == 'DELETE') {
      deleteCount++;
    } else if (request.url.path.endsWith('/moments')) {
      data = {
        'records': [
          {
            'id': 41,
            'authorId': 7,
            'authorNickname': '我',
            'title': '待删除动态',
            'content': '内容',
            'visibility': 'PUBLIC',
            'createdAt': '2026-07-16T10:00:00',
            'mine': true,
          },
        ],
        'page': 1,
        'pageSize': 10,
        'total': 1,
      };
    }
    return _response(data);
  }
}

class _ProfileClient extends http.BaseClient {
  final response = Completer<http.StreamedResponse>();

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) =>
      response.future;
}

class _MomentSearchClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    if (request.url.path.endsWith('/moments/topics/suggestions')) {
      return _response([
        {'id': 1, 'name': '美食探店', 'userCount': 20, 'momentCount': 30},
        {'id': 2, 'name': '美食', 'userCount': 3, 'momentCount': 4},
      ]);
    }
    if (request.url.path.endsWith('/moments')) {
      return _response({
        'records': <Object>[],
        'page': 1,
        'pageSize': 10,
        'total': 0,
      });
    }
    return _response(null);
  }
}

class _MomentDetailClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final path = request.url.path;
    if (path.endsWith('/moments/5/comments')) {
      return _response({
        'records': [
          {
            'id': 6,
            'userId': 9,
            'nickname': '店主',
            'content': '这是一条清晰的根评论',
            'createdAt': '2026-07-16T10:01:00',
            'merchant': true,
            'authorBadge': true,
            'likeCount': 8,
            'replyCount': 1,
            'replies': [
              {
                'id': 7,
                'userId': 10,
                'nickname': '回复者',
                'content': '这是线程中的回复',
                'replyToNickname': '店主',
                'parentId': 6,
                'createdAt': '2026-07-16T10:02:00',
              },
            ],
          },
        ],
        'page': 1,
        'pageSize': 10,
        'total': 1,
      });
    }
    if (path.endsWith('/moments/5')) {
      return _response({
        'id': 5,
        'authorId': 9,
        'authorNickname': '店主',
        'title': '动态标题',
        'content': '动态正文',
        'visibility': 'PUBLIC',
        'createdAt': '2026-07-16T10:00:00',
        'commentCount': 2,
      });
    }
    return _response(null);
  }
}

class _CollapsibleMomentClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final path = request.url.path;
    if (path.endsWith('/messages/conversations')) return _response([]);
    if (path.endsWith('/moments/unread-count')) {
      return _response({'relatedUnreadCount': 0});
    }
    if (path.endsWith('/moments')) {
      return _response({
        'records': [
          for (var index = 0; index < 8; index++)
            {
              'id': 100 + index,
              'authorId': 7,
              'authorNickname': '用户$index',
              'title': '可滚动动态$index',
              'content': '用于验证连续折叠的动态内容，用于验证连续折叠的动态内容。',
              'visibility': 'PUBLIC',
              'createdAt': '2026-07-16T10:00:00',
            },
        ],
        'page': 1,
        'pageSize': 10,
        'total': 8,
      });
    }
    return _response(null);
  }
}

http.StreamedResponse _response(Object? data) => http.StreamedResponse(
  Stream.value(utf8.encode(jsonEncode({'code': 0, 'data': data}))),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

Widget _withServices(http.Client client, Widget child) => AppScope(
  services: AppServices(
    apiClient: ApiClient(
      httpClient: client,
      baseUrl: 'https://example.test/api',
    ),
  ),
  child: MaterialApp(home: child),
);

ChatMessage _message(String type) => ChatMessage(
  id: 1,
  conversationId: 2,
  senderUserId: 3,
  type: type,
  content: '',
  createdAt: '2026-07-15T12:00:00',
);

void main() {
  test('new moment notification only accepts unseen posts in this window', () {
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

  testWidgets('image preview is an in-place dismissible zoom overlay', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: MomentImagePreview(
              key: const ValueKey('customer-image-preview'),
              images: const ['/moment.jpg'],
              index: 0,
              resolveUrl: (url) => 'https://example.test$url',
              child: const SizedBox.square(dimension: 120),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('customer-image-preview')));
    await tester.pump(const Duration(milliseconds: 180));
    expect(find.byKey(const ValueKey('moment-image-viewer')), findsOneWidget);
    expect(find.byKey(const ValueKey('moment-image-zoom')), findsOneWidget);
    expect(find.byType(AppBar), findsNothing);

    final zoomGesture = tester.widget<GestureDetector>(
      find.byKey(const ValueKey('moment-image-zoom')),
    );
    expect(zoomGesture.onDoubleTap, isNotNull);
    expect(zoomGesture.onScaleStart, isNotNull);
    expect(zoomGesture.onScaleUpdate, isNotNull);

    await tester.tapAt(const Offset(8, 8));
    await tester.pump(const Duration(milliseconds: 180));
    expect(find.byKey(const ValueKey('moment-image-viewer')), findsNothing);
  });

  test('hashtag search resolves only an exact topic name', () {
    const topics = [
      MomentTopic(id: 1, name: '美食探店'),
      MomentTopic(id: 2, name: '美食'),
    ];

    expect(findExactMomentTopic(topics, '#美食')?.id, 2);
    expect(findExactMomentTopic(topics, '#不存在'), isNull);
  });

  testWidgets('submitting an existing hashtag opens its topic page', (
    tester,
  ) async {
    await tester.pumpWidget(
      _withServices(_MomentSearchClient(), const MomentSearchPage()),
    );
    await tester.enterText(find.byType(TextField), '#美食');
    await tester.tap(find.byTooltip('搜索'));
    await tester.pumpAndSettle();

    expect(find.text('#美食'), findsOneWidget);
    expect(find.text('3 人参与 · 4 条动态'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('reaction picker shows the complete emoji set and counts', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => showMomentReactionPicker(
              context,
              counts: const {'😀': 4},
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
    expect(find.text('4'), findsOneWidget);
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

  testWidgets('moment cards use a white surface without border or shadow', (
    tester,
  ) async {
    const moment = Moment(
      id: 9,
      authorId: 7,
      authorNickname: '用户',
      title: '无边框动态',
      content: '动态内容',
      visibility: 'PUBLIC',
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
    await tester.pumpWidget(const SizedBox.shrink());
  });
  test('dynamic and comment share messages both render as moment cards', () {
    expect(_message('MOMENT_SHARE').isMomentShare, isTrue);
    expect(_message('MOMENT_COMMENT_SHARE').isMomentShare, isTrue);
    expect(_message('TEXT').isMomentShare, isFalse);
  });

  test(
    'moment read visibility requires active tab and two-axis intersection',
    () {
      const viewport = Rect.fromLTWH(0, 0, 360, 720);
      expect(
        momentRectIntersectsViewport(
          const Rect.fromLTWH(10, 100, 200, 200),
          viewport,
          true,
        ),
        isTrue,
      );
      expect(
        momentRectIntersectsViewport(
          const Rect.fromLTWH(370, 100, 200, 200),
          viewport,
          true,
        ),
        isFalse,
      );
      expect(
        momentRectIntersectsViewport(
          const Rect.fromLTWH(10, 100, 200, 200),
          viewport,
          false,
        ),
        isFalse,
      );
    },
  );

  test('topic and draft metadata preserve the final API contract', () {
    final topic = MomentTopic.fromJson({
      'id': 7,
      'name': '晚饭',
      'userCount': 12,
      'momentCount': 31,
    });
    expect(topic.userCount, 12);
    expect(topic.momentCount, 31);
    final moment = Moment.fromJson({
      'id': 1,
      'title': '草稿标题',
      'content': '草稿内容',
      'status': 'DRAFT',
      'createdAt': '2026-07-01T10:00:00',
      'publishedAt': '2026-07-15T10:00:00',
      'visibility': 'PRIVATE',
    });
    expect(moment.status, 'DRAFT');
    expect(moment.publishedAt, '2026-07-15T10:00:00');
    expect(moment.createdAt, '2026-07-01T10:00:00');
  });

  test('quoted moment snapshots retain the complete card payload', () {
    final quoted = QuotedChatMessage.fromJson({
      'id': 9,
      'type': 'MOMENT_COMMENT_SHARE',
      'sharedMomentId': 5,
      'sharedMomentCommentId': 8,
      'sharedMomentTitle': '今天吃什么',
      'sharedMomentPreview': '番茄炒蛋',
      'sharedCommentPreview': '看起来很好吃',
      'sharedCardIcon': 'COMMENT',
    }).toChatMessage();
    expect(quoted.isMomentShare, isTrue);
    expect(quoted.sharedMomentCommentId, 8);
    expect(quoted.sharedCommentPreview, '看起来很好吃');
  });

  test('new page rebasing de-duplicates shifted page results', () {
    Moment moment(int id) => Moment.fromJson({
      'id': id,
      'title': '标题$id',
      'content': '内容$id',
      'visibility': 'PUBLIC',
    });
    final merged = mergeUniqueMoments(
      [moment(1), moment(2)],
      [moment(2), moment(3)],
    );
    expect(merged.map((item) => item.id), [1, 2, 3]);
  });

  test('private owner publications enter both feeds and topics show ten', () {
    expect(momentFeedsForOwnerPublication('PRIVATE'), {
      'RECOMMENDED',
      'RELATED',
    });
    expect(momentTopicSuggestionLimit, 10);
  });

  test('moment selector collapses continuously from scroll distance', () {
    expect(momentListCanHideSelector(120), isFalse);
    expect(momentListCanHideSelector(400), isTrue);
    expect(
      resolveMomentSelectorCollapse(
        current: 0,
        scrollDelta: 14,
        offset: 14,
        maxScrollExtent: 400,
      ),
      closeTo(.25, .001),
    );
    expect(
      resolveMomentSelectorCollapse(
        current: .25,
        scrollDelta: 42,
        offset: 56,
        maxScrollExtent: 400,
      ),
      1,
    );
    expect(
      resolveMomentSelectorCollapse(
        current: 1,
        scrollDelta: -28,
        offset: 80,
        maxScrollExtent: 400,
      ),
      closeTo(.5, .001),
    );
    expect(
      resolveMomentSelectorCollapse(
        current: .5,
        scrollDelta: 20,
        offset: 20,
        maxScrollExtent: 120,
      ),
      0,
    );
  });

  test('hot comment prefers likes then the newest comment', () {
    MomentComment comment(int id, int likes, String createdAt) => MomentComment(
      id: id,
      userId: id,
      nickname: '用户$id',
      content: '评论$id',
      createdAt: createdAt,
      likeCount: likes,
    );
    final selected = selectHotMomentComment([
      comment(1, 2, '2026-07-15T10:00:00'),
      comment(2, 5, '2026-07-15T09:00:00'),
      comment(3, 5, '2026-07-15T11:00:00'),
    ]);
    expect(selected.id, 3);
  });

  test('comment author metadata keeps the merchant badge', () {
    final comment = MomentComment.fromJson({
      'id': 1,
      'content': '欢迎来店里尝尝',
      'createdAt': '2026-07-16T10:00:00',
      'author': {'id': 8, 'nickname': '店主', 'role': 'SHOP'},
    });
    expect(comment.merchant, isTrue);
  });

  testWidgets('moment skeleton renders without a progress spinner', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: MomentFeedSkeleton(itemCount: 2))),
    );
    expect(find.byType(MomentCardSkeleton), findsNWidgets(2));
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('chat list skeleton matches rows and has no progress spinner', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: ChatListSkeleton(itemCount: 4))),
    );
    expect(find.byType(ShimmerBox), findsNWidgets(16));
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('chat moment selector shrinks proportionally while scrolling', (
    tester,
  ) async {
    await tester.pumpWidget(
      _withServices(_CollapsibleMomentClient(), const ChatListPage()),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('动态'));
    await tester.pumpAndSettle();

    final before = tester.getSize(find.byType(AppBar).first).height;
    await tester.drag(find.byType(ListView).first, const Offset(0, -28));
    await tester.pump();
    final after = tester.getSize(find.byType(AppBar).first).height;

    expect(before, momentSelectorHeight);
    expect(after, greaterThan(0));
    expect(after, lessThan(before));
    expect(tester.takeException(), isNull);
  });

  testWidgets('chat and moment switching preserves the related tab state', (
    tester,
  ) async {
    await tester.pumpWidget(
      _withServices(_CollapsibleMomentClient(), const ChatListPage()),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('动态').first);
    await tester.pumpAndSettle();
    final paneFinder = find.byKey(const ValueKey('customer-moment-pane'));
    final paneElement = tester.element(paneFinder);
    final recommendedScrollFinder = find.byKey(
      const ValueKey('customer-recommended-scroll'),
      skipOffstage: false,
    );
    final recommendedController = tester
        .widget<ListView>(recommendedScrollFinder)
        .controller;
    await tester.tap(find.text('相关'));
    await tester.pumpAndSettle();
    final relatedScrollFinder = find.byKey(
      const ValueKey('customer-related-scroll'),
      skipOffstage: false,
    );
    final relatedController = tester
        .widget<ListView>(relatedScrollFinder)
        .controller;
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
      tester.widget<ListView>(recommendedScrollFinder).controller,
      same(recommendedController),
    );
    expect(
      tester.widget<ListView>(relatedScrollFinder).controller,
      same(relatedController),
    );
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

  testWidgets('hot comment identities render as badge widgets', (tester) async {
    final moment = Moment.fromJson({
      'id': 5,
      'authorId': 1,
      'authorNickname': '发布者',
      'title': '动态',
      'content': '内容',
      'visibility': 'PUBLIC',
      'createdAt': '2026-07-16T10:00:00',
      'commentCount': 1,
      'hotComment': {
        'id': 6,
        'userId': 9,
        'nickname': '店主',
        'content': '欢迎来尝尝',
        'createdAt': '2026-07-16T10:01:00',
        'merchant': true,
        'authorBadge': true,
      },
    });
    await tester.pumpWidget(
      _withServices(
        _MomentFeedClient(),
        Scaffold(body: MomentCard(moment: moment)),
      ),
    );
    await tester.pump();

    expect(find.text('商家'), findsOneWidget);
    expect(find.text('作者'), findsOneWidget);
    expect(
      find.ancestor(of: find.text('商家'), matching: find.byType(Container)),
      findsWidgets,
    );
    expect(
      find.ancestor(of: find.text('作者'), matching: find.byType(Container)),
      findsWidgets,
    );
    expect(
      tester.getTopLeft(find.text('热门评论')).dy,
      greaterThan(
        tester.getBottomLeft(find.byIcon(Icons.ios_share_rounded)).dy,
      ),
    );
  });

  testWidgets('moment detail comments render a compact threaded layout', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 760));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      _withServices(_MomentDetailClient(), const MomentDetailPage(momentId: 5)),
    );
    await tester.pumpAndSettle();

    expect(find.text('这是一条清晰的根评论'), findsOneWidget);
    expect(find.text('这是线程中的回复'), findsOneWidget);
    expect(find.text('回复 店主'), findsOneWidget);
    expect(find.text('商家'), findsOneWidget);
    expect(find.text('作者'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('destructive confirmation requires an explicit choice', (
    tester,
  ) async {
    bool? result;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () async {
              result = await confirmDestructiveAction(
                context,
                title: '删除评论？',
                message: '删除后无法恢复。',
              );
            },
            child: const Text('删除'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    expect(find.text('删除评论？'), findsOneWidget);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(result, isFalse);
  });

  testWidgets(
    'user moments profile uses the shared feed skeleton while loading',
    (tester) async {
      final client = _ProfileClient();
      await tester.pumpWidget(
        _withServices(client, const UserMomentsPage(userId: 7, nickname: '用户')),
      );
      await tester.pump();
      expect(find.byType(MomentFeedSkeleton), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsNothing);

      client.response.complete(
        _response({
          'userId': 7,
          'canView': false,
          'hiddenByMe': false,
          'reason': '无法查看动态',
        }),
      );
      await tester.pumpAndSettle();
    },
  );

  testWidgets('deleting a feed moment removes its card immediately', (
    tester,
  ) async {
    final client = _MomentFeedClient();
    await tester.pumpWidget(
      _withServices(client, const MomentFeedPage(embedded: true, authorId: 7)),
    );
    await tester.pumpAndSettle();
    expect(find.text('待删除动态'), findsOneWidget);

    await tester.tap(find.byTooltip('更多操作'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除动态'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, '删除'));
    await tester.pumpAndSettle();

    expect(client.deleteCount, 1);
    expect(find.text('待删除动态'), findsNothing);
    expect(find.text('还没有动态'), findsOneWidget);
    await tester.pump(const Duration(seconds: 3));
  });
}
