import 'dart:async';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models/moment_models.dart';
import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/moment_image.dart';
import '../services/websocket_service.dart';
import '../utils/moment_time_formatter.dart';
import '../widgets/app_notice.dart';
import '../widgets/moment_image_viewer.dart';
import '../widgets/moment_reaction_picker.dart';
import '../widgets/skeleton_box.dart';
import 'moment_editor_page.dart';
import 'dish_edit_page.dart';
import 'shop_profile_page.dart';

const _orange = Color(0xFFF28C28);
const _background = Color(0xFFF5F6F8);
final Set<int> _shopViewedMomentIds = <int>{};

bool shouldQueueMomentAsNew({
  required bool mine,
  required bool viewed,
  required bool known,
  required String publishedAt,
  required DateTime after,
  required DateTime through,
}) =>
    !mine &&
    !viewed &&
    !known &&
    momentPublishedInWindow(publishedAt, after: after, through: through);

double nextMomentHeaderCollapseExtent({
  required double current,
  required double scrollDelta,
  required double maxScrollExtent,
  double headerExtent = 60,
  double minScrollableExtent = 300,
}) {
  if (headerExtent <= 0 || maxScrollExtent < minScrollableExtent) return 0;
  return (current + scrollDelta).clamp(0.0, headerExtent).toDouble();
}

bool momentRectIntersectsViewport(
  Rect rect,
  Size viewport, {
  double topInset = 0,
}) =>
    rect.bottom > topInset &&
    rect.top < viewport.height &&
    rect.right > 0 &&
    rect.left < viewport.width;

int? findMomentCommentContainerId(
  Iterable<MomentComment> comments,
  int targetId,
) {
  for (final comment in comments) {
    if (comment.id == targetId) return comment.id;
    if (comment.replies.any((reply) => reply.id == targetId)) return comment.id;
  }
  return null;
}

List<MomentComment> rankMomentComments(Iterable<MomentComment> comments) {
  final ranked = comments.toList();
  ranked.sort((left, right) {
    final likes = right.likeCount.compareTo(left.likeCount);
    if (likes != 0) return likes;
    return right.createdAt.compareTo(left.createdAt);
  });
  return ranked;
}

bool shouldShowMomentEdited(MomentItem moment) {
  if (moment.status.toUpperCase() != 'PUBLISHED' ||
      moment.editedAt?.isNotEmpty != true) {
    return false;
  }
  final published = DateTime.tryParse(moment.createdAt);
  final edited = DateTime.tryParse(moment.editedAt!);
  if (published == null || edited == null) return false;
  return edited.isAfter(published.add(const Duration(seconds: 2)));
}

Future<bool> _confirmDelete(
  BuildContext context, {
  required String title,
  required String content,
}) async =>
    await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: Text(content),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('删除'),
          ),
        ],
      ),
    ) ??
    false;

class MomentsPane extends StatefulWidget {
  const MomentsPane({
    super.key,
    this.initialRelatedUnread = 0,
    this.onUnreadChanged,
    this.onHeaderCollapseChanged,
    this.headerCollapseExtent = 60,
    this.active = true,
  });
  final int initialRelatedUnread;
  final ValueChanged<int>? onUnreadChanged;
  final ValueChanged<double>? onHeaderCollapseChanged;
  final double headerCollapseExtent;
  final bool active;

  @override
  State<MomentsPane> createState() => _MomentsPaneState();
}

class _MomentsPaneState extends State<MomentsPane>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final _recommendedKey = GlobalKey<_MomentListViewState>();
  final _relatedKey = GlobalKey<_MomentListViewState>();
  late int _relatedUnread;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    _tabs.addListener(() {
      if (_tabs.indexIsChanging || !mounted) return;
      setState(() {});
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        final state = _tabs.index == 0
            ? _recommendedKey.currentState
            : _relatedKey.currentState;
        widget.onHeaderCollapseChanged?.call(
          state?.headerCollapseProgress ?? 0,
        );
        state?.resumeNewMomentObservation();
      });
    });
    _relatedUnread = widget.initialRelatedUnread;
    _refreshUnread();
  }

  Future<void> _refreshUnread() async {
    try {
      final count = await shopApi.fetchRelatedMomentUnreadCount();
      if (mounted) setState(() => _relatedUnread = count);
      widget.onUnreadChanged?.call(count);
    } catch (_) {}
  }

  void _checkCurrentFeed() {
    final state = _tabs.index == 0
        ? _recommendedKey.currentState
        : _relatedKey.currentState;
    state?.resumeNewMomentObservation();
  }

  @override
  void didUpdateWidget(covariant MomentsPane oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.active && widget.active) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _checkCurrentFeed();
      });
    }
  }

  void _readOne() {
    if (_relatedUnread <= 0) return;
    setState(() => _relatedUnread--);
    widget.onUnreadChanged?.call(_relatedUnread);
  }

  void _search() => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => const ShopMomentSearchPage()),
  );

  Future<void> _compose() async {
    final changed = await Navigator.push<MomentItem>(
      context,
      MaterialPageRoute(builder: (_) => const MomentEditorPage()),
    );
    if (changed == null || !mounted) return;
    _recommendedKey.currentState?.insertPublished(changed);
    _relatedKey.currentState?.insertPublished(changed);
  }

  Future<void> _openDrafts() async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const ShopMomentDraftsPage()),
    );
    if (changed == true && mounted) {
      _recommendedKey.currentState?.reload();
      _relatedKey.currentState?.reload();
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Material(
        color: Colors.white,
        child: Container(
          height: 58,
          padding: const EdgeInsets.fromLTRB(10, 4, 8, 4),
          decoration: const BoxDecoration(
            border: Border(bottom: BorderSide(color: Color(0xFFEEEEEE))),
          ),
          child: Row(
            children: [
              Expanded(
                child: TabBar(
                  controller: _tabs,
                  dividerColor: Colors.transparent,
                  indicator: const UnderlineTabIndicator(
                    borderSide: BorderSide(color: _orange, width: 3),
                    insets: EdgeInsets.symmetric(horizontal: 18),
                  ),
                  labelColor: const Color(0xFF202124),
                  unselectedLabelColor: const Color(0xFF777B82),
                  labelStyle: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                  unselectedLabelStyle: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w500,
                  ),
                  tabs: [
                    const Tab(text: '推荐'),
                    Tab(
                      child: _UnreadLabel(text: '相关', count: _relatedUnread),
                    ),
                  ],
                ),
              ),
              _FeedToolbarAction(
                tooltip: '搜索动态',
                onPressed: _search,
                icon: Icons.search_rounded,
              ),
              const SizedBox(width: 2),
              _FeedToolbarAction(
                tooltip: '发布动态',
                onPressed: _compose,
                icon: Icons.edit_note_rounded,
                emphasized: true,
              ),
              const SizedBox(width: 2),
              _FeedToolbarAction(
                tooltip: '草稿箱',
                onPressed: _openDrafts,
                icon: Icons.drafts_outlined,
              ),
            ],
          ),
        ),
      ),
      Expanded(
        child: TabBarView(
          controller: _tabs,
          children: [
            MomentListView(
              key: _recommendedKey,
              feed: 'recommended',
              isActive: () => widget.active && _tabs.index == 0,
              onHeaderCollapseChanged: widget.onHeaderCollapseChanged,
              headerCollapseExtent: widget.headerCollapseExtent,
            ),
            MomentListView(
              key: _relatedKey,
              feed: 'related',
              isActive: () => widget.active && _tabs.index == 1,
              onMomentRead: _readOne,
              onHeaderCollapseChanged: widget.onHeaderCollapseChanged,
              headerCollapseExtent: widget.headerCollapseExtent,
            ),
          ],
        ),
      ),
    ],
  );

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }
}

class _FeedToolbarAction extends StatelessWidget {
  const _FeedToolbarAction({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    this.emphasized = false,
  });
  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;
  final bool emphasized;

  @override
  Widget build(BuildContext context) => Tooltip(
    message: tooltip,
    child: Material(
      color: emphasized ? const Color(0xFFFFF0E2) : Colors.transparent,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(12),
        child: SizedBox.square(
          dimension: 42,
          child: Icon(
            icon,
            size: 23,
            color: emphasized
                ? const Color(0xFFE96F00)
                : const Color(0xFF4D5156),
          ),
        ),
      ),
    ),
  );
}

class MomentListView extends StatefulWidget {
  const MomentListView({
    super.key,
    this.feed = 'recommended',
    this.keyword,
    this.topicId,
    this.userId,
    this.onMomentRead,
    this.isActive,
    this.onHeaderCollapseChanged,
    this.headerCollapseExtent = 60,
  });
  final String feed;
  final String? keyword;
  final int? topicId;
  final int? userId;
  final VoidCallback? onMomentRead;
  final bool Function()? isActive;
  final ValueChanged<double>? onHeaderCollapseChanged;
  final double headerCollapseExtent;

  @override
  State<MomentListView> createState() => _MomentListViewState();
}

class _MomentListViewState extends State<MomentListView>
    with AutomaticKeepAliveClientMixin {
  final _controller = ScrollController();
  final List<MomentItem> _items = [];
  int _page = 0;
  bool _loading = false;
  bool _hasMore = true;
  String? _error;
  bool _didConsumeInitialExposure = false;
  List<MomentItem> _pending = const [];
  final Map<int, GlobalKey> _itemKeys = {};
  StreamSubscription? _momentSubscription;
  Timer? _silentTimer;
  double _headerCollapsedExtent = 0;
  bool _checkingNewMoments = false;
  bool _showNewMomentSkeleton = false;
  DateTime _newMomentCursor = DateTime.now().toUtc();

  @override
  bool get wantKeepAlive => true;

  double get headerCollapseProgress => widget.headerCollapseExtent <= 0
      ? 0
      : _headerCollapsedExtent / widget.headerCollapseExtent;

  void reload() => _load(refresh: true);

  void insertPublished(MomentItem item) {
    setState(() {
      _pending = _pending.where((pending) => pending.id != item.id).toList();
      final existing = _items.indexWhere((moment) => moment.id == item.id);
      if (existing >= 0) {
        _items[existing] = item;
      } else {
        _items.insert(0, item);
      }
      _page = 0;
      _hasMore = true;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_controller.hasClients) {
        _controller.animateTo(
          0,
          duration: const Duration(milliseconds: 320),
          curve: Curves.easeOutCubic,
        );
      }
    });
  }

  @override
  void initState() {
    super.initState();
    _controller.addListener(_scroll);
    _momentSubscription = ShopWebSocketService.instance
        .streamForModule('MOMENT')
        .listen((_) => _checkNewMoments());
    _silentTimer = Timer.periodic(
      const Duration(seconds: 10),
      (_) => _checkNewMoments(),
    );
    _load();
  }

  void _scroll() {
    if (_controller.position.extentAfter < 320) _load();
  }

  bool _handleHeaderScroll(ScrollUpdateNotification notification) {
    if (widget.isActive?.call() == false) return false;
    final delta = notification.scrollDelta;
    if (delta == null) return false;
    final next = nextMomentHeaderCollapseExtent(
      current: _headerCollapsedExtent,
      scrollDelta: delta,
      maxScrollExtent: notification.metrics.maxScrollExtent,
      headerExtent: widget.headerCollapseExtent,
    );
    if ((next - _headerCollapsedExtent).abs() < 0.01) return false;
    _headerCollapsedExtent = next;
    widget.onHeaderCollapseChanged?.call(headerCollapseProgress);
    return false;
  }

  Future<void> _load({bool refresh = false}) async {
    if (_loading || (!refresh && !_hasMore)) return;
    if (refresh) {
      _page = 0;
      _hasMore = true;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final seen = refresh ? <int>{} : _items.map((item) => item.id).toSet();
      final collected = <MomentItem>[];
      var cursor = _page;
      var hasMore = true;
      do {
        final consumeExposure =
            !_didConsumeInitialExposure && cursor == 0 && !refresh;
        final result = await _fetchPage(
          cursor,
          consumeExposure: consumeExposure,
        );
        if (consumeExposure) _didConsumeInitialExposure = true;
        cursor++;
        hasMore = result.hasMore;
        for (final item in result.items) {
          if (seen.add(item.id)) collected.add(item);
        }
        // After inserting newer posts, page offsets shift. Walk through pages
        // already represented locally until the first genuinely unseen row.
      } while (collected.isEmpty && hasMore);
      if (!mounted) return;
      setState(() {
        if (refresh) {
          _items.clear();
          _pending = const [];
        }
        _items.addAll(collected);
        _hasMore = hasMore;
        _page = cursor;
      });
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<MomentPage<MomentItem>> _fetchPage(
    int page, {
    bool consumeExposure = true,
  }) => shopApi.fetchMoments(
    feed: widget.feed,
    page: page,
    query: widget.keyword,
    topicId: widget.topicId,
    userId: widget.userId,
    consumeExposure: consumeExposure,
  );

  Future<void> checkForNewMoments({bool showSkeleton = false}) =>
      _checkNewMoments(showSkeleton: showSkeleton);

  void resumeNewMomentObservation() {
    _newMomentCursor = DateTime.now().toUtc();
  }

  Future<void> _checkNewMoments({bool showSkeleton = false}) async {
    final now = DateTime.now().toUtc();
    final normalizedFeed = widget.feed.toLowerCase();
    if ((normalizedFeed != 'recommended' && normalizedFeed != 'related') ||
        widget.isActive?.call() == false) {
      _newMomentCursor = now;
      return;
    }
    if (_loading || !mounted || _checkingNewMoments) return;
    final tenSecondsAgo = now.subtract(const Duration(seconds: 10));
    final windowStart = _newMomentCursor.isAfter(tenSecondsAgo)
        ? _newMomentCursor
        : tenSecondsAgo;
    final windowEnd = now;
    _checkingNewMoments = true;
    if (showSkeleton && _items.isEmpty) {
      setState(() => _showNewMomentSkeleton = true);
    }
    try {
      final known = {
        ..._items.map((item) => item.id),
        ..._pending.map((item) => item.id),
      };
      final fresh = <MomentItem>[];
      final freshIds = <int>{};
      var page = 0;
      while (page < 5) {
        final latest = await _fetchPage(page, consumeExposure: false);
        var reachedKnown = false;
        var reachedWindowStart = false;
        for (final item in latest.items) {
          final publishedAt = parseMomentPublishedInstant(item.createdAt);
          if (publishedAt != null && !publishedAt.isAfter(windowStart)) {
            reachedWindowStart = true;
          }
          if (known.contains(item.id)) {
            reachedKnown = true;
          } else if (shouldQueueMomentAsNew(
                mine: item.mine,
                viewed: _shopViewedMomentIds.contains(item.id),
                known: false,
                publishedAt: item.createdAt,
                after: windowStart,
                through: windowEnd,
              ) &&
              freshIds.add(item.id)) {
            fresh.add(item);
          }
        }
        if (reachedKnown || reachedWindowStart || !latest.hasMore) break;
        page++;
      }
      final currentIds = {
        ..._items.map((item) => item.id),
        ..._pending.map((item) => item.id),
      };
      fresh.removeWhere((item) => currentIds.contains(item.id));
      if (fresh.isNotEmpty && mounted) {
        setState(() {
          final pendingById = <int, MomentItem>{
            for (final item in fresh) item.id: item,
            for (final item in _pending) item.id: item,
          };
          _pending = pendingById.values.toList();
        });
      }
      _newMomentCursor = windowEnd;
    } catch (_) {
    } finally {
      _checkingNewMoments = false;
      if (mounted && _showNewMomentSkeleton) {
        setState(() => _showNewMomentSkeleton = false);
      }
    }
  }

  void _insertPending() {
    if (_pending.isEmpty) return;
    final insertAt = _firstVisibleItemIndex();
    final pending = List<MomentItem>.of(_pending);
    final known = _items.map((item) => item.id).toSet();
    pending.removeWhere((item) => known.contains(item.id));
    if (pending.isEmpty) return;
    final firstKey = _itemKeys.putIfAbsent(pending.first.id, GlobalKey.new);
    setState(() {
      _items.insertAll(insertAt, pending);
      _pending = const [];
      // Rebase offset pagination. Subsequent loads de-duplicate pages already
      // present and continue until they reach the first unseen older post.
      _page = 0;
      _hasMore = true;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = firstKey.currentContext;
      if (context == null) return;
      Scrollable.ensureVisible(
        context,
        alignment: 0,
        duration: const Duration(milliseconds: 420),
        curve: Curves.easeOutCubic,
      );
    });
  }

  int _firstVisibleItemIndex() {
    if (!_controller.hasClients) return 0;
    final viewport = _controller.position.context.storageContext
        .findRenderObject();
    if (viewport is! RenderBox) return 0;
    final viewportTop = viewport.localToGlobal(Offset.zero).dy;
    for (var index = 0; index < _items.length; index++) {
      final box = _itemKeys[_items[index].id]?.currentContext
          ?.findRenderObject();
      if (box is RenderBox &&
          box.localToGlobal(Offset.zero).dy + box.size.height > viewportTop) {
        return index;
      }
    }
    return 0;
  }

  @override
  void dispose() {
    _controller.dispose();
    _momentSubscription?.cancel();
    _silentTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    if (_items.isEmpty && _loading) return const _MomentFeedSkeleton();
    if (_items.isEmpty && _error != null) {
      return _MomentFeedStateView(
        icon: Icons.cloud_off_rounded,
        title: '动态加载失败',
        message: _error!,
        action: '重新加载',
        onAction: () => _load(refresh: true),
      );
    }
    final list = RefreshIndicator(
      onRefresh: () => _load(refresh: true),
      child: _items.isEmpty
          ? ListView(
              key: ValueKey('shop-${widget.feed.toLowerCase()}-scroll'),
              controller: _controller,
              children: const [
                SizedBox(height: 150),
                _MomentFeedEmptyContent(),
              ],
            )
          : NotificationListener<ScrollUpdateNotification>(
              onNotification: _handleHeaderScroll,
              child: ListView.separated(
                key: ValueKey('shop-${widget.feed.toLowerCase()}-scroll'),
                controller: _controller,
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 28),
                itemCount: _items.length + (_hasMore ? 1 : 0),
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  if (index == _items.length)
                    return const Padding(
                      padding: EdgeInsets.all(18),
                      child: Center(child: CircularProgressIndicator()),
                    );
                  final item = _items[index];
                  return _MomentReadMarker(
                    key: _itemKeys.putIfAbsent(item.id, GlobalKey.new),
                    moment: item,
                    enabled:
                        widget.isActive?.call() != false &&
                        widget.topicId == null &&
                        widget.userId == null,
                    markRead: widget.feed.toLowerCase() == 'related',
                    onVisible: () => _shopViewedMomentIds.add(item.id),
                    onRead: widget.onMomentRead,
                    child: MomentCard(
                      moment: item,
                      onChanged: () async {
                        try {
                          final updated = await shopApi.fetchMoment(item.id);
                          if (mounted) setState(() => _items[index] = updated);
                        } catch (_) {}
                      },
                      onDeleted: () => setState(() => _items.removeAt(index)),
                    ),
                  );
                },
              ),
            ),
    );
    return Stack(
      children: [
        Column(
          children: [
            AnimatedSize(
              duration: const Duration(milliseconds: 220),
              child: _pending.isEmpty
                  ? const SizedBox.shrink()
                  : Padding(
                      padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                      child: Material(
                        color: const Color(0xFFFFF1E4),
                        borderRadius: BorderRadius.circular(12),
                        child: InkWell(
                          onTap: _insertPending,
                          borderRadius: BorderRadius.circular(12),
                          child: SizedBox(
                            width: double.infinity,
                            height: 40,
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(
                                  '有 ${_pending.length} 条新动态，点击查看',
                                  style: const TextStyle(
                                    color: Color(0xFFC8660B),
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(width: 3),
                                const Icon(
                                  Icons.keyboard_arrow_up_rounded,
                                  size: 18,
                                  color: Color(0xFFC8660B),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
            ),
            Expanded(child: list),
          ],
        ),
        if (_showNewMomentSkeleton && _pending.isEmpty)
          const Positioned(
            top: 8,
            left: 12,
            right: 12,
            child: IgnorePointer(child: _NewMomentSkeletonCard()),
          ),
      ],
    );
  }
}

class _NewMomentSkeletonCard extends StatelessWidget {
  const _NewMomentSkeletonCard();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: const BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.all(Radius.circular(8)),
    ),
    child: const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            SkeletonBox(
              width: 40,
              height: 40,
              borderRadius: BorderRadius.all(Radius.circular(20)),
            ),
            SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SkeletonBox(width: 108, height: 14),
                SizedBox(height: 7),
                SkeletonBox(width: 72, height: 10),
              ],
            ),
          ],
        ),
        SizedBox(height: 14),
        SkeletonBox(width: double.infinity, height: 14),
        SizedBox(height: 8),
        FractionallySizedBox(
          widthFactor: .68,
          child: SkeletonBox(width: double.infinity, height: 12),
        ),
      ],
    ),
  );
}

class MomentCard extends StatefulWidget {
  const MomentCard({
    super.key,
    required this.moment,
    this.onChanged,
    this.onDeleted,
    this.onCommentTap,
    this.full = false,
  });
  final MomentItem moment;
  final VoidCallback? onChanged;
  final VoidCallback? onDeleted;
  final VoidCallback? onCommentTap;
  final bool full;

  @override
  State<MomentCard> createState() => _MomentCardState();
}

class _MomentCardState extends State<MomentCard> {
  MomentItem get moment => widget.moment;
  VoidCallback? get onChanged => widget.onChanged;
  VoidCallback? get onDeleted => widget.onDeleted;
  VoidCallback? get onCommentTap => widget.onCommentTap;
  bool get full => widget.full;
  late bool _liked = moment.liked;
  late int _likeCount = moment.likeCount;
  late List<MomentReaction> _reactions = List.of(moment.reactions);

  @override
  void didUpdateWidget(covariant MomentCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.moment != widget.moment) {
      _liked = moment.liked;
      _likeCount = moment.likeCount;
      _reactions = List.of(moment.reactions);
    }
  }

  Future<void> _like(BuildContext context) async {
    final previousLiked = _liked;
    final previousCount = _likeCount;
    setState(() {
      _liked = !_liked;
      _likeCount = (_likeCount + (_liked ? 1 : -1)).clamp(0, 1 << 31);
    });
    try {
      await shopApi.toggleMomentLike(moment.id, _liked);
      onChanged?.call();
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _liked = previousLiked;
          _likeCount = previousCount;
        });
      }
      if (context.mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _react(BuildContext context) async {
    final emoji = await showMomentReactionPicker(
      context,
      counts: {for (final item in _reactions) item.emoji: item.count},
      selected: {
        for (final item in _reactions)
          if (item.mine) item.emoji,
      },
    );
    if (emoji == null) return;
    final index = _reactions.indexWhere((item) => item.emoji == emoji);
    final selected = index >= 0 && _reactions[index].mine;
    final before = List<MomentReaction>.of(_reactions);
    setState(() {
      if (index < 0) {
        _reactions.add(MomentReaction(emoji: emoji, count: 1, mine: true));
      } else {
        final reaction = _reactions[index];
        final nextCount = reaction.count + (selected ? -1 : 1);
        if (nextCount <= 0) {
          _reactions.removeAt(index);
        } else {
          _reactions[index] = MomentReaction(
            emoji: emoji,
            count: nextCount,
            mine: !selected,
          );
        }
      }
    });
    try {
      await shopApi.toggleMomentReaction(moment.id, emoji, !selected);
      onChanged?.call();
    } on ApiException catch (error) {
      if (mounted) setState(() => _reactions = before);
      if (context.mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _share(BuildContext context, {int? commentId}) async {
    try {
      final chats = await shopApi.fetchConversations();
      if (!context.mounted) return;
      final selected = await showModalBottomSheet<ChatConversation>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            children: [
              const ListTile(
                title: Text(
                  '分享到聊天',
                  style: TextStyle(fontWeight: FontWeight.w800),
                ),
              ),
              ...chats.map(
                (chat) => ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.chat_outlined)),
                  title: Text(chat.title),
                  onTap: () => Navigator.pop(context, chat),
                ),
              ),
            ],
          ),
        ),
      );
      if (selected == null) return;
      await shopApi.shareMomentToChat(
        conversationId: selected.id,
        momentId: moment.id,
        commentId: commentId,
      );
      if (context.mounted) AppNotice.show(context, '已分享到${selected.title}');
    } on ApiException catch (error) {
      if (context.mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _more(BuildContext context) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (moment.mine)
              ListTile(
                leading: const Icon(Icons.edit_outlined),
                title: const Text('编辑动态'),
                onTap: () => Navigator.pop(context, 'edit'),
              ),
            if (moment.mine)
              ListTile(
                leading: const Icon(Icons.delete_outline, color: Colors.red),
                title: const Text('删除动态'),
                onTap: () => Navigator.pop(context, 'delete'),
              ),
            ListTile(
              leading: const Icon(Icons.share_outlined),
              title: const Text('分享到聊天'),
              onTap: () => Navigator.pop(context, 'share'),
            ),
          ],
        ),
      ),
    );
    if (action == 'edit') {
      final result = await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => MomentEditorPage(initial: moment)),
      );
      if (result != null) onChanged?.call();
    } else if (action == 'delete') {
      final confirmed = await _confirmDelete(
        context,
        title: '删除动态？',
        content: '删除后无法恢复，评论和互动也会一并移除。',
      );
      if (!confirmed) return;
      await shopApi.deleteMoment(moment.id);
      onDeleted?.call();
    } else if (action == 'share') {
      await _share(context);
    }
  }

  Future<void> _openDish(BuildContext context) async {
    final dishId = moment.dishId;
    if (dishId == null) return;
    try {
      final results = await Future.wait([
        shopApi.fetchDishes(pageSize: 100),
        shopApi.fetchCategories(),
      ]);
      final dishes = results[0] as PageResult<DishInfo>;
      final categories = results[1] as List<CategoryInfo>;
      final dish = dishes.records
          .where((item) => item.id == dishId)
          .firstOrNull;
      if (!context.mounted) return;
      if (dish == null || categories.isEmpty) {
        AppNotice.show(context, '该菜品不在当前店铺中');
        return;
      }
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => DishEditPage(dish: dish, categories: categories),
        ),
      );
    } on ApiException catch (error) {
      if (context.mounted) AppNotice.show(context, error.message);
    }
  }

  void _openAuthor(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => UserMomentsPage(
          userId: moment.authorId,
          nickname: moment.authorName,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final displayTime = shouldShowMomentEdited(moment)
        ? '编辑于 ${formatMomentTime(moment.editedAt!)}'
        : formatMomentTime(moment.createdAt);

    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(full ? 18 : 16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        borderRadius: BorderRadius.circular(full ? 18 : 16),
        onTap: full
            ? null
            : () async {
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        MomentDetailPage(momentId: moment.id, initial: moment),
                  ),
                );
                onChanged?.call();
              },
        child: Container(
          padding: full
              ? const EdgeInsets.fromLTRB(18, 17, 18, 0)
              : const EdgeInsets.fromLTRB(16, 15, 16, 8),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(16)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  InkWell(
                    customBorder: const CircleBorder(),
                    onTap: () => _openAuthor(context),
                    child: CircleAvatar(
                      radius: full ? 23 : 21,
                      backgroundColor: const Color(0xFFFFE8D2),
                      foregroundImage:
                          moment.authorAvatarUrl?.isNotEmpty == true
                          ? NetworkImage(
                              ApiConfig.resolveImageUrl(
                                moment.authorAvatarUrl!,
                              ),
                            )
                          : null,
                      child: moment.authorAvatarUrl?.isNotEmpty == true
                          ? null
                          : Icon(
                              Icons.person_outline_rounded,
                              size: full ? 24 : 22,
                              color: const Color(0xFF9C5A1B),
                            ),
                    ),
                  ),
                  const SizedBox(width: 11),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Flexible(
                              child: InkWell(
                                onTap: () => _openAuthor(context),
                                child: Text(
                                  moment.authorName,
                                  style: TextStyle(
                                    fontSize: full ? 15.5 : 15,
                                    fontWeight: FontWeight.w700,
                                    color: const Color(0xFF202124),
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ),
                            if (moment.authorMerchant)
                              const MomentRoleBadge(text: '商家'),
                          ],
                        ),
                        Row(
                          children: [
                            Text(
                              displayTime,
                              style: TextStyle(
                                fontSize: full ? 12 : 11.5,
                                color: const Color(0xFF92979F),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: '更多',
                    onPressed: () => _more(context),
                    icon: const Icon(Icons.more_horiz),
                  ),
                ],
              ),
              SizedBox(height: full ? 18 : 12),
              Text(
                moment.title,
                style: TextStyle(
                  fontSize: full ? 22 : 18,
                  height: 1.3,
                  fontWeight: FontWeight.w800,
                  color: const Color(0xFF202124),
                ),
              ),
              SizedBox(height: full ? 11 : 7),
              if (full)
                SelectableText(
                  moment.content,
                  style: const TextStyle(
                    fontSize: 16,
                    height: 1.7,
                    color: Color(0xFF303134),
                  ),
                )
              else
                Text(
                  moment.content,
                  maxLines: 5,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 15,
                    height: 1.58,
                    color: Color(0xFF3C4043),
                  ),
                ),
              if (!full && moment.content.length > 150)
                const Padding(
                  padding: EdgeInsets.only(top: 4),
                  child: Text('查看全文', style: TextStyle(color: _orange)),
                ),
              if (moment.images.isNotEmpty) ...[
                SizedBox(height: full ? 16 : 10),
                _MomentImages(images: moment.images),
              ],
              if (moment.shopId != null || moment.dishId != null) ...[
                SizedBox(height: full ? 15 : 10),
                _MomentReferenceCard(
                  moment: moment,
                  onTap: () {
                    if (moment.dishId != null) {
                      _openDish(context);
                    } else if (moment.mine) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const ShopProfilePage(),
                        ),
                      );
                    } else {
                      AppNotice.show(context, '该店铺可在顾客端查看');
                    }
                  },
                ),
              ],
              if (moment.topics.isNotEmpty) ...[
                SizedBox(height: full ? 14 : 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: moment.topics
                      .map(
                        (topic) => InkWell(
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => TopicMomentsPage(topic: topic),
                            ),
                          ),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 9,
                              vertical: 5,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFF1E4),
                              borderRadius: BorderRadius.circular(9),
                              border: Border.all(
                                color: const Color(0xFFFFD9B5),
                              ),
                            ),
                            child: Text(
                              '#${topic.name}',
                              style: const TextStyle(
                                fontSize: 12,
                                color: Color(0xFFC8660B),
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ],
              if (moment.visibility == 'PRIVATE') ...[
                SizedBox(height: full ? 12 : 10),
                const _VisibilityBadge(visibility: 'PRIVATE'),
              ],
              if (_reactions.isNotEmpty) ...[
                SizedBox(height: full ? 16 : 12),
                Wrap(
                  spacing: full ? 8 : 7,
                  runSpacing: full ? 8 : 7,
                  children: _reactions
                      .map(
                        (reaction) => _MomentReactionChip(
                          reaction: reaction,
                          detail: full,
                          onPressed: () async {
                            await shopApi.toggleMomentReaction(
                              moment.id,
                              reaction.emoji,
                              !reaction.mine,
                            );
                            onChanged?.call();
                          },
                        ),
                      )
                      .toList(),
                ),
              ],
              SizedBox(height: full ? 17 : 10),
              const Divider(height: 1, color: Color(0xFFEEEEEE)),
              SizedBox(
                height: full ? 54 : 48,
                child: Row(
                  children: [
                    Expanded(
                      child: _MomentAction(
                        icon: _liked
                            ? Icons.favorite_rounded
                            : Icons.favorite_border_rounded,
                        label: _likeCount == 0
                            ? '点赞'
                            : full
                            ? '点赞 $_likeCount'
                            : '$_likeCount',
                        active: _liked,
                        detail: full,
                        onTap: () => _like(context),
                      ),
                    ),
                    const _MomentActionDivider(),
                    Expanded(
                      child: _MomentAction(
                        icon: Icons.chat_bubble_outline_rounded,
                        label: moment.commentCount == 0
                            ? '评论'
                            : full
                            ? '评论 ${moment.commentCount}'
                            : '${moment.commentCount}',
                        detail: full,
                        onTap: full
                            ? (onCommentTap ?? () {})
                            : () => Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => MomentDetailPage(
                                    momentId: moment.id,
                                    initial: moment,
                                  ),
                                ),
                              ),
                      ),
                    ),
                    const _MomentActionDivider(),
                    Expanded(
                      child: _MomentReactionPickerAction(
                        detail: full,
                        onTap: () => _react(context),
                      ),
                    ),
                    const _MomentActionDivider(),
                    Expanded(
                      child: _MomentAction(
                        icon: Icons.ios_share_rounded,
                        label: '分享',
                        detail: full,
                        onTap: () => _share(context),
                      ),
                    ),
                  ],
                ),
              ),
              if (!full && moment.hotComment != null) ...[
                const SizedBox(height: 10),
                _HotCommentPreview(
                  comment: moment.hotComment!,
                  onTap: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => MomentDetailPage(
                          momentId: moment.id,
                          initial: moment,
                        ),
                      ),
                    );
                    onChanged?.call();
                  },
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _MomentReactionChip extends StatelessWidget {
  const _MomentReactionChip({
    required this.reaction,
    required this.detail,
    required this.onPressed,
  });

  final MomentReaction reaction;
  final bool detail;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onPressed,
    borderRadius: BorderRadius.circular(detail ? 10 : 9),
    child: Ink(
      height: detail ? 34 : 32,
      padding: EdgeInsets.symmetric(horizontal: detail ? 11 : 10),
      decoration: BoxDecoration(
        color: reaction.mine
            ? const Color(0xFFFFE8D2)
            : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(detail ? 10 : 9),
        border: reaction.mine
            ? Border.all(color: const Color(0xFFF3B77D))
            : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(reaction.emoji, style: TextStyle(fontSize: detail ? 17 : 16)),
          SizedBox(width: detail ? 6 : 5),
          Text(
            '${reaction.count}',
            style: TextStyle(
              fontSize: detail ? 12.5 : 12,
              fontWeight: FontWeight.w600,
              color: reaction.mine
                  ? const Color(0xFFC9650A)
                  : const Color(0xFF5F6368),
            ),
          ),
        ],
      ),
    ),
  );
}

class _MomentReactionPickerAction extends StatelessWidget {
  const _MomentReactionPickerAction({
    required this.detail,
    required this.onTap,
  });

  final bool detail;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: _MomentActionContent(
      icon: Icons.add_reaction_outlined,
      label: '回应',
      active: false,
      detail: detail,
    ),
  );
}

class _MomentAction extends StatelessWidget {
  const _MomentAction({
    required this.icon,
    required this.label,
    required this.onTap,
    required this.detail,
    this.active = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool detail;
  final bool active;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: _MomentActionContent(
      icon: icon,
      label: label,
      active: active,
      detail: detail,
    ),
  );
}

class _MomentActionContent extends StatelessWidget {
  const _MomentActionContent({
    required this.icon,
    required this.label,
    required this.active,
    required this.detail,
  });

  final IconData icon;
  final String label;
  final bool active;
  final bool detail;

  @override
  Widget build(BuildContext context) => Center(
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          icon,
          size: detail ? 20 : 19,
          color: active ? const Color(0xFFE64646) : const Color(0xFF646A70),
        ),
        const SizedBox(width: 5),
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 12,
              fontWeight: active ? FontWeight.w600 : FontWeight.w500,
              color: active ? const Color(0xFFE64646) : const Color(0xFF646A70),
            ),
          ),
        ),
      ],
    ),
  );
}

class _MomentActionDivider extends StatelessWidget {
  const _MomentActionDivider();

  @override
  Widget build(BuildContext context) => const SizedBox(
    height: 18,
    child: VerticalDivider(width: 1, color: Color(0xFFEEEEEE)),
  );
}

class MomentDetailPage extends StatefulWidget {
  const MomentDetailPage({
    super.key,
    required this.momentId,
    this.initial,
    this.highlightCommentId,
  });
  final int momentId;
  final MomentItem? initial;
  final int? highlightCommentId;

  @override
  State<MomentDetailPage> createState() => _MomentDetailPageState();
}

class _MomentDetailPageState extends State<MomentDetailPage> {
  MomentItem? _moment;
  final List<MomentComment> _comments = [];
  final _comment = TextEditingController();
  final _commentFocus = FocusNode();
  final _scroll = ScrollController();
  int _page = 0;
  bool _loadingComments = false;
  bool _hasMore = true;
  MomentComment? _replying;
  String? _commentImage;
  final Map<int, List<MomentComment>> _expandedReplies = {};
  final Map<int, int> _replyPages = {};
  final Map<int, GlobalKey> _commentKeys = {};
  int? _currentUserId;
  int? _highlightContainerId;
  bool _locatingReply = false;
  bool _momentUnavailable = false;
  String? _momentLoadError;

  @override
  void initState() {
    super.initState();
    _moment = widget.initial;
    shopApi
        .fetchCurrentUser()
        .then((user) {
          if (mounted) setState(() => _currentUserId = user.id);
        })
        .catchError((_) {});
    _reloadMoment();
    _loadComments();
    _scroll.addListener(() {
      if (_scroll.position.extentAfter < 260) _loadComments();
    });
  }

  Future<void> _reloadMoment() async {
    try {
      final value = await shopApi.fetchMoment(widget.momentId);
      if (mounted) {
        setState(() {
          _moment = value;
          _momentUnavailable = false;
          _momentLoadError = null;
        });
      }
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() {
        _momentLoadError = error.message;
        _momentUnavailable = error.statusCode == 403 || error.statusCode == 404;
      });
    }
  }

  Future<void> _loadComments({bool refresh = false}) async {
    if (_loadingComments || (!refresh && !_hasMore)) return;
    if (refresh) {
      _page = 0;
      _hasMore = true;
    }
    setState(() => _loadingComments = true);
    try {
      final result = await shopApi.fetchMomentComments(
        widget.momentId,
        page: _page,
      );
      if (!mounted) return;
      setState(() {
        if (refresh) _comments.clear();
        final ids = _comments.map((item) => item.id).toSet();
        _comments.addAll(result.items.where((item) => ids.add(item.id)));
        _page++;
        _hasMore = result.hasMore || result.items.length == 10;
      });
    } finally {
      if (mounted) setState(() => _loadingComments = false);
    }
    final highlightId = widget.highlightCommentId;
    if (!mounted || highlightId == null) return;
    final containerId = findMomentCommentContainerId(_comments, highlightId);
    if (containerId != null) {
      _showHighlighted(containerId, highlightId);
    } else if (_hasMore) {
      await _loadComments();
    } else {
      await _findHighlightedReply(highlightId);
    }
  }

  void _showHighlighted(int containerId, int targetId) {
    if (mounted) setState(() => _highlightContainerId = containerId);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final target = _commentKeys[targetId]?.currentContext;
      if (target != null) {
        Scrollable.ensureVisible(
          target,
          duration: const Duration(milliseconds: 650),
          curve: Curves.easeOutCubic,
          alignment: 0.25,
        );
      }
    });
  }

  Future<void> _findHighlightedReply(int targetId) async {
    if (_locatingReply) return;
    _locatingReply = true;
    try {
      for (final entry in _expandedReplies.entries) {
        if (entry.value.any((reply) => reply.id == targetId)) {
          _showHighlighted(entry.key, targetId);
          return;
        }
      }
      for (final root in _comments) {
        if (root.replyCount == 0) continue;
        var page = 0;
        final replies = <MomentComment>[];
        while (mounted) {
          final result = await shopApi.fetchCommentReplies(root.id, page: page);
          for (final reply in result.items) {
            if (!replies.any((item) => item.id == reply.id)) {
              replies.add(reply);
            }
          }
          if (replies.any((reply) => reply.id == targetId)) {
            if (!mounted) return;
            setState(() {
              _expandedReplies[root.id] = replies;
              _replyPages[root.id] = page + 1;
            });
            _showHighlighted(root.id, targetId);
            return;
          }
          if (!result.hasMore && result.items.length < 10) break;
          page++;
        }
      }
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      _locatingReply = false;
    }
  }

  Future<void> _sendComment() async {
    if (_comment.text.trim().isEmpty && _commentImage == null) return;
    try {
      await shopApi.addMomentComment(
        widget.momentId,
        _comment.text,
        parentId: _replying == null
            ? null
            : (_replying!.parentId ?? _replying!.id),
        replyToCommentId: _replying?.id,
        imageUrl: _commentImage,
      );
      _comment.clear();
      setState(() {
        _replying = null;
        _commentImage = null;
      });
      await _loadComments(refresh: true);
      await _reloadMoment();
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _pickCommentImage() async {
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (file == null) return;
    try {
      final prepared = await prepareMomentImage(file.path, comment: true);
      final url = await shopApi.uploadMomentImage(prepared, comment: true);
      if (mounted) setState(() => _commentImage = url);
    } on StateError {
      if (mounted) AppNotice.show(context, '图片压缩后仍超过5MB，请选择较小的图片');
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _moreReplies(MomentComment comment) async {
    final page = _replyPages[comment.id] ?? 0;
    final result = await shopApi.fetchCommentReplies(comment.id, page: page);
    if (!mounted) return;
    setState(() {
      final replies = _expandedReplies.putIfAbsent(comment.id, () => []);
      final ids = replies.map((item) => item.id).toSet();
      replies.addAll(result.items.where((item) => ids.add(item.id)));
      _replyPages[comment.id] = page + 1;
    });
  }

  Future<void> _likeComment(MomentComment comment) async {
    try {
      await shopApi.toggleCommentLike(comment.id, !comment.liked);
      await _loadComments(refresh: true);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _deleteComment(MomentComment comment) async {
    final confirmed = await _confirmDelete(
      context,
      title: comment.parentId == null ? '删除评论？' : '删除回复？',
      content: comment.parentId == null ? '删除后这条评论及相关回复将无法恢复。' : '删除后无法恢复。',
    );
    if (!confirmed) return;
    try {
      await shopApi.deleteMomentComment(comment.id);
      await _loadComments(refresh: true);
      await _reloadMoment();
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _shareComment(MomentComment comment) async {
    try {
      final chats = await shopApi.fetchConversations();
      if (!mounted) return;
      final selected = await showModalBottomSheet<ChatConversation>(
        context: context,
        showDragHandle: true,
        builder: (sheetContext) => SafeArea(
          child: ListView(
            children: [
              const ListTile(
                title: Text(
                  '分享到聊天',
                  style: TextStyle(fontWeight: FontWeight.w800),
                ),
              ),
              ...chats.map(
                (chat) => ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.chat_outlined)),
                  title: Text(chat.title),
                  onTap: () => Navigator.pop(sheetContext, chat),
                ),
              ),
            ],
          ),
        ),
      );
      if (selected == null) return;
      await shopApi.shareMomentToChat(
        conversationId: selected.id,
        momentId: widget.momentId,
        commentId: comment.id,
      );
      if (mounted) AppNotice.show(context, '已分享到${selected.title}');
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  @override
  void dispose() {
    _comment.dispose();
    _commentFocus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _background,
    appBar: AppBar(title: const Text('动态详情')),
    body: _momentUnavailable
        ? _MomentUnavailableView(onRetry: _reloadMoment)
        : _moment == null && _momentLoadError != null
        ? Center(
            child: TextButton(
              onPressed: _reloadMoment,
              child: Text('$_momentLoadError，点击重试'),
            ),
          )
        : _moment == null
        ? const _MomentDetailSkeleton()
        : Column(
            children: [
              Expanded(
                child: RefreshIndicator(
                  onRefresh: () async {
                    await _reloadMoment();
                    await _loadComments(refresh: true);
                  },
                  child: ListView(
                    controller: _scroll,
                    padding: const EdgeInsets.all(12),
                    children: [
                      MomentCard(
                        moment: _moment!,
                        full: true,
                        onChanged: _reloadMoment,
                        onCommentTap: _commentFocus.requestFocus,
                      ),
                      const SizedBox(height: 12),
                      Container(
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(18),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Padding(
                              padding: const EdgeInsets.fromLTRB(
                                18,
                                16,
                                18,
                                14,
                              ),
                              child: Row(
                                children: [
                                  const Text(
                                    '全部评论',
                                    style: TextStyle(
                                      fontSize: 18,
                                      fontWeight: FontWeight.w800,
                                      color: Color(0xFF202124),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  if (_moment!.commentCount > 0)
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 7,
                                        vertical: 2,
                                      ),
                                      decoration: BoxDecoration(
                                        color: const Color(0xFFF1F2F4),
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Text(
                                        '${_moment!.commentCount}',
                                        style: const TextStyle(
                                          fontSize: 11,
                                          color: Color(0xFF70757A),
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            const Divider(height: 1, color: Color(0xFFEEEEEE)),
                            Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 18,
                              ),
                              child: Column(
                                children: [
                                  if (_comments.isEmpty && !_loadingComments)
                                    const _EmptyMomentComments(),
                                  if (_comments.isEmpty && _loadingComments)
                                    const Column(
                                      children: [
                                        _CommentSkeleton(),
                                        _CommentSkeleton(),
                                        _CommentSkeleton(),
                                      ],
                                    ),
                                  for (final comment in _comments)
                                    _CommentTile(
                                      key: _commentKeys.putIfAbsent(
                                        comment.id,
                                        GlobalKey.new,
                                      ),
                                      comment: comment,
                                      highlighted:
                                          _highlightContainerId == comment.id &&
                                          widget.highlightCommentId ==
                                              comment.id,
                                      highlightCommentId:
                                          widget.highlightCommentId,
                                      commentKeys: _commentKeys,
                                      currentUserId: _currentUserId,
                                      replies:
                                          _expandedReplies[comment.id] ??
                                          comment.replies.take(3).toList(),
                                      expanded: _expandedReplies.containsKey(
                                        comment.id,
                                      ),
                                      onReply: (target) {
                                        setState(() => _replying = target);
                                        _commentFocus.requestFocus();
                                      },
                                      onDelete: _deleteComment,
                                      onLike: _likeComment,
                                      onShare: _shareComment,
                                      onMoreReplies: () =>
                                          _moreReplies(comment),
                                      onCollapse: () => setState(() {
                                        _expandedReplies.remove(comment.id);
                                        _replyPages.remove(comment.id);
                                      }),
                                    ),
                                  if (_loadingComments && _comments.isNotEmpty)
                                    const Padding(
                                      padding: EdgeInsets.symmetric(
                                        vertical: 16,
                                      ),
                                      child: SizedBox.square(
                                        dimension: 22,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Material(
                color: Colors.white,
                child: SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(10, 8, 8, 8),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (_replying != null || _commentImage != null)
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  _replying != null
                                      ? '回复 ${_replying!.nickname}'
                                      : '已选择图片',
                                  style: const TextStyle(color: Colors.grey),
                                ),
                              ),
                              IconButton(
                                tooltip: '取消',
                                onPressed: () => setState(() {
                                  _replying = null;
                                  _commentImage = null;
                                }),
                                icon: const Icon(Icons.close, size: 18),
                              ),
                            ],
                          ),
                        Row(
                          children: [
                            IconButton(
                              tooltip: '添加图片',
                              onPressed: _pickCommentImage,
                              icon: const Icon(Icons.image_outlined),
                            ),
                            Expanded(
                              child: TextField(
                                controller: _comment,
                                focusNode: _commentFocus,
                                minLines: 1,
                                maxLines: 4,
                                decoration: const InputDecoration(
                                  hintText: '写评论...',
                                ),
                              ),
                            ),
                            IconButton(
                              tooltip: '发送',
                              onPressed: _sendComment,
                              icon: const Icon(Icons.send, color: _orange),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
  );
}

class _EmptyMomentComments extends StatelessWidget {
  const _EmptyMomentComments();

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: 34),
    child: Center(
      child: Column(
        children: [
          Icon(Icons.mode_comment_outlined, size: 34, color: Color(0xFFB7BBC1)),
          SizedBox(height: 10),
          Text(
            '还没有评论',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Color(0xFF70757A),
            ),
          ),
          SizedBox(height: 3),
          Text(
            '来说说你的想法吧',
            style: TextStyle(fontSize: 12, color: Color(0xFFA0A4AA)),
          ),
        ],
      ),
    ),
  );
}

class _CommentTile extends StatefulWidget {
  const _CommentTile({
    super.key,
    required this.comment,
    required this.highlighted,
    required this.highlightCommentId,
    required this.commentKeys,
    required this.currentUserId,
    required this.replies,
    required this.expanded,
    required this.onReply,
    required this.onDelete,
    required this.onLike,
    required this.onShare,
    required this.onMoreReplies,
    required this.onCollapse,
  });

  final MomentComment comment;
  final bool highlighted;
  final int? highlightCommentId;
  final Map<int, GlobalKey> commentKeys;
  final int? currentUserId;
  final List<MomentComment> replies;
  final bool expanded;
  final ValueChanged<MomentComment> onReply;
  final ValueChanged<MomentComment> onDelete;
  final ValueChanged<MomentComment> onLike;
  final ValueChanged<MomentComment> onShare;
  final VoidCallback onMoreReplies;
  final VoidCallback onCollapse;

  @override
  State<_CommentTile> createState() => _CommentTileState();
}

class _CommentTileState extends State<_CommentTile>
    with SingleTickerProviderStateMixin {
  bool _contentExpanded = false;
  late final AnimationController _highlight = AnimationController(
    vsync: this,
    value: widget.highlighted ? 0.0 : 1.0,
    duration: const Duration(milliseconds: 1000),
  );

  @override
  void initState() {
    super.initState();
    if (widget.highlighted) _highlight.forward();
  }

  @override
  void didUpdateWidget(covariant _CommentTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.highlighted && !oldWidget.highlighted) {
      _highlight.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _highlight.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _highlight,
    builder: (_, child) => DecoratedBox(
      decoration: BoxDecoration(
        color: Color.lerp(
          const Color(0xFFFFF0DE),
          Colors.transparent,
          _highlight.value,
        ),
        borderRadius: BorderRadius.circular(8),
      ),
      child: child,
    ),
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CommentAvatar(
            comment: widget.comment,
            radius: 18,
            onTap: () => _openCommentAuthor(context, widget.comment),
          ),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _CommentHeader(
                  comment: widget.comment,
                  currentUserId: widget.currentUserId,
                  onAuthorTap: () =>
                      _openCommentAuthor(context, widget.comment),
                  onShare: () => widget.onShare(widget.comment),
                  onDelete: () => widget.onDelete(widget.comment),
                ),
                const SizedBox(height: 7),
                Text(
                  widget.comment.content,
                  maxLines: _contentExpanded ? null : 8,
                  overflow: _contentExpanded ? null : TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    height: 1.5,
                    color: Color(0xFF303134),
                  ),
                ),
                if (widget.comment.content.length > 240)
                  InkWell(
                    onTap: () =>
                        setState(() => _contentExpanded = !_contentExpanded),
                    borderRadius: BorderRadius.circular(6),
                    child: Padding(
                      padding: const EdgeInsets.only(top: 6, bottom: 2),
                      child: Text(
                        _contentExpanded ? '收起' : '展开评论',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFFE57300),
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                if (widget.comment.imageUrl?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(top: 10),
                    child: MomentImagePreview(
                      images: [widget.comment.imageUrl!],
                      index: 0,
                      resolveUrl: ApiConfig.resolveImageUrl,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: Image.network(
                          ApiConfig.resolveImageUrl(widget.comment.imageUrl!),
                          width: 148,
                          height: 148,
                          fit: BoxFit.cover,
                          loadingBuilder: (_, child, progress) =>
                              progress == null
                              ? child
                              : const SoftImageLoadingPlaceholder(
                                  width: 148,
                                  height: 148,
                                ),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                _CommentMetaActions(
                  comment: widget.comment,
                  onReply: () => widget.onReply(widget.comment),
                  onLike: () => widget.onLike(widget.comment),
                ),
                if (widget.replies.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 9),
                    child: _ReplyThread(
                      replies: widget.replies,
                      highlightCommentId: widget.highlightCommentId,
                      commentKeys: widget.commentKeys,
                      currentUserId: widget.currentUserId,
                      onReply: widget.onReply,
                      onLike: widget.onLike,
                      onShare: widget.onShare,
                      onDelete: widget.onDelete,
                    ),
                  ),
                if (widget.comment.replyCount > widget.replies.length ||
                    widget.expanded)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Wrap(
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        if (widget.comment.replyCount > widget.replies.length)
                          TextButton.icon(
                            style: _threadButtonStyle,
                            onPressed: widget.onMoreReplies,
                            icon: const Icon(
                              Icons.keyboard_arrow_down_rounded,
                              size: 17,
                            ),
                            label: Text(
                              '更多回复（${widget.comment.replyCount - widget.replies.length}）',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        if (widget.expanded)
                          TextButton(
                            style: _threadButtonStyle,
                            onPressed: widget.onCollapse,
                            child: const Text(
                              '收起',
                              style: TextStyle(fontSize: 12),
                            ),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

final ButtonStyle _threadButtonStyle = TextButton.styleFrom(
  padding: const EdgeInsets.symmetric(horizontal: 4),
  minimumSize: const Size(0, 34),
  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
);

class _ReplyThread extends StatelessWidget {
  const _ReplyThread({
    required this.replies,
    required this.highlightCommentId,
    required this.commentKeys,
    required this.currentUserId,
    required this.onReply,
    required this.onLike,
    required this.onShare,
    required this.onDelete,
  });

  final List<MomentComment> replies;
  final int? highlightCommentId;
  final Map<int, GlobalKey> commentKeys;
  final int? currentUserId;
  final ValueChanged<MomentComment> onReply;
  final ValueChanged<MomentComment> onLike;
  final ValueChanged<MomentComment> onShare;
  final ValueChanged<MomentComment> onDelete;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: const Color(0xFFF7F8FA),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Stack(
      children: [
        const Positioned(
          left: 13,
          top: 10,
          bottom: 10,
          child: VerticalDivider(
            width: 1,
            thickness: 1,
            color: Color(0xFFE1E4E8),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(left: 13),
          child: Column(
            children: [
              for (final reply in replies)
                _ReplyLine(
                  key: commentKeys.putIfAbsent(reply.id, GlobalKey.new),
                  reply: reply,
                  highlighted: highlightCommentId == reply.id,
                  currentUserId: currentUserId,
                  onReply: onReply,
                  onLike: onLike,
                  onShare: onShare,
                  onDelete: onDelete,
                ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _ReplyLine extends StatelessWidget {
  const _ReplyLine({
    super.key,
    required this.reply,
    required this.highlighted,
    required this.currentUserId,
    required this.onReply,
    required this.onLike,
    required this.onShare,
    required this.onDelete,
  });

  final MomentComment reply;
  final bool highlighted;
  final int? currentUserId;
  final ValueChanged<MomentComment> onReply;
  final ValueChanged<MomentComment> onLike;
  final ValueChanged<MomentComment> onShare;
  final ValueChanged<MomentComment> onDelete;

  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
    tween: Tween(begin: highlighted ? 0 : 1, end: 1),
    duration: const Duration(milliseconds: 1100),
    builder: (_, value, child) => DecoratedBox(
      decoration: BoxDecoration(
        color: Color.lerp(const Color(0xFFFFE8C8), Colors.transparent, value),
        borderRadius: BorderRadius.circular(8),
      ),
      child: child,
    ),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 6, 9),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CommentAvatar(
            comment: reply,
            radius: 14,
            onTap: () => _openCommentAuthor(context, reply),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _CommentHeader(
                  comment: reply,
                  currentUserId: currentUserId,
                  compact: true,
                  onAuthorTap: () => _openCommentAuthor(context, reply),
                  onShare: () => onShare(reply),
                  onDelete: () => onDelete(reply),
                ),
                if (reply.replyToName?.isNotEmpty == true) ...[
                  const SizedBox(height: 2),
                  Text(
                    '回复 ${reply.replyToName}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 10.5,
                      color: Color(0xFF8A8F98),
                    ),
                  ),
                ],
                const SizedBox(height: 5),
                Text(
                  reply.content,
                  style: const TextStyle(
                    fontSize: 13,
                    height: 1.45,
                    color: Color(0xFF3C4043),
                  ),
                ),
                if (reply.imageUrl?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: MomentImagePreview(
                      images: [reply.imageUrl!],
                      index: 0,
                      resolveUrl: ApiConfig.resolveImageUrl,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.network(
                          ApiConfig.resolveImageUrl(reply.imageUrl!),
                          width: 112,
                          height: 112,
                          fit: BoxFit.cover,
                          loadingBuilder: (_, child, progress) =>
                              progress == null
                              ? child
                              : const SoftImageLoadingPlaceholder(
                                  width: 112,
                                  height: 112,
                                ),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 5),
                _CommentMetaActions(
                  comment: reply,
                  compact: true,
                  onReply: () => onReply(reply),
                  onLike: () => onLike(reply),
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _CommentHeader extends StatelessWidget {
  const _CommentHeader({
    required this.comment,
    required this.currentUserId,
    required this.onAuthorTap,
    required this.onShare,
    required this.onDelete,
    this.compact = false,
  });

  final MomentComment comment;
  final int? currentUserId;
  final VoidCallback onAuthorTap;
  final VoidCallback onShare;
  final VoidCallback onDelete;
  final bool compact;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Expanded(
        child: Wrap(
          spacing: 5,
          runSpacing: 3,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            InkWell(
              onTap: onAuthorTap,
              child: Text(
                comment.nickname,
                style: TextStyle(
                  fontSize: compact ? 12.5 : 14,
                  fontWeight: FontWeight.w700,
                  color: compact
                      ? const Color(0xFF303134)
                      : const Color(0xFF202124),
                ),
              ),
            ),
            if (comment.merchant) const _CommentBadge(text: '商家'),
            if (comment.author) const _CommentBadge(text: '作者'),
          ],
        ),
      ),
      PopupMenuButton<String>(
        tooltip: compact ? '回复操作' : '评论操作',
        padding: EdgeInsets.zero,
        iconSize: compact ? 17 : 20,
        constraints: const BoxConstraints(minWidth: 180, maxWidth: 240),
        icon: const Icon(Icons.more_horiz_rounded, color: Color(0xFF8A8F98)),
        onSelected: (value) {
          if (value == 'share') onShare();
          if (value == 'delete') onDelete();
        },
        itemBuilder: (_) => [
          const PopupMenuItem(value: 'share', child: Text('分享到聊天')),
          if (comment.mine || currentUserId == comment.userId)
            PopupMenuItem(
              value: 'delete',
              child: Text(compact ? '删除回复' : '删除评论'),
            ),
        ],
      ),
    ],
  );
}

class _CommentAvatar extends StatelessWidget {
  const _CommentAvatar({
    required this.comment,
    required this.radius,
    required this.onTap,
  });

  final MomentComment comment;
  final double radius;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    customBorder: const CircleBorder(),
    child: CircleAvatar(
      radius: radius,
      backgroundColor: radius > 15
          ? const Color(0xFFFFE6CF)
          : const Color(0xFFE9ECEF),
      backgroundImage: comment.avatarUrl?.isNotEmpty == true
          ? NetworkImage(ApiConfig.resolveImageUrl(comment.avatarUrl!))
          : null,
      child: comment.avatarUrl?.isNotEmpty == true
          ? null
          : Icon(
              Icons.person_outline_rounded,
              size: radius,
              color: radius > 15
                  ? const Color(0xFFA85A18)
                  : const Color(0xFF70757A),
            ),
    ),
  );
}

class _CommentMetaActions extends StatelessWidget {
  const _CommentMetaActions({
    required this.comment,
    required this.onReply,
    required this.onLike,
    this.compact = false,
  });

  final MomentComment comment;
  final VoidCallback onReply;
  final VoidCallback onLike;
  final bool compact;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Expanded(
        child: Text(
          formatMomentTime(comment.createdAt),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: compact ? 10 : 11,
            color: const Color(0xFF9AA0A6),
          ),
        ),
      ),
      _CommentAction(
        icon: Icons.reply_rounded,
        label: '回复',
        compact: compact,
        onTap: onReply,
      ),
      if (!compact) const SizedBox(width: 2),
      _CommentAction(
        icon: comment.liked
            ? Icons.favorite_rounded
            : Icons.favorite_border_rounded,
        label: '${comment.likeCount}',
        active: comment.liked,
        compact: compact,
        onTap: onLike,
      ),
    ],
  );
}

class _CommentBadge extends StatelessWidget {
  const _CommentBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
    decoration: BoxDecoration(
      color: const Color(0xFFFFEBDD),
      borderRadius: BorderRadius.circular(5),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 9,
        height: 1,
        color: Color(0xFFB75E0A),
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}

class _CommentAction extends StatelessWidget {
  const _CommentAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.active = false,
    this.compact = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool active;
  final bool compact;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: compact ? 5 : 7,
          vertical: compact ? 4 : 6,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: compact ? 14 : 16,
              color: active ? const Color(0xFFE85D4A) : const Color(0xFF70757A),
            ),
            const SizedBox(width: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: compact ? 10 : 11,
                color: active
                    ? const Color(0xFFE85D4A)
                    : const Color(0xFF70757A),
                fontWeight: active ? FontWeight.w600 : FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

void _openCommentAuthor(BuildContext context, MomentComment comment) {
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) =>
          UserMomentsPage(userId: comment.userId, nickname: comment.nickname),
    ),
  );
}

class MomentReplyActionBar extends StatelessWidget {
  const MomentReplyActionBar({
    super.key,
    required this.createdAt,
    required this.liked,
    required this.likeCount,
    required this.canDelete,
    required this.onReply,
    required this.onLike,
    required this.onShare,
    required this.onDelete,
  });

  final String createdAt;
  final bool liked;
  final int likeCount;
  final bool canDelete;
  final VoidCallback onReply;
  final VoidCallback onLike;
  final VoidCallback onShare;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Flexible(
        child: Text(
          formatMomentTime(createdAt),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 11, color: Colors.grey),
        ),
      ),
      const SizedBox(width: 4),
      Expanded(
        flex: 2,
        child: Wrap(
          alignment: WrapAlignment.end,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 2,
          runSpacing: 0,
          children: [
            IconButton(
              constraints: const BoxConstraints.tightFor(width: 36, height: 36),
              visualDensity: VisualDensity.compact,
              tooltip: '回复',
              onPressed: onReply,
              icon: const Icon(Icons.reply, size: 17),
            ),
            TextButton.icon(
              style: TextButton.styleFrom(
                visualDensity: VisualDensity.compact,
                minimumSize: const Size(44, 36),
                padding: const EdgeInsets.symmetric(horizontal: 6),
              ),
              onPressed: onLike,
              icon: Icon(
                liked ? Icons.favorite : Icons.favorite_border,
                size: 15,
                color: liked ? Colors.red : null,
              ),
              label: Text('$likeCount'),
            ),
            SizedBox(
              width: 36,
              height: 36,
              child: PopupMenuButton<String>(
                padding: EdgeInsets.zero,
                tooltip: '更多操作',
                onSelected: (value) {
                  if (value == 'share') onShare();
                  if (value == 'delete') onDelete();
                },
                itemBuilder: (_) => [
                  const PopupMenuItem(value: 'share', child: Text('分享到聊天')),
                  if (canDelete)
                    const PopupMenuItem(value: 'delete', child: Text('删除回复')),
                ],
              ),
            ),
          ],
        ),
      ),
    ],
  );
}

class _VisibilityBadge extends StatelessWidget {
  const _VisibilityBadge({required this.visibility});
  final String visibility;

  @override
  Widget build(BuildContext context) {
    final (icon, label) = switch (visibility) {
      'PRIVATE' => (Icons.lock_outline, '私密'),
      'RELATED' => (Icons.people_outline, '相关'),
      _ => (Icons.public, '公开'),
    };
    return Container(
      margin: const EdgeInsets.only(left: 7),
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
      decoration: BoxDecoration(
        color: const Color(0xFFF1F2F4),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 11, color: Colors.grey),
          const SizedBox(width: 2),
          Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
        ],
      ),
    );
  }
}

class _MomentReferenceCard extends StatelessWidget {
  const _MomentReferenceCard({required this.moment, required this.onTap});
  final MomentItem moment;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final dish = moment.dishId != null;
    return Material(
      color: _background,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              Container(
                width: 52,
                height: 52,
                clipBehavior: Clip.antiAlias,
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF4E8),
                  borderRadius: BorderRadius.circular(6),
                ),
                child:
                    (dish ? moment.dishImageUrl : moment.shopImageUrl)
                            ?.isNotEmpty ==
                        true
                    ? Image.network(
                        ApiConfig.resolveImageUrl(
                          (dish ? moment.dishImageUrl : moment.shopImageUrl)!,
                        ),
                        fit: BoxFit.cover,
                      )
                    : Icon(
                        dish ? Icons.restaurant_menu : Icons.storefront,
                        color: _orange,
                      ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      dish ? '关联菜品' : '关联店铺',
                      style: const TextStyle(fontSize: 11, color: _orange),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      dish
                          ? (moment.dishName ?? '菜品')
                          : (moment.shopName ?? '店铺'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    if (dish && moment.dishDescription?.isNotEmpty == true)
                      Text(
                        moment.dishDescription!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          color: Colors.grey,
                        ),
                      ),
                    if (dish)
                      Row(
                        children: [
                          if (moment.dishPrice != null)
                            Text(
                              '¥${moment.dishPrice!.toStringAsFixed(2)}',
                              style: const TextStyle(
                                color: _orange,
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          if (moment.dishSalesCount != null) ...[
                            const SizedBox(width: 8),
                            Text(
                              '已售${moment.dishSalesCount}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                              ),
                            ),
                          ],
                        ],
                      ),
                    if (dish && moment.shopName?.isNotEmpty == true)
                      Text(
                        moment.shopName!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          color: Colors.grey,
                        ),
                      ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: Colors.grey),
            ],
          ),
        ),
      ),
    );
  }
}

class _HotCommentPreview extends StatelessWidget {
  const _HotCommentPreview({required this.comment, required this.onTap});

  final MomentComment comment;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Ink(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(11, 7, 4, 7),
      decoration: const BoxDecoration(
        border: Border(left: BorderSide(color: Color(0xFFF2A04B), width: 2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(
                Icons.local_fire_department_rounded,
                size: 15,
                color: Color(0xFFE77910),
              ),
              SizedBox(width: 4),
              Text(
                '热门评论',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFFE77910),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          _HotCommentLine(comment: comment),
          if (comment.replies.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8, left: 29),
              child: _HotCommentLine(
                comment: comment.replies.first,
                compact: true,
              ),
            ),
        ],
      ),
    ),
  );
}

class _HotCommentLine extends StatelessWidget {
  const _HotCommentLine({required this.comment, this.compact = false});

  final MomentComment comment;
  final bool compact;

  void _openUser(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) =>
            UserMomentsPage(userId: comment.userId, nickname: comment.nickname),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      InkWell(
        onTap: () => _openUser(context),
        customBorder: const CircleBorder(),
        child: CircleAvatar(
          radius: compact ? 9 : 11,
          backgroundColor: const Color(0xFFE9ECEF),
          backgroundImage: comment.avatarUrl?.isNotEmpty == true
              ? NetworkImage(ApiConfig.resolveImageUrl(comment.avatarUrl!))
              : null,
          child: comment.avatarUrl?.isNotEmpty == true
              ? null
              : Icon(
                  Icons.person_outline_rounded,
                  size: compact ? 11 : 13,
                  color: const Color(0xFF777B82),
                ),
        ),
      ),
      const SizedBox(width: 8),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Flexible(
                  child: Text(
                    comment.nickname,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: compact ? 11.5 : 12.5,
                      fontWeight: FontWeight.w700,
                      color: const Color(0xFF303134),
                    ),
                  ),
                ),
                if (comment.merchant) ...[
                  const SizedBox(width: 4),
                  const _MiniIdentityBadge(text: '商家'),
                ],
                if (comment.author) ...[
                  const SizedBox(width: 4),
                  const _MiniIdentityBadge(text: '作者'),
                ],
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    formatMomentTime(comment.createdAt),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: compact ? 9 : 10,
                      color: const Color(0xFF9AA0A6),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 3),
            Text(
              comment.content,
              maxLines: compact ? 1 : 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: compact ? 12 : 13,
                height: 1.4,
                color: const Color(0xFF44474C),
              ),
            ),
          ],
        ),
      ),
      if (!compact && comment.likeCount > 0) ...[
        const SizedBox(width: 8),
        const Icon(Icons.favorite_rounded, size: 12, color: Color(0xFFB1B5BB)),
        const SizedBox(width: 2),
        Text(
          '${comment.likeCount}',
          style: const TextStyle(fontSize: 11, color: Color(0xFF8A8F98)),
        ),
      ],
    ],
  );
}

class _MiniIdentityBadge extends StatelessWidget {
  const _MiniIdentityBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
    decoration: BoxDecoration(
      color: const Color(0xFFFFE9D2),
      borderRadius: BorderRadius.circular(3),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 9,
        height: 1.15,
        color: Color(0xFFB75E0A),
      ),
    ),
  );
}

class ShopMomentSearchPage extends StatefulWidget {
  const ShopMomentSearchPage({super.key});

  @override
  State<ShopMomentSearchPage> createState() => _ShopMomentSearchPageState();
}

class _ShopMomentSearchPageState extends State<ShopMomentSearchPage> {
  final _controller = TextEditingController();
  String _keyword = '';
  bool _searchingTopic = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _submitSearch(String value) async {
    final keyword = value.trim();
    setState(() => _keyword = keyword);
    if (!keyword.startsWith('#') || keyword.substring(1).trim().isEmpty) return;

    setState(() => _searchingTopic = true);
    try {
      final result = await shopApi.fetchMomentTopics(keyword: keyword);
      if (!mounted) return;
      final exact = findExactShopMomentTopic(result.items, keyword);
      if (exact != null) {
        await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => TopicMomentsPage(topic: exact)),
        );
      }
    } catch (_) {
      // Keep the normal moment results usable if topic lookup is unavailable.
    } finally {
      if (mounted) setState(() => _searchingTopic = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _background,
    appBar: AppBar(
      titleSpacing: 0,
      title: TextField(
        controller: _controller,
        autofocus: true,
        textInputAction: TextInputAction.search,
        decoration: const InputDecoration(
          hintText: '搜索标题、内容或话题',
          border: InputBorder.none,
        ),
        onSubmitted: _submitSearch,
      ),
      actions: [
        IconButton(
          tooltip: '搜索',
          onPressed: _searchingTopic
              ? null
              : () => _submitSearch(_controller.text),
          icon: _searchingTopic
              ? const SizedBox.square(
                  dimension: 19,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.search_rounded),
        ),
      ],
    ),
    body: _keyword.isEmpty
        ? const Center(child: Text('输入关键词搜索动态'))
        : MomentListView(key: ValueKey(_keyword), keyword: _keyword),
  );
}

MomentTopic? findExactShopMomentTopic(
  Iterable<MomentTopic> topics,
  String query,
) {
  final expected = query.trim().replaceFirst(RegExp(r'^#+'), '').toLowerCase();
  if (expected.isEmpty) return null;
  for (final topic in topics) {
    if (topic.name.trim().toLowerCase() == expected) return topic;
  }
  return null;
}

class ShopMomentDraftsPage extends StatefulWidget {
  const ShopMomentDraftsPage({super.key});

  @override
  State<ShopMomentDraftsPage> createState() => _ShopMomentDraftsPageState();
}

class _ShopMomentDraftsPageState extends State<ShopMomentDraftsPage> {
  late Future<List<MomentItem>> _future = shopApi.fetchMomentDrafts();

  void _reload() => setState(() => _future = shopApi.fetchMomentDrafts());

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _background,
    appBar: AppBar(title: const Text('草稿箱')),
    body: FutureBuilder<List<MomentItem>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const _MomentFeedSkeleton();
        }
        if (snapshot.hasError) {
          return Center(
            child: TextButton(
              onPressed: _reload,
              child: const Text('加载失败，点击重试'),
            ),
          );
        }
        final drafts = snapshot.data ?? const [];
        if (drafts.isEmpty) return const Center(child: Text('暂无草稿'));
        return ListView.separated(
          padding: const EdgeInsets.all(12),
          itemCount: drafts.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final draft = drafts[index];
            return ListTile(
              tileColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              leading: const Icon(Icons.drafts_outlined, color: _orange),
              title: Text(draft.title.isEmpty ? '未命名草稿' : draft.title),
              subtitle: Text(
                '${formatMomentTime(draft.editedAt ?? draft.createdAt)} · ${draft.content}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: IconButton(
                tooltip: '删除草稿',
                onPressed: () async {
                  final confirmed = await _confirmDelete(
                    context,
                    title: '删除草稿？',
                    content: '删除后无法恢复。',
                  );
                  if (!confirmed) return;
                  await shopApi.deleteMoment(draft.id);
                  _reload();
                },
                icon: const Icon(Icons.delete_outline_rounded),
              ),
              onTap: () async {
                final changed = await Navigator.push<MomentItem>(
                  context,
                  MaterialPageRoute(
                    builder: (_) => MomentEditorPage(initial: draft),
                  ),
                );
                if (changed != null && context.mounted) {
                  _reload();
                  Navigator.pop(context, true);
                }
              },
            );
          },
        );
      },
    ),
  );
}

class TopicMomentsPage extends StatelessWidget {
  const TopicMomentsPage({super.key, required this.topic});
  final MomentTopic topic;
  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _background,
    appBar: AppBar(
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('#${topic.name}'),
          Text(
            '${topic.userCount}人使用 · ${topic.momentCount}条动态',
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w400),
          ),
        ],
      ),
    ),
    body: MomentListView(topicId: topic.id),
  );
}

class UserMomentsPage extends StatefulWidget {
  const UserMomentsPage({
    super.key,
    required this.userId,
    required this.nickname,
    this.blockedReason,
  });
  final int userId;
  final String nickname;
  final String? blockedReason;

  @override
  State<UserMomentsPage> createState() => _UserMomentsPageState();
}

class _MomentFeedSkeleton extends StatelessWidget {
  const _MomentFeedSkeleton();

  @override
  Widget build(BuildContext context) => ListView.separated(
    padding: const EdgeInsets.all(12),
    itemCount: 3,
    separatorBuilder: (_, _) => const SizedBox(height: 10),
    itemBuilder: (_, index) => DecoratedBox(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.all(Radius.circular(8)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                SkeletonBox(
                  width: 40,
                  height: 40,
                  borderRadius: BorderRadius.all(Radius.circular(20)),
                ),
                SizedBox(width: 10),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SkeletonBox(width: 108, height: 14),
                    SizedBox(height: 7),
                    SkeletonBox(width: 72, height: 10),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 14),
            const SkeletonBox(width: double.infinity, height: 15),
            const SizedBox(height: 8),
            const SkeletonBox(width: double.infinity, height: 12),
            const SizedBox(height: 8),
            const SkeletonBox(width: 210, height: 12),
            if (index == 0) ...[
              const SizedBox(height: 12),
              const AspectRatio(
                aspectRatio: 2.4,
                child: SkeletonBox(
                  width: double.infinity,
                  height: double.infinity,
                ),
              ),
            ],
            const SizedBox(height: 16),
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                SkeletonBox(width: 54, height: 16),
                SkeletonBox(width: 54, height: 16),
                SkeletonBox(width: 54, height: 16),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _MomentFeedStateView extends StatelessWidget {
  const _MomentFeedStateView({
    required this.icon,
    required this.title,
    required this.message,
    required this.action,
    required this.onAction,
  });
  final IconData icon;
  final String title;
  final String message;
  final String action;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48, color: const Color(0xFF9AA0A6)),
          const SizedBox(height: 13),
          Text(
            title,
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Color(0xFF777B82)),
          ),
          const SizedBox(height: 12),
          TextButton.icon(
            onPressed: onAction,
            icon: const Icon(Icons.refresh_rounded),
            label: Text(action),
          ),
        ],
      ),
    ),
  );
}

class _MomentFeedEmptyContent extends StatelessWidget {
  const _MomentFeedEmptyContent();

  @override
  Widget build(BuildContext context) => const Center(
    child: Column(
      children: [
        Icon(Icons.dynamic_feed_outlined, size: 48, color: Color(0xFFB0B4BA)),
        SizedBox(height: 12),
        Text('暂无可展示的动态', style: TextStyle(fontWeight: FontWeight.w700)),
        SizedBox(height: 5),
        Text('稍后再来看看', style: TextStyle(color: Color(0xFF8A8F98))),
      ],
    ),
  );
}

class _MomentDetailSkeleton extends StatelessWidget {
  const _MomentDetailSkeleton();

  @override
  Widget build(BuildContext context) => const Column(
    children: [
      Expanded(child: _MomentFeedSkeleton()),
      ColoredBox(
        color: Colors.white,
        child: SafeArea(
          top: false,
          child: Padding(
            padding: EdgeInsets.all(12),
            child: SkeletonBox(width: double.infinity, height: 44),
          ),
        ),
      ),
    ],
  );
}

class _MomentUnavailableView extends StatelessWidget {
  const _MomentUnavailableView({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 78,
            height: 78,
            decoration: BoxDecoration(
              color: const Color(0xFFFFF0E2),
              borderRadius: BorderRadius.circular(22),
            ),
            child: const Icon(
              Icons.sentiment_very_dissatisfied_rounded,
              size: 42,
              color: Color(0xFFE77910),
            ),
          ),
          const SizedBox(height: 18),
          const Text(
            '帖子不见了',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 7),
          const Text(
            '它可能被删除、设为私密，或者偷偷溜出了你的可见范围。',
            textAlign: TextAlign.center,
            style: TextStyle(color: Color(0xFF777B82), height: 1.5),
          ),
          const SizedBox(height: 14),
          TextButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('再找一次'),
          ),
        ],
      ),
    ),
  );
}

class _CommentSkeleton extends StatelessWidget {
  const _CommentSkeleton();

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.only(bottom: 10),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SkeletonBox(
          width: 34,
          height: 34,
          borderRadius: BorderRadius.all(Radius.circular(17)),
        ),
        SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SkeletonBox(width: 96, height: 12),
              SizedBox(height: 9),
              SkeletonBox(width: double.infinity, height: 12),
              SizedBox(height: 7),
              SkeletonBox(width: 180, height: 12),
            ],
          ),
        ),
      ],
    ),
  );
}

class _MomentProfileSkeleton extends StatelessWidget {
  const _MomentProfileSkeleton();

  @override
  Widget build(BuildContext context) => const Column(
    children: [
      Padding(
        padding: EdgeInsets.all(16),
        child: Row(
          children: [
            SkeletonBox(
              width: 56,
              height: 56,
              borderRadius: BorderRadius.all(Radius.circular(28)),
            ),
            SizedBox(width: 12),
            Expanded(child: SkeletonBox(width: double.infinity, height: 18)),
          ],
        ),
      ),
      Expanded(child: _MomentFeedSkeleton()),
    ],
  );
}

class _UserMomentsPageState extends State<UserMomentsPage> {
  MomentProfileSummary? _summary;
  MomentPrivacy? _privacy;
  bool _loading = true;
  bool _saving = false;
  int _listGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final values = await Future.wait<Object>([
        shopApi.fetchMomentProfileSummary(widget.userId),
        shopApi.fetchMomentPrivacy(widget.userId),
      ]);
      if (!mounted) return;
      setState(() {
        _summary = values[0] as MomentProfileSummary;
        _privacy = values[1] as MomentPrivacy;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _loading = false);
      AppNotice.show(context, error.message);
    }
  }

  Future<void> _showTheirMoments() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      final current = _privacy ?? const MomentPrivacy();
      _privacy = await shopApi.updateMomentPrivacy(
        widget.userId,
        hideTheirMoments: false,
        hideMineFromThem: current.hideMineFromThem,
      );
      _summary = await shopApi.fetchMomentProfileSummary(widget.userId);
      if (!mounted) return;
      setState(() {
        _saving = false;
        _listGeneration++;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _saving = false);
      AppNotice.show(context, error.message);
    }
  }

  Widget _emptyState({
    required IconData icon,
    required String title,
    required String detail,
    Widget? action,
  }) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 56, color: const Color(0xFFB8B2AC)),
          const SizedBox(height: 16),
          Text(
            title,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            detail,
            style: const TextStyle(color: Colors.grey),
            textAlign: TextAlign.center,
          ),
          if (action != null) ...[const SizedBox(height: 20), action],
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _background,
    appBar: AppBar(title: Text('${widget.nickname}的动态')),
    body: _loading
        ? const _MomentProfileSkeleton()
        : (_summary?.hiddenByMe == true || _privacy?.hideTheirMoments == true)
        ? _emptyState(
            icon: Icons.visibility_off_outlined,
            title: '您已设置不看Ta动态',
            detail: '推荐、相关和个人动态页都已隐藏。关闭后会立即恢复显示。',
            action: FilledButton.icon(
              onPressed: _saving ? null : _showTheirMoments,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.visibility_outlined),
              label: const Text('关闭不看Ta并查看'),
            ),
          )
        : (_summary?.canView == false || widget.blockedReason != null)
        ? _emptyState(
            icon: Icons.lock_outline,
            title: '这里暂时没有向你开放',
            detail:
                _summary?.reason ?? widget.blockedReason ?? '对方调整了动态页的可见范围。',
          )
        : MomentListView(key: ValueKey(_listGeneration), userId: widget.userId),
  );
}

class _MomentImages extends StatelessWidget {
  const _MomentImages({required this.images});
  final List<String> images;
  @override
  Widget build(BuildContext context) {
    final columns = images.length == 1
        ? 1
        : images.length == 2
        ? 2
        : 3;
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: images.length.clamp(0, 9),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: columns,
        crossAxisSpacing: 5,
        mainAxisSpacing: 5,
      ),
      itemBuilder: (_, index) => MomentImagePreview(
        images: images,
        index: index,
        resolveUrl: ApiConfig.resolveImageUrl,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: Image.network(
            ApiConfig.resolveImageUrl(images[index]),
            fit: BoxFit.cover,
            loadingBuilder: (_, child, progress) =>
                progress == null ? child : const SoftImageLoadingPlaceholder(),
            errorBuilder: (_, _, _) => const ColoredBox(
              color: Color(0xFFF0F1F3),
              child: Center(
                child: Icon(
                  Icons.broken_image_outlined,
                  color: Color(0xFF9AA0A6),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class MomentRoleBadge extends StatelessWidget {
  const MomentRoleBadge({super.key, required this.text});
  final String text;
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(left: 6),
    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
    decoration: BoxDecoration(
      color: const Color(0xFFFFE8CF),
      borderRadius: BorderRadius.circular(4),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 10,
        color: Color(0xFFB76512),
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}

class _UnreadLabel extends StatelessWidget {
  const _UnreadLabel({required this.text, required this.count});
  final String text;
  final int count;

  @override
  Widget build(BuildContext context) => Stack(
    clipBehavior: Clip.none,
    children: [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5),
        child: Text(text),
      ),
      if (count > 0)
        Positioned(
          right: -10,
          top: -9,
          child: Container(
            constraints: const BoxConstraints(minWidth: 17, minHeight: 17),
            padding: const EdgeInsets.symmetric(horizontal: 5),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: const Color(0xFFE85D4A),
              borderRadius: BorderRadius.circular(9),
            ),
            child: Text(
              count > 99 ? '99+' : '$count',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 9,
                height: 1,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
    ],
  );
}

class _MomentReadMarker extends StatefulWidget {
  const _MomentReadMarker({
    super.key,
    required this.moment,
    required this.child,
    required this.enabled,
    required this.markRead,
    this.onVisible,
    this.onRead,
  });
  final MomentItem moment;
  final Widget child;
  final bool enabled;
  final bool markRead;
  final VoidCallback? onVisible;
  final VoidCallback? onRead;

  @override
  State<_MomentReadMarker> createState() => _MomentReadMarkerState();
}

class _MomentReadMarkerState extends State<_MomentReadMarker> {
  Timer? _timer;
  bool _reported = false;

  @override
  void initState() {
    super.initState();
    _syncTimer();
  }

  @override
  void didUpdateWidget(covariant _MomentReadMarker oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.enabled != widget.enabled ||
        oldWidget.moment.id != widget.moment.id) {
      if (oldWidget.moment.id != widget.moment.id) _reported = false;
      _syncTimer();
    }
  }

  void _syncTimer() {
    _timer?.cancel();
    _timer = null;
    if (!widget.enabled || _reported) return;
    _timer = Timer.periodic(const Duration(milliseconds: 350), (_) => _check());
    WidgetsBinding.instance.addPostFrameCallback((_) => _check());
  }

  void _check() {
    if (_reported || !mounted || !widget.enabled) return;
    final box = context.findRenderObject() as RenderBox?;
    if (box == null || !box.attached || !box.hasSize) return;
    final origin = box.localToGlobal(Offset.zero);
    final rect = origin & box.size;
    final viewport = MediaQuery.sizeOf(context);
    if (momentRectIntersectsViewport(
      rect,
      viewport,
      topInset: kToolbarHeight,
    )) {
      _reported = true;
      _timer?.cancel();
      widget.onVisible?.call();
      if (widget.markRead) {
        shopApi.markMomentRead(widget.moment.id).catchError((_) {});
        if (widget.moment.unread) widget.onRead?.call();
      }
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
