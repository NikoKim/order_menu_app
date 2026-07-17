import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../main.dart';
import '../moment_models.dart';
import '../services/api_client.dart';
import '../services/moment_api.dart';
import '../services/moment_image.dart';
import '../services/customer_websocket_service.dart';
import '../utils/moment_time_formatter.dart';
import '../widgets/app_notice.dart';
import '../widgets/moment_card.dart';
import '../widgets/ui_utils.dart';

final Set<int> _customerViewedMomentIds = <int>{};

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

class MomentFeedPage extends StatefulWidget {
  const MomentFeedPage({
    super.key,
    this.embedded = false,
    this.authorId,
    this.title,
    this.onRelatedUnreadChanged,
    this.onHeaderCollapseChanged,
    this.headerCollapseExtent = 56,
    this.active = true,
  });
  final bool embedded;
  final int? authorId;
  final String? title;
  final ValueChanged<int>? onRelatedUnreadChanged;

  /// 当前动态列表滚动时，向外层“聊天 / 动态”页头回传 0～1 的压缩进度。
  /// 0 表示完全展开，1 表示完全收起。
  final ValueChanged<double>? onHeaderCollapseChanged;

  /// 外层页头用于完成压缩过渡的滚动距离。
  final double headerCollapseExtent;
  final bool active;

  @override
  State<MomentFeedPage> createState() => _MomentFeedPageState();
}

class _MomentFeedPageState extends State<MomentFeedPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  int _relatedUnread = 0;
  final _recommendedKey = GlobalKey<_MomentListState>();
  final _relatedKey = GlobalKey<_MomentListState>();
  double _headerCollapseProgress = 0;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: widget.authorId == null ? 2 : 1, vsync: this);
    _tabs.addListener(_handleTabChanged);
    if (widget.authorId == null)
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadUnread());
  }

  void _handleTabChanged() {
    if (_tabs.indexIsChanging || !mounted) return;
    setState(() {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final state = _tabs.index == 0
          ? _recommendedKey.currentState
          : _relatedKey.currentState;
      _handleHeaderCollapse(state?.headerCollapseProgress ?? 0);
      state?.resumeNewMomentObservation();
    });
  }

  @override
  void didUpdateWidget(covariant MomentFeedPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.active && widget.active) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        final state = _tabs.index == 0
            ? _recommendedKey.currentState
            : _relatedKey.currentState;
        state?.resumeNewMomentObservation();
      });
    }
  }

  void _handleHeaderCollapse(double progress) {
    final normalized = progress.clamp(0.0, 1.0).toDouble();
    if ((normalized - _headerCollapseProgress).abs() < 0.002) return;
    _headerCollapseProgress = normalized;
    widget.onHeaderCollapseChanged?.call(normalized);
  }

  Future<void> _loadUnread() async {
    try {
      final count = await CustomerApi(
        AppScope.of(context).api,
      ).relatedMomentUnreadCount();
      if (mounted) setState(() => _relatedUnread = count);
      widget.onRelatedUnreadChanged?.call(count);
    } catch (_) {}
  }

  void _readOne() {
    if (_relatedUnread <= 0) return;
    setState(() => _relatedUnread--);
    widget.onRelatedUnreadChanged?.call(_relatedUnread);
  }

  Future<void> _openComposer() async {
    final created = await Navigator.of(context).push<Moment>(
      MaterialPageRoute(builder: (_) => const MomentComposerPage()),
    );
    if (created == null) return;
    final feeds = momentFeedsForOwnerPublication(created.visibility);
    if (feeds.contains('RECOMMENDED')) {
      _recommendedKey.currentState?.insertPublished(created);
    }
    if (feeds.contains('RELATED')) {
      _relatedKey.currentState?.insertPublished(created);
    }
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final body = Column(
      children: [
        if (widget.authorId == null)
          _MomentFeedToolbar(
            controller: _tabs,
            relatedUnread: _relatedUnread,
            onSearch: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => const MomentSearchPage())),
            onCompose: _openComposer,
            onDrafts: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => const MomentDraftsPage())),
          ),
        Expanded(
          child: TabBarView(
            controller: _tabs,
            children: widget.authorId == null
                ? [
                    _MomentList(
                      key: _recommendedKey,
                      feed: 'RECOMMENDED',
                      isActive: () => widget.active && _tabs.index == 0,
                      headerCollapseExtent: widget.headerCollapseExtent,
                      onHeaderCollapseChanged: _handleHeaderCollapse,
                    ),
                    _MomentList(
                      key: _relatedKey,
                      feed: 'RELATED',
                      onMomentRead: _readOne,
                      isActive: () => widget.active && _tabs.index == 1,
                      headerCollapseExtent: widget.headerCollapseExtent,
                      onHeaderCollapseChanged: _handleHeaderCollapse,
                    ),
                  ]
                : [_MomentList(feed: 'USER', authorId: widget.authorId)],
          ),
        ),
      ],
    );
    if (widget.embedded) {
      return ColoredBox(
        color: const Color(0xFFF6F7F8),
        child: SafeArea(top: false, child: body),
      );
    }
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F8),
      appBar: AppBar(
        title: Text(
          widget.title ?? '动态',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        centerTitle: false,
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF202124),
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
      ),
      body: body,
    );
  }
}

class _MomentFeedToolbar extends StatelessWidget {
  const _MomentFeedToolbar({
    required this.controller,
    required this.relatedUnread,
    required this.onSearch,
    required this.onCompose,
    required this.onDrafts,
  });

  final TabController controller;
  final int relatedUnread;
  final VoidCallback onSearch;
  final VoidCallback onCompose;
  final VoidCallback onDrafts;

  @override
  Widget build(BuildContext context) => Material(
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
              controller: controller,
              dividerColor: Colors.transparent,
              indicator: const UnderlineTabIndicator(
                borderSide: BorderSide(color: Color(0xFFF28C28), width: 3),
                insets: EdgeInsets.symmetric(horizontal: 18),
              ),
              indicatorSize: TabBarIndicatorSize.tab,
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
                  child: _TabBadge(text: '相关', count: relatedUnread),
                ),
              ],
            ),
          ),
          _FeedToolbarAction(
            tooltip: '搜索动态',
            icon: Icons.search_rounded,
            onPressed: onSearch,
          ),
          const SizedBox(width: 2),
          _FeedToolbarAction(
            tooltip: '发布动态',
            icon: Icons.edit_note_rounded,
            emphasized: true,
            onPressed: onCompose,
          ),
          const SizedBox(width: 2),
          _FeedToolbarAction(
            tooltip: '草稿箱',
            icon: Icons.drafts_outlined,
            onPressed: onDrafts,
          ),
        ],
      ),
    ),
  );
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

class _MomentList extends StatefulWidget {
  const _MomentList({
    super.key,
    required this.feed,
    this.authorId,
    this.topicId,
    this.keyword,
    this.onMomentRead,
    this.isActive,
    this.onHeaderCollapseChanged,
    this.headerCollapseExtent = 56,
  });
  final String feed;
  final int? authorId;
  final int? topicId;
  final String? keyword;
  final VoidCallback? onMomentRead;
  final bool Function()? isActive;
  final ValueChanged<double>? onHeaderCollapseChanged;
  final double headerCollapseExtent;

  @override
  State<_MomentList> createState() => _MomentListState();
}

class _MomentListState extends State<_MomentList>
    with AutomaticKeepAliveClientMixin {
  final _controller = ScrollController();
  List<Moment> _items = const [];
  bool _loading = true;
  bool _more = true;
  Object? _error;
  int _page = 1;
  final Map<int, GlobalKey> _itemKeys = {};
  final Set<int> _reportedRead = {};
  bool _visibilityScheduled = false;
  Timer? _newMomentTimer;
  StreamSubscription<CustomerWsMessage>? _momentSubscription;
  List<Moment> _pendingMoments = const [];
  bool _checkingNewMoments = false;
  bool _showNewMomentSkeleton = false;
  DateTime _newMomentCursor = DateTime.now().toUtc();

  @override
  bool get wantKeepAlive => true;

  double get headerCollapseProgress {
    if (!_controller.hasClients || widget.headerCollapseExtent <= 0) return 0;
    return (_controller.offset / widget.headerCollapseExtent)
        .clamp(0.0, 1.0)
        .toDouble();
  }

  void _reportHeaderCollapse() {
    if (widget.isActive?.call() == false) return;
    widget.onHeaderCollapseChanged?.call(headerCollapseProgress);
  }

  @override
  void initState() {
    super.initState();
    _controller.addListener(() {
      _reportHeaderCollapse();
      if (_controller.position.extentAfter < 400 && !_loading && _more) {
        _load();
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _reportHeaderCollapse();
      _load(refresh: true);
    });
    _newMomentTimer = Timer.periodic(
      const Duration(seconds: 10),
      (_) => _checkForNewMoments(),
    );
    _momentSubscription = CustomerWebSocketService.instance
        .streamForModule('MOMENT')
        .listen((_) => _checkForNewMoments());
  }

  @override
  void dispose() {
    _controller.dispose();
    _newMomentTimer?.cancel();
    _momentSubscription?.cancel();
    super.dispose();
  }

  Future<void> checkForNewMoments({bool showSkeleton = false}) =>
      _checkForNewMoments(showSkeleton: showSkeleton);

  void resumeNewMomentObservation() {
    _newMomentCursor = DateTime.now().toUtc();
  }

  Future<void> _checkForNewMoments({bool showSkeleton = false}) async {
    final now = DateTime.now().toUtc();
    if ((widget.feed != 'RECOMMENDED' && widget.feed != 'RELATED') ||
        widget.isActive?.call() == false) {
      _newMomentCursor = now;
      return;
    }
    if (_loading || _checkingNewMoments) return;
    final tenSecondsAgo = now.subtract(const Duration(seconds: 10));
    final windowStart = _newMomentCursor.isAfter(tenSecondsAgo)
        ? _newMomentCursor
        : tenSecondsAgo;
    final windowEnd = now;
    _checkingNewMoments = true;
    if (showSkeleton && _items.isEmpty && mounted) {
      setState(() => _showNewMomentSkeleton = true);
    }
    try {
      final api = CustomerApi(AppScope.of(context).api);
      final known = {
        ..._items.map((item) => item.id),
        ..._pendingMoments.map((item) => item.id),
      };
      final incoming = <Moment>[];
      final incomingIds = <int>{};
      var page = 1;
      while (page <= 5) {
        final result = await api.moments(
          feed: widget.feed,
          page: page,
          keyword: widget.keyword,
          authorId: widget.authorId,
          topicId: widget.topicId,
          consumeExposure: false,
        );
        var reachedKnown = false;
        var reachedWindowStart = false;
        for (final item in result.items) {
          final publishedValue = item.publishedAt ?? item.createdAt;
          final publishedAt = parseMomentPublishedInstant(publishedValue);
          if (publishedAt != null && !publishedAt.isAfter(windowStart)) {
            reachedWindowStart = true;
          }
          if (known.contains(item.id)) {
            reachedKnown = true;
          } else if (shouldQueueMomentAsNew(
                mine: item.mine,
                viewed: _customerViewedMomentIds.contains(item.id),
                known: false,
                publishedAt: publishedValue,
                after: windowStart,
                through: windowEnd,
              ) &&
              incomingIds.add(item.id)) {
            incoming.add(item);
          }
        }
        if (reachedKnown || reachedWindowStart || !result.hasMore) break;
        page++;
      }
      if (!mounted) return;
      final currentIds = {
        ..._items.map((item) => item.id),
        ..._pendingMoments.map((item) => item.id),
      };
      incoming.removeWhere((item) => currentIds.contains(item.id));
      if (incoming.isNotEmpty) {
        setState(() => _pendingMoments = [...incoming, ..._pendingMoments]);
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

  void insertPublished(Moment moment) {
    setState(() {
      _pendingMoments = _pendingMoments
          .where((item) => item.id != moment.id)
          .toList();
      final existing = _items.indexWhere((item) => item.id == moment.id);
      if (existing >= 0) {
        _items[existing] = moment;
      } else {
        _items = [moment, ..._items];
      }
      _page = 1;
      _more = true;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_controller.hasClients)
        _controller.animateTo(
          0,
          duration: const Duration(milliseconds: 350),
          curve: Curves.easeOutCubic,
        );
    });
  }

  void _showPendingMoments() {
    if (_pendingMoments.isEmpty) return;
    var insertionIndex = 0;
    var nearestTop = double.infinity;
    for (var i = 0; i < _items.length; i++) {
      final itemContext = _itemKeys[_items[i].id]?.currentContext;
      final box = itemContext?.findRenderObject();
      if (box is! RenderBox || !box.attached) continue;
      final scrollableBox = itemContext == null
          ? null
          : Scrollable.maybeOf(itemContext)?.context.findRenderObject();
      if (scrollableBox is! RenderBox || !scrollableBox.attached) continue;
      final itemRect = box.localToGlobal(Offset.zero) & box.size;
      final viewportRect =
          scrollableBox.localToGlobal(Offset.zero) & scrollableBox.size;
      if (itemRect.overlaps(viewportRect) && itemRect.top < nearestTop) {
        nearestTop = itemRect.top;
        insertionIndex = i;
      }
    }
    final firstNewId = _pendingMoments.first.id;
    setState(() {
      _items = [
        ..._items.take(insertionIndex),
        ..._pendingMoments,
        ..._items.skip(insertionIndex),
      ];
      _pendingMoments = const [];
      _page = 1;
      _more = true;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final target = _itemKeys[firstNewId]?.currentContext;
      if (target != null)
        Scrollable.ensureVisible(
          target,
          duration: const Duration(milliseconds: 420),
          curve: Curves.easeOutCubic,
          alignment: 0,
        );
    });
  }

  Future<void> _load({bool refresh = false}) async {
    if (_loading && !refresh && _items.isNotEmpty) return;
    if (mounted)
      setState(() {
        _loading = true;
        if (refresh) _error = null;
      });
    try {
      final api = CustomerApi(AppScope.of(context).api);
      final next = refresh ? 1 : _page;
      final result = await api.moments(
        feed: widget.feed,
        page: next,
        keyword: widget.keyword,
        authorId: widget.authorId,
        topicId: widget.topicId,
      );
      if (!mounted) return;
      setState(() {
        _items = refresh
            ? result.items
            : mergeUniqueMoments(_items, result.items);
        _page = next + 1;
        _more = result.hasMore;
        _loading = false;
      });
      _scheduleVisibilityCheck();
    } catch (e) {
      if (mounted)
        setState(() {
          _error = e;
          _loading = false;
        });
    }
  }

  void _scheduleVisibilityCheck() {
    if (widget.isActive?.call() == false || _visibilityScheduled) {
      return;
    }
    _visibilityScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _visibilityScheduled = false;
      if (!mounted) return;
      final visible = <int>[];
      for (final item in _items) {
        final itemContext = _itemKeys[item.id]?.currentContext;
        final box = itemContext?.findRenderObject();
        if (box is! RenderBox || !box.attached) continue;
        final scrollableBox = itemContext == null
            ? null
            : Scrollable.maybeOf(itemContext)?.context.findRenderObject();
        if (scrollableBox is! RenderBox || !scrollableBox.attached) continue;
        final origin = box.localToGlobal(Offset.zero);
        final rect = origin & box.size;
        final viewportOrigin = scrollableBox.localToGlobal(Offset.zero);
        final viewportRect = viewportOrigin & scrollableBox.size;
        if (momentRectIntersectsViewport(
          rect,
          viewportRect,
          widget.isActive?.call() != false,
        )) {
          visible.add(item.id);
        }
      }
      if (visible.isEmpty) return;
      _customerViewedMomentIds.addAll(visible);
      if (widget.feed != 'RELATED') return;
      final unread = visible.where((id) {
        final item = _items.firstWhere((candidate) => candidate.id == id);
        return item.unread && !_reportedRead.contains(id);
      }).toList();
      if (unread.isEmpty) return;
      _reportedRead.addAll(unread);
      CustomerApi(
        AppScope.of(context).api,
      ).markMomentsRead(unread).catchError((_) {});
      for (var i = 0; i < unread.length; i++) {
        widget.onMomentRead?.call();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    if (_loading && _items.isEmpty) return const MomentFeedSkeleton();
    if (_error != null && _items.isEmpty) {
      return _FeedStateView(
        icon: Icons.cloud_off_rounded,
        title: '动态加载失败',
        message: '网络好像开小差了，请稍后重试',
        actionLabel: '重新加载',
        onAction: () => _load(refresh: true),
      );
    }
    if (_items.isEmpty) {
      return _withNewMomentOverlay(
        RefreshIndicator(
          onRefresh: () => _load(refresh: true),
          child: ListView(
            key: ValueKey('customer-${widget.feed.toLowerCase()}-scroll'),
            controller: _controller,
            physics: const AlwaysScrollableScrollPhysics(),
            children: const [
              SizedBox(height: 150),
              SizedBox(
                height: 240,
                child: _FeedStateView(
                  icon: Icons.dynamic_feed_outlined,
                  title: '还没有动态',
                  message: '这里会展示最新发布的内容',
                ),
              ),
            ],
          ),
        ),
      );
    }
    _scheduleVisibilityCheck();
    final list = RefreshIndicator(
      onRefresh: () => _load(refresh: true),
      child: NotificationListener<ScrollNotification>(
        onNotification: (_) {
          _scheduleVisibilityCheck();
          return false;
        },
        child: ListView.separated(
          key: ValueKey('customer-${widget.feed.toLowerCase()}-scroll'),
          controller: _controller,
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 28),
          itemCount: _items.length + (_loading ? 1 : 0),
          separatorBuilder: (_, _) => const SizedBox(height: 12),
          itemBuilder: (_, index) {
            if (index == _items.length)
              return const Center(
                child: Padding(
                  padding: EdgeInsets.all(12),
                  child: CircularProgressIndicator(),
                ),
              );
            final item = _items[index];
            return KeyedSubtree(
              key: _itemKeys.putIfAbsent(item.id, GlobalKey.new),
              child: MomentCard(
                moment: item,
                onChanged: (value) {
                  final currentIndex = _items.indexWhere(
                    (candidate) => candidate.id == value.id,
                  );
                  if (currentIndex >= 0) {
                    setState(() => _items[currentIndex] = value);
                  }
                },
                onDeleted: () {
                  if (!mounted) return;
                  setState(() {
                    _items = _items
                        .where((candidate) => candidate.id != item.id)
                        .toList();
                    _itemKeys.remove(item.id);
                    _reportedRead.remove(item.id);
                  });
                },
              ),
            );
          },
        ),
      ),
    );
    return _withNewMomentOverlay(list);
  }

  Widget _withNewMomentOverlay(Widget list) => Stack(
    children: [
      list,
      if (_pendingMoments.isNotEmpty)
        Positioned(
          top: 10,
          left: 54,
          right: 54,
          child: _NewMomentBanner(
            count: _pendingMoments.length,
            onTap: _showPendingMoments,
          ),
        ),
      if (_showNewMomentSkeleton && _pendingMoments.isEmpty)
        const Positioned(
          top: 10,
          left: 12,
          right: 12,
          child: IgnorePointer(child: MomentCardSkeleton(compact: true)),
        ),
    ],
  );
}

class _FeedStateView extends StatelessWidget {
  const _FeedStateView({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 36),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 68,
            height: 68,
            decoration: const BoxDecoration(
              color: Color(0xFFFFF0E2),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 32, color: const Color(0xFFE77910)),
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: const TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w700,
              color: Color(0xFF202124),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 13,
              height: 1.45,
              color: Color(0xFF8A8F98),
            ),
          ),
          if (onAction != null && actionLabel != null) ...[
            const SizedBox(height: 14),
            OutlinedButton.icon(
              onPressed: onAction,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: Text(actionLabel!),
            ),
          ],
        ],
      ),
    ),
  );
}

class _NewMomentBanner extends StatelessWidget {
  const _NewMomentBanner({required this.count, required this.onTap});

  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: const Color(0xFF2F3135),
    elevation: 4,
    shadowColor: Colors.black26,
    borderRadius: BorderRadius.circular(22),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(22),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.arrow_upward_rounded,
              size: 17,
              color: Colors.white,
            ),
            const SizedBox(width: 7),
            Flexible(
              child: Text(
                '有$count条新动态',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

bool momentRectIntersectsViewport(Rect rect, Rect viewport, bool tabActive) {
  if (!tabActive ||
      rect.width <= 0 ||
      rect.height <= 0 ||
      viewport.width <= 0 ||
      viewport.height <= 0) {
    return false;
  }
  return rect.overlaps(viewport);
}

const momentTopicSuggestionLimit = 10;

Set<String> momentFeedsForOwnerPublication(String visibility) => const {
  'RECOMMENDED',
  'RELATED',
};

List<Moment> mergeUniqueMoments(
  Iterable<Moment> existing,
  Iterable<Moment> incoming,
) {
  final ids = <int>{};
  return [
    ...existing,
    ...incoming,
  ].where((moment) => ids.add(moment.id)).toList();
}

class _TabBadge extends StatelessWidget {
  const _TabBadge({required this.text, required this.count});
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
                fontSize: 9,
                height: 1,
                color: Colors.white,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
    ],
  );
}

class MomentSearchPage extends StatefulWidget {
  const MomentSearchPage({super.key});

  @override
  State<MomentSearchPage> createState() => _MomentSearchPageState();
}

class MomentDraftsPage extends StatefulWidget {
  const MomentDraftsPage({super.key});

  @override
  State<MomentDraftsPage> createState() => _MomentDraftsPageState();
}

class _MomentDraftsPageState extends State<MomentDraftsPage> {
  Future<List<Moment>>? _future;
  bool _initialized = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    _future = CustomerApi(AppScope.of(context).api).momentDrafts();
  }

  void _reload() {
    if (!mounted) return;
    final future = CustomerApi(AppScope.of(context).api).momentDrafts();
    setState(() {
      _future = future;
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF6F7F8),
    appBar: AppBar(
      title: const Text('动态草稿', style: TextStyle(fontWeight: FontWeight.w700)),
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
    ),
    body: FutureBuilder<List<Moment>>(
      future: _future,
      builder: (context, snapshot) {
        if (!snapshot.hasData && !snapshot.hasError) {
          return const MomentFeedSkeleton(itemCount: 4);
        }
        if (snapshot.hasError) {
          return _FeedStateView(
            icon: Icons.cloud_off_rounded,
            title: '草稿加载失败',
            message: '没有获取到草稿内容，请稍后再试',
            actionLabel: '重新加载',
            onAction: _reload,
          );
        }
        final drafts = snapshot.data ?? const <Moment>[];
        if (drafts.isEmpty) {
          return const _FeedStateView(
            icon: Icons.note_alt_outlined,
            title: '还没有草稿',
            message: '未完成的动态会临时保存在这里',
          );
        }
        return RefreshIndicator(
          onRefresh: () async => _reload(),
          child: ListView.separated(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 28),
            itemCount: drafts.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (_, index) {
              final draft = drafts[index];
              final time = draft.editedAt?.isNotEmpty == true
                  ? formatMomentTime(draft.editedAt!)
                  : formatMomentTime(draft.createdAt);
              return Material(
                color: Colors.white,
                borderRadius: BorderRadius.circular(14),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () async {
                    final changed = await Navigator.of(context).push<Moment>(
                      MaterialPageRoute(
                        builder: (_) => MomentComposerPage(moment: draft),
                      ),
                    );
                    if (changed != null) _reload();
                  },
                  child: Container(
                    padding: const EdgeInsets.all(15),
                    decoration: BoxDecoration(
                      border: Border.all(color: const Color(0xFFEAECF0)),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 42,
                          height: 42,
                          decoration: BoxDecoration(
                            color: const Color(0xFFFFF0E2),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Icon(
                            Icons.edit_note_rounded,
                            color: Color(0xFFE77910),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      draft.title.trim().isEmpty
                                          ? '未命名草稿'
                                          : draft.title.trim(),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w700,
                                        color: Color(0xFF202124),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    time,
                                    style: const TextStyle(
                                      fontSize: 11,
                                      color: Color(0xFF9AA0A6),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 6),
                              Text(
                                draft.content.trim().isEmpty
                                    ? '暂未填写正文'
                                    : draft.content.trim(),
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 13,
                                  height: 1.45,
                                  color: Color(0xFF6F747A),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 6),
                        const Icon(
                          Icons.chevron_right_rounded,
                          color: Color(0xFFB0B4B9),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        );
      },
    ),
  );
}

class _MomentSearchPageState extends State<MomentSearchPage> {
  final _controller = TextEditingController();
  Timer? _timer;
  String _keyword = '';
  bool _searchingTopic = false;

  @override
  void dispose() {
    _controller.dispose();
    _timer?.cancel();
    super.dispose();
  }

  void _onChanged(String value) {
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 350), () {
      if (mounted) setState(() => _keyword = value.trim());
    });
    setState(() {});
  }

  Future<void> _submitSearch(String value) async {
    final keyword = value.trim();
    _timer?.cancel();
    setState(() => _keyword = keyword);
    if (!keyword.startsWith('#') || keyword.substring(1).trim().isEmpty) return;

    setState(() => _searchingTopic = true);
    try {
      final topics = await CustomerApi(
        AppScope.of(context).api,
      ).searchMomentTopics(keyword);
      if (!mounted) return;
      final exact = findExactMomentTopic(topics, keyword);
      if (exact != null) {
        await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => MomentTopicPage(topic: exact)),
        );
      }
    } catch (_) {
      // The normal moment search remains available when topic lookup fails.
    } finally {
      if (mounted) setState(() => _searchingTopic = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF6F7F8),
    appBar: AppBar(
      titleSpacing: 0,
      backgroundColor: Colors.white,
      foregroundColor: const Color(0xFF202124),
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
      title: Container(
        height: 42,
        margin: const EdgeInsets.only(right: 12),
        decoration: BoxDecoration(
          color: const Color(0xFFF2F3F5),
          borderRadius: BorderRadius.circular(13),
        ),
        child: TextField(
          controller: _controller,
          autofocus: true,
          textInputAction: TextInputAction.search,
          onChanged: _onChanged,
          onSubmitted: _submitSearch,
          decoration: InputDecoration(
            hintText: '搜索标题、正文或话题',
            hintStyle: const TextStyle(color: Color(0xFF9AA0A6)),
            prefixIcon: const Icon(Icons.search_rounded, size: 21),
            suffixIcon: _controller.text.isEmpty
                ? null
                : IconButton(
                    tooltip: '清空',
                    onPressed: () {
                      _controller.clear();
                      _timer?.cancel();
                      setState(() => _keyword = '');
                    },
                    icon: const Icon(Icons.close_rounded, size: 19),
                  ),
            border: InputBorder.none,
            contentPadding: const EdgeInsets.symmetric(vertical: 10),
          ),
        ),
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
    body: AnimatedSwitcher(
      duration: const Duration(milliseconds: 220),
      child: _keyword.isEmpty
          ? const _FeedStateView(
              key: ValueKey('search-empty'),
              icon: Icons.manage_search_rounded,
              title: '搜索动态',
              message: '输入标题、正文或话题关键词开始查找',
            )
          : _MomentList(
              key: ValueKey(_keyword),
              feed: 'SEARCH',
              keyword: _keyword,
            ),
    ),
  );
}

MomentTopic? findExactMomentTopic(Iterable<MomentTopic> topics, String query) {
  final expected = query.trim().replaceFirst(RegExp(r'^#+'), '').toLowerCase();
  if (expected.isEmpty) return null;
  for (final topic in topics) {
    if (topic.name.trim().toLowerCase() == expected) return topic;
  }
  return null;
}

class MomentTopicPage extends StatelessWidget {
  const MomentTopicPage({super.key, required this.topic});

  final MomentTopic topic;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF6F7F8),
    appBar: AppBar(
      title: Text(
        '#${topic.name}',
        style: const TextStyle(fontWeight: FontWeight.w700),
      ),
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
    ),
    body: Column(
      children: [
        Container(
          width: double.infinity,
          margin: const EdgeInsets.fromLTRB(12, 12, 12, 0),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFFFFF6ED), Color(0xFFFFFBF7)],
            ),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFFFE2C5)),
          ),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Text(
                  '#',
                  style: TextStyle(
                    fontSize: 25,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFFE77910),
                  ),
                ),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      topic.name,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFF202124),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${topic.userCount} 人参与 · ${topic.momentCount} 条动态',
                      style: const TextStyle(
                        fontSize: 13,
                        color: Color(0xFF7A7F85),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: _MomentList(feed: 'TOPIC', topicId: topic.id),
        ),
      ],
    ),
  );
}

class MomentComposerPage extends StatefulWidget {
  const MomentComposerPage({
    super.key,
    this.moment,
    this.shopId,
    this.dishId,
    this.shopName,
    this.dishName,
  });
  final Moment? moment;
  final int? shopId;
  final int? dishId;
  final String? shopName;
  final String? dishName;
  @override
  State<MomentComposerPage> createState() => _MomentComposerPageState();
}

class _MomentComposerPageState extends State<MomentComposerPage> {
  late final TextEditingController _title;
  late final TextEditingController _content;
  final _topicSearch = TextEditingController();
  final _picker = ImagePicker();
  List<MomentTopic> _topics = const [];
  List<MomentTopic> _suggestions = const [];
  List<MomentTopic> _hotTopics = const [];
  int _hotPage = 1;
  bool _hotHasMore = true;
  int _hotTotal = 0;
  bool _loadingTopics = false;
  final List<XFile> _images = [];
  late List<String> _existingImages;
  String _visibility = 'PUBLIC';
  bool _submitting = false;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _title = TextEditingController(text: widget.moment?.title ?? '');
    _content = TextEditingController(text: widget.moment?.content ?? '');
    _topics = widget.moment?.topics.toList() ?? [];
    _existingImages = widget.moment?.images.toList() ?? [];
    _visibility = widget.moment?.visibility ?? 'PUBLIC';
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadHotTopics());
  }

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    _topicSearch.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _pickImages() async {
    final selected = await _picker.pickMultiImage(imageQuality: 86);
    if (mounted)
      setState(() {
        _images.addAll(
          selected.take(9 - _images.length - _existingImages.length),
        );
      });
  }

  void _searchTopics(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () async {
      if (value.trim().isEmpty || !mounted) return;
      setState(() => _loadingTopics = true);
      try {
        final result = await CustomerApi(
          AppScope.of(context).api,
        ).momentTopics(query: value.replaceFirst('#', ''), pageSize: 10);
        if (mounted) setState(() => _suggestions = result.items);
      } catch (_) {
      } finally {
        if (mounted) setState(() => _loadingTopics = false);
      }
    });
  }

  Future<void> _loadHotTopics({bool next = false}) async {
    if (mounted) setState(() => _loadingTopics = true);
    try {
      final requestedPage = next ? (_hotHasMore ? _hotPage + 1 : 1) : 1;
      final result = await CustomerApi(
        AppScope.of(context).api,
      ).momentTopics(page: requestedPage, pageSize: 10);
      if (mounted)
        setState(() {
          _hotTopics = result.items;
          _hotPage = requestedPage;
          _hotHasMore = result.hasMore;
          _hotTotal = result.total;
        });
    } catch (_) {
    } finally {
      if (mounted) setState(() => _loadingTopics = false);
    }
  }

  Future<void> _addTopic() async {
    if (_topics.length >= 5) {
      AppNotice.show(context, '最多添加5个话题');
      return;
    }
    final name = _topicSearch.text.trim().replaceFirst('#', '');
    if (name.isEmpty) return;
    try {
      final existing = _suggestions
          .where((item) => item.name == name)
          .firstOrNull;
      final topic =
          existing ??
          await CustomerApi(AppScope.of(context).api).createMomentTopic(name);
      if (mounted && !_topics.any((item) => item.name == topic.name))
        setState(() {
          _topics = [..._topics, topic];
          _topicSearch.clear();
          _suggestions = const [];
        });
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    }
  }

  Future<void> _submit({bool draft = false}) async {
    if (!draft && _title.text.trim().length <= 3) {
      AppNotice.show(context, '标题必须大于3个字');
      return;
    }
    if (!draft && _content.text.trim().isEmpty) {
      AppNotice.show(context, '请输入动态内容');
      return;
    }
    if (_submitting) return;
    setState(() => _submitting = true);
    try {
      final api = CustomerApi(AppScope.of(context).api);
      final uploaded = <String>[..._existingImages];
      for (final image in _images) {
        final path = await prepareMomentImage(image.path, comment: false);
        uploaded.add(await api.uploadMomentImage(path));
      }
      final value = await api.publishMoment(
        id: widget.moment?.id,
        title: _title.text,
        content: _content.text,
        visibility: _visibility,
        topics: _topics.map((item) => item.name).toList(),
        imageUrls: uploaded,
        shopId: widget.shopId ?? widget.moment?.shopId,
        dishId: widget.dishId ?? widget.moment?.dishId,
        draft: draft,
      );
      if (!mounted) return;
      AppNotice.show(context, draft ? '草稿已保存，30天后自动清理' : '动态已发布');
      Navigator.pop(context, value);
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final linkedName =
        widget.dishName ??
        widget.shopName ??
        widget.moment?.dishName ??
        widget.moment?.shopName;
    final imageCount = _images.length + _existingImages.length;

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F8),
      appBar: AppBar(
        title: Text(
          widget.moment == null ? '发布动态' : '编辑动态',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF202124),
        surfaceTintColor: Colors.transparent,
        scrolledUnderElevation: 0,
        actions: [
          TextButton(
            onPressed: _submitting ? null : () => _submit(draft: true),
            child: const Text('存草稿'),
          ),
          const SizedBox(width: 6),
        ],
      ),
      body: ListView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 108),
        children: [
          _ComposerSection(
            title: '动态内容',
            icon: Icons.article_outlined,
            child: Column(
              children: [
                TextField(
                  controller: _title,
                  maxLength: 80,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF202124),
                  ),
                  decoration: const InputDecoration(
                    hintText: '写一个清晰的标题',
                    hintStyle: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Color(0xFFB0B4B9),
                    ),
                    counterStyle: TextStyle(color: Color(0xFF9AA0A6)),
                    border: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    contentPadding: EdgeInsets.zero,
                  ),
                ),
                const Divider(height: 20, color: Color(0xFFEEF0F2)),
                TextField(
                  controller: _content,
                  minLines: 8,
                  maxLines: 18,
                  style: const TextStyle(
                    fontSize: 15,
                    height: 1.6,
                    color: Color(0xFF3C4043),
                  ),
                  decoration: const InputDecoration(
                    hintText: '分享你的想法、体验或最新进展…',
                    hintStyle: TextStyle(color: Color(0xFFB0B4B9)),
                    border: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    contentPadding: EdgeInsets.zero,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _ComposerSection(
            title: '图片',
            icon: Icons.photo_library_outlined,
            trailing: Text(
              '$imageCount/9',
              style: const TextStyle(fontSize: 12, color: Color(0xFF9AA0A6)),
            ),
            child: Wrap(
              spacing: 9,
              runSpacing: 9,
              children: [
                for (final imageUrl in _existingImages)
                  _ComposerImageTile(
                    child: Image.network(
                      resolveCustomerImageUrl(imageUrl),
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => const ColoredBox(
                        color: Color(0xFFF0F1F3),
                        child: Icon(Icons.broken_image_outlined),
                      ),
                    ),
                    onRemove: () {
                      setState(() {
                        _existingImages = _existingImages
                            .where((item) => item != imageUrl)
                            .toList();
                      });
                    },
                  ),
                for (final image in _images)
                  _ComposerImageTile(
                    child: Image.file(File(image.path), fit: BoxFit.cover),
                    onRemove: () => setState(() => _images.remove(image)),
                  ),
                if (imageCount < 9)
                  InkWell(
                    onTap: _pickImages,
                    borderRadius: BorderRadius.circular(13),
                    child: Ink(
                      width: 92,
                      height: 92,
                      decoration: BoxDecoration(
                        color: const Color(0xFFF4F5F7),
                        borderRadius: BorderRadius.circular(13),
                        border: Border.all(color: const Color(0xFFE0E3E7)),
                      ),
                      child: const Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.add_photo_alternate_outlined,
                            color: Color(0xFFE77910),
                          ),
                          SizedBox(height: 5),
                          Text(
                            '添加图片',
                            style: TextStyle(
                              fontSize: 12,
                              color: Color(0xFF7A7F85),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
          if (linkedName != null) ...[
            const SizedBox(height: 12),
            _ComposerSection(
              title: '关联内容',
              icon: widget.dishId != null || widget.moment?.dishId != null
                  ? Icons.restaurant_menu_rounded
                  : Icons.storefront_outlined,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 11,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF6ED),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFFFE1C2)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.link_rounded, color: Color(0xFFE77910)),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        linkedName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF4A4E52),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 12),
          _ComposerSection(
            title: '话题',
            icon: Icons.tag_rounded,
            trailing: Text(
              '${_topics.length}/5',
              style: const TextStyle(fontSize: 12, color: Color(0xFF9AA0A6)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_topics.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Wrap(
                      spacing: 7,
                      runSpacing: 7,
                      children: [
                        for (final topic in _topics)
                          InputChip(
                            label: Text('#${topic.name}'),
                            visualDensity: VisualDensity.compact,
                            backgroundColor: const Color(0xFFFFF1E4),
                            side: const BorderSide(color: Color(0xFFFFD9B5)),
                            deleteIconColor: const Color(0xFFE77910),
                            labelStyle: const TextStyle(
                              color: Color(0xFFC8660B),
                              fontWeight: FontWeight.w600,
                            ),
                            onDeleted: () {
                              setState(() {
                                _topics = _topics
                                    .where((e) => e.id != topic.id)
                                    .toList();
                              });
                            },
                          ),
                      ],
                    ),
                  ),
                Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFFF4F5F7),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _topicSearch,
                          onChanged: (value) {
                            _searchTopics(value);
                            setState(() {});
                          },
                          onSubmitted: (_) => _addTopic(),
                          decoration: const InputDecoration(
                            prefixIcon: Icon(Icons.tag_rounded, size: 20),
                            hintText: '搜索或创建话题',
                            border: InputBorder.none,
                            contentPadding: EdgeInsets.symmetric(vertical: 12),
                          ),
                        ),
                      ),
                      IconButton(
                        tooltip: '添加话题',
                        onPressed: _addTopic,
                        icon: const Icon(Icons.add_circle_rounded),
                        color: const Color(0xFFE77910),
                      ),
                    ],
                  ),
                ),
                if (_suggestions.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 7,
                    runSpacing: 7,
                    children: _suggestions
                        .take(momentTopicSuggestionLimit)
                        .map(
                          (topic) => _TopicSuggestionChip(
                            topic: topic,
                            onTap: () {
                              if (_topics.length >= 5 ||
                                  _topics.any((item) => item.id == topic.id)) {
                                return;
                              }
                              setState(() {
                                _topics = [..._topics, topic];
                                _topicSearch.clear();
                                _suggestions = const [];
                              });
                            },
                          ),
                        )
                        .toList(),
                  ),
                ],
                if (_topicSearch.text.trim().isEmpty &&
                    _hotTopics.isNotEmpty) ...[
                  const SizedBox(height: 13),
                  Row(
                    children: [
                      const Text(
                        '热门话题',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF5F6368),
                        ),
                      ),
                      const Spacer(),
                      if (_hotTotal > 10)
                        TextButton.icon(
                          onPressed: _loadingTopics
                              ? null
                              : () => _loadHotTopics(next: true),
                          icon: const Icon(Icons.refresh_rounded, size: 16),
                          label: const Text('换一换'),
                        ),
                    ],
                  ),
                  Wrap(
                    spacing: 7,
                    runSpacing: 7,
                    children: [
                      for (var i = 0; i < _hotTopics.length.clamp(0, 10); i++)
                        _TopicSuggestionChip(
                          topic: _hotTopics[i],
                          onTap: () {
                            final topic = _hotTopics[i];
                            if (_topics.length >= 5 ||
                                _topics.any((item) => item.id == topic.id)) {
                              return;
                            }
                            setState(() => _topics = [..._topics, topic]);
                          },
                        ),
                    ],
                  ),
                ],
                if (_loadingTopics)
                  const Padding(
                    padding: EdgeInsets.only(top: 10),
                    child: LinearProgressIndicator(
                      minHeight: 2,
                      color: Color(0xFFE77910),
                      backgroundColor: Color(0xFFFFE7CF),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _ComposerSection(
            title: '谁可以看到',
            icon: Icons.visibility_outlined,
            child: _VisibilitySelector(
              value: _visibility,
              onChanged: (value) => setState(() => _visibility = value),
            ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(top: BorderSide(color: Color(0xFFEAECF0))),
          ),
          child: SizedBox(
            height: 48,
            child: FilledButton.icon(
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFFF28C28),
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFFFFD7B0),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              onPressed: _submitting ? null : _submit,
              icon: _submitting
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.send_rounded, size: 19),
              label: Text(
                widget.moment == null ? '发布动态' : '保存修改',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ComposerSection extends StatelessWidget {
  const _ComposerSection({
    required this.title,
    required this.icon,
    required this.child,
    this.trailing,
  });

  final String title;
  final IconData icon;
  final Widget child;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: const Color(0xFFEAECF0)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: const Color(0xFFFFF0E2),
                borderRadius: BorderRadius.circular(9),
              ),
              child: Icon(icon, size: 18, color: const Color(0xFFE77910)),
            ),
            const SizedBox(width: 9),
            Text(
              title,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w800,
                color: Color(0xFF202124),
              ),
            ),
            const Spacer(),
            if (trailing != null) trailing!,
          ],
        ),
        const SizedBox(height: 14),
        child,
      ],
    ),
  );
}

class _VisibilityOption {
  const _VisibilityOption(this.value, this.label, this.icon);

  final String value;
  final String label;
  final IconData icon;
}

class _VisibilitySelector extends StatelessWidget {
  const _VisibilitySelector({required this.value, required this.onChanged});

  final String value;
  final ValueChanged<String> onChanged;

  static const _items = [
    _VisibilityOption('PUBLIC', '公开', Icons.public_rounded),
    _VisibilityOption('RELATED', '相关', Icons.group_outlined),
    _VisibilityOption('PRIVATE', '私密', Icons.lock_outline_rounded),
  ];

  @override
  Widget build(BuildContext context) => Row(
    children: [
      for (var i = 0; i < _items.length; i++) ...[
        if (i > 0) const SizedBox(width: 8),
        Expanded(
          child: Material(
            color: value == _items[i].value
                ? const Color(0xFFFFF0E2)
                : const Color(0xFFF4F5F7),
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              onTap: () => onChanged(_items[i].value),
              borderRadius: BorderRadius.circular(12),
              child: Container(
                height: 46,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: value == _items[i].value
                        ? const Color(0xFFFFC68E)
                        : const Color(0xFFE6E8EB),
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      _items[i].icon,
                      size: 17,
                      color: value == _items[i].value
                          ? const Color(0xFFE77910)
                          : const Color(0xFF777C82),
                    ),
                    const SizedBox(width: 5),
                    Text(
                      _items[i].label,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: value == _items[i].value
                            ? const Color(0xFFC8660B)
                            : const Color(0xFF656A70),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    ],
  );
}

class _TopicSuggestionChip extends StatelessWidget {
  const _TopicSuggestionChip({required this.topic, required this.onTap});

  final MomentTopic topic;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ActionChip(
    onPressed: onTap,
    avatar: const Icon(Icons.tag_rounded, size: 15, color: Color(0xFFE77910)),
    label: Text('${topic.name} · ${topic.momentCount}'),
    visualDensity: VisualDensity.compact,
    backgroundColor: const Color(0xFFF7F7F8),
    side: const BorderSide(color: Color(0xFFE6E8EB)),
    labelStyle: const TextStyle(fontSize: 12, color: Color(0xFF666B71)),
  );
}

class _ComposerImageTile extends StatelessWidget {
  const _ComposerImageTile({required this.child, required this.onRemove});

  final Widget child;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) => Stack(
    clipBehavior: Clip.none,
    children: [
      ClipRRect(
        borderRadius: BorderRadius.circular(13),
        child: SizedBox(width: 92, height: 92, child: child),
      ),
      Positioned(
        top: -7,
        right: -7,
        child: Material(
          color: const Color(0xFF32353A),
          shape: const CircleBorder(),
          child: InkWell(
            onTap: onRemove,
            customBorder: const CircleBorder(),
            child: const SizedBox.square(
              dimension: 25,
              child: Icon(Icons.close_rounded, size: 15, color: Colors.white),
            ),
          ),
        ),
      ),
    ],
  );
}

/// 外层“聊天 / 动态”标题栏的通用压缩容器。
///
/// parent 页面将 progress 绑定到 MomentFeedPage.onHeaderCollapseChanged，
/// 就能得到随滚动连续压缩、渐隐的效果。
class MomentCollapsibleHeader extends StatelessWidget {
  const MomentCollapsibleHeader({
    super.key,
    required this.progress,
    required this.child,
    this.expandedHeight = 60,
  });

  final double progress;
  final double expandedHeight;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final value = progress.clamp(0.0, 1.0).toDouble();
    return ClipRect(
      child: Align(
        alignment: Alignment.topCenter,
        heightFactor: 1 - value,
        child: Opacity(
          opacity: 1 - value,
          child: Transform.translate(
            offset: Offset(0, -10 * value),
            child: SizedBox(height: expandedHeight, child: child),
          ),
        ),
      ),
    );
  }
}
