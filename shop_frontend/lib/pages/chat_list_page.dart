import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../services/latest_request.dart';
import '../widgets/app_notice.dart';
import '../widgets/skeleton_box.dart';
import 'chat_detail_page.dart';
import 'moment_pages.dart';

class ChatListPage extends StatefulWidget {
  const ChatListPage({super.key});

  @override
  State<ChatListPage> createState() => _ChatListPageState();
}

class _ChatListPageState extends State<ChatListPage>
    with WidgetsBindingObserver {
  late Future<List<ChatConversation>> _future;
  final _conversationRequests = LatestRequest();
  List<ChatConversation>? _cachedConversations;
  StreamSubscription? _chatSubscription;
  StreamSubscription? _notificationSubscription;
  StreamSubscription<bool>? _connectionSubscription;
  Timer? _refreshTimer;
  final Set<int> _pinningIds = {};
  final Map<int, bool> _pinnedOverrides = {};
  final Set<int> _deletingIds = {};
  final Set<int> _forceVisibleIds = {};
  final _swipeController = ConversationSwipeController();
  bool _showMoments = false;
  int _relatedMomentUnread = 0;
  double _topBarCollapseProgress = 0;

  int get _chatUnread => (_cachedConversations ?? const <ChatConversation>[])
      .fold(0, (total, conversation) => total + conversation.unreadCount);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _future = _requestConversations();
    _loadMomentUnread();
    _chatSubscription = ShopWebSocketService.instance
        .streamForModule('CHAT')
        .listen((event) {
          final conversationId = int.tryParse(
            '${event.data['conversationId'] ?? event.data['id'] ?? ''}',
          );
          if (event.action == 'MESSAGE_CREATED' && conversationId != null) {
            _forceVisibleIds.add(conversationId);
          }
          _reload();
        });
    _notificationSubscription = ShopWebSocketService.instance
        .streamForModule('NOTIFICATION')
        .where(
          (event) =>
              event.action == 'CREATED' || event.action == 'READ_CHANGED',
        )
        .listen((_) => _reload());
    _connectionSubscription = ShopWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) => _reload());
    _refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted) {
        _reload();
        _loadMomentUnread();
      }
    });
  }

  Future<void> _loadMomentUnread() async {
    try {
      final count = await shopApi.fetchRelatedMomentUnreadCount();
      if (mounted) setState(() => _relatedMomentUnread = count);
    } catch (_) {}
  }

  void _handleMomentHeaderCollapse(double progress) {
    final normalized = progress.clamp(0.0, 1.0).toDouble();
    if ((normalized - _topBarCollapseProgress).abs() < 0.002 || !mounted) {
      return;
    }
    setState(() => _topBarCollapseProgress = normalized);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _reload();
    }
  }

  Future<List<ChatConversation>> _requestConversations() async {
    final generation = _conversationRequests.begin();
    try {
      final fetched = await shopApi.fetchConversations();
      if (_conversationRequests.isLatest(generation)) {
        final conversations = fetched.map((conversation) {
          if (!_forceVisibleIds.contains(conversation.id)) return conversation;
          if (!conversation.hidden && !_deletingIds.contains(conversation.id)) {
            _forceVisibleIds.remove(conversation.id);
            return conversation;
          }
          return conversation.withPreferences(hidden: false);
        }).toList();
        final merge = mergePinnedConversationOverrides(
          conversations,
          _pinnedOverrides,
          pendingIds: _pinningIds,
        );
        for (final conversationId in merge.confirmedIds) {
          _pinnedOverrides.remove(conversationId);
        }
        _cachedConversations = merge.conversations;
        return merge.conversations;
      }
      return _cachedConversations ?? fetched;
    } catch (_) {
      final cached = _cachedConversations;
      if (cached != null) return cached;
      rethrow;
    }
  }

  Future<void> _reload() async {
    final future = _requestConversations();
    if (mounted) {
      setState(() {
        _future = future;
      });
    }
    await future;
  }

  void _markReadLocally(int conversationId) {
    final cached = _cachedConversations;
    if (cached == null) return;
    _conversationRequests.begin();
    setState(() {
      _cachedConversations = cached
          .map(
            (conversation) => conversation.id == conversationId
                ? conversation.markedRead()
                : conversation,
          )
          .toList();
    });
  }

  Future<void> _setPinned(ChatConversation conversation, bool pinned) async {
    if (_pinningIds.contains(conversation.id)) return;
    final before = _cachedConversations;
    if (before == null) return;
    _pinningIds.add(conversation.id);
    _pinnedOverrides[conversation.id] = pinned;
    _conversationRequests.begin();
    setState(() {
      _cachedConversations = before
          .map(
            (item) => item.id == conversation.id
                ? item.withPreferences(pinned: pinned)
                : item,
          )
          .toList();
    });
    try {
      final confirmed = await shopApi.updateConversationPreferences(
        conversation.id,
        pinned: pinned,
      );
      if (!mounted) return;
      _conversationRequests.begin();
      _pinnedOverrides[conversation.id] = confirmed.pinned;
      setState(() {
        _cachedConversations = _cachedConversations
            ?.map(
              (item) => item.id == conversation.id
                  ? item.withPreferences(pinned: confirmed.pinned)
                  : item,
            )
            .toList();
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      _conversationRequests.begin();
      _pinnedOverrides.remove(conversation.id);
      setState(() => _cachedConversations = before);
      AppNotice.show(context, error.message);
    } finally {
      _pinningIds.remove(conversation.id);
    }
  }

  Future<bool> _deleteConversation(ChatConversation conversation) async {
    if (!conversation.canDeleteFromList) {
      AppNotice.show(context, '自己的家庭群聊不能删除');
      return false;
    }
    _deletingIds.add(conversation.id);
    try {
      final result = await shopApi.deleteConversation(
        conversation.id,
        expectedMessageVersion: conversation.messageVersion,
      );
      if (mounted) {
        final current = _cachedConversations
            ?.where((item) => item.id == conversation.id)
            .firstOrNull;
        final shouldRemove = shouldRemoveConversationAfterHide(result, current);
        setState(() {
          if (shouldRemove) {
            _cachedConversations = _cachedConversations
                ?.where((item) => item.id != conversation.id)
                .toList();
          } else {
            _forceVisibleIds.add(conversation.id);
          }
        });
        if (!shouldRemove) await _reload();
        if (!mounted) return result.hidden;
        AppNotice.show(
          context,
          shouldRemove ? '会话已删除，商家无法主动恢复该私聊' : '收到新消息，会话已重新显示',
        );
      }
      return result.hidden;
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
      return false;
    } finally {
      _deletingIds.remove(conversation.id);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _chatSubscription?.cancel();
    _notificationSubscription?.cancel();
    _connectionSubscription?.cancel();
    _refreshTimer?.cancel();
    _swipeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F6F8),
    body: Column(
      children: [
        ColoredBox(
          color: Colors.white,
          child: SafeArea(
            bottom: false,
            child: ClipRect(
              child: Align(
                alignment: Alignment.topCenter,
                heightFactor: 1 - _topBarCollapseProgress,
                child: Opacity(
                  opacity: 1 - _topBarCollapseProgress,
                  child: Transform.translate(
                    offset: Offset(0, -10 * _topBarCollapseProgress),
                    child: SizedBox(
                      height: kToolbarHeight,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Row(
                          children: [
                            _TopSectionButton(
                              text: '聊天',
                              selected: !_showMoments,
                              count: _chatUnread,
                              onTap: () => setState(() {
                                _showMoments = false;
                                _topBarCollapseProgress = 0;
                              }),
                            ),
                            const SizedBox(width: 6),
                            _TopSectionButton(
                              text: '动态',
                              selected: _showMoments,
                              count: _relatedMomentUnread,
                              onTap: () => setState(() {
                                _showMoments = true;
                                _topBarCollapseProgress = 0;
                              }),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        Expanded(
          child: IndexedStack(
            index: _showMoments ? 0 : 1,
            children: [
              MomentsPane(
                key: const ValueKey('shop-moment-pane'),
                initialRelatedUnread: _relatedMomentUnread,
                active: _showMoments,
                onUnreadChanged: (count) {
                  if (mounted) setState(() => _relatedMomentUnread = count);
                },
                onHeaderCollapseChanged: _handleMomentHeaderCollapse,
                headerCollapseExtent: 60,
              ),
              RefreshIndicator(
                onRefresh: _reload,
                child: FutureBuilder<List<ChatConversation>>(
                  future: _future,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState != ConnectionState.done &&
                        _cachedConversations == null) {
                      return const _ChatListSkeleton();
                    }
                    if (snapshot.hasError && _cachedConversations == null) {
                      return ListView(
                        children: [
                          const SizedBox(height: 180),
                          Center(
                            child: TextButton(
                              onPressed: _reload,
                              child: const Text('聊天加载失败，点击重试'),
                            ),
                          ),
                        ],
                      );
                    }
                    final conversations = sortChatConversations(
                      _cachedConversations ??
                          snapshot.data ??
                          const <ChatConversation>[],
                    );
                    if (conversations.isEmpty) {
                      return ListView(
                        children: const [
                          SizedBox(height: 180),
                          Center(child: Text('暂无聊天')),
                        ],
                      );
                    }
                    return ListView.separated(
                      padding: const EdgeInsets.all(12),
                      itemCount: conversations.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 8),
                      itemBuilder: (context, index) {
                        final conversation = conversations[index];
                        final showPinnedHeader =
                            index == 0 && conversation.pinned;
                        final showOthersHeader =
                            !conversation.pinned &&
                            (index == 0 || conversations[index - 1].pinned);
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (showPinnedHeader || showOthersHeader)
                              Padding(
                                padding: const EdgeInsets.fromLTRB(4, 4, 4, 8),
                                child: Text(
                                  showPinnedHeader ? '置顶聊天' : '全部聊天',
                                  style: const TextStyle(
                                    color: Colors.grey,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ConversationSwipeTile(
                              key: ValueKey('conversation-${conversation.id}'),
                              itemId: conversation.id,
                              controller: _swipeController,
                              pinned: conversation.pinned,
                              canDelete: conversation.canDeleteFromList,
                              onPin: () => _setPinned(
                                conversation,
                                !conversation.pinned,
                              ),
                              onDelete: () => _deleteConversation(conversation),
                              child: Container(
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: ListTile(
                                  contentPadding: const EdgeInsets.symmetric(
                                    horizontal: 16,
                                    vertical: 4,
                                  ),
                                  leading: Stack(
                                    clipBehavior: Clip.none,
                                    children: [
                                      _ConversationAvatar(
                                        conversation: conversation,
                                      ),
                                      if (conversation.shouldShowUnreadBadge)
                                        Positioned(
                                          right: -7,
                                          top: -7,
                                          child: _UnreadBadge(
                                            count: conversation.unreadCount,
                                            muted: conversation.muted,
                                          ),
                                        ),
                                    ],
                                  ),
                                  title: Row(
                                    children: [
                                      Flexible(
                                        child: Text(
                                          conversation.title,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                      if (conversation.pinned) ...[
                                        const SizedBox(width: 5),
                                        const Icon(
                                          Icons.push_pin,
                                          size: 14,
                                          color: Colors.grey,
                                        ),
                                      ],
                                      if (!conversation.isDirect) ...[
                                        const SizedBox(width: 6),
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 5,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: const Color(0xFFFFE8CF),
                                            borderRadius: BorderRadius.circular(
                                              4,
                                            ),
                                          ),
                                          child: const Text(
                                            '群聊',
                                            style: TextStyle(
                                              fontSize: 10,
                                              color: Color(0xFFB76512),
                                            ),
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                  subtitle: Text(
                                    conversation.isActive
                                        ? ((conversation.lastMessageContent ??
                                                      '')
                                                  .isEmpty
                                              ? (conversation.isDirect
                                                    ? '私聊'
                                                    : '家庭群聊')
                                              : conversation
                                                    .lastMessageContent!)
                                        : '已解散，历史消息仍可查看',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  trailing: SizedBox(
                                    width: 58,
                                    child: Column(
                                      mainAxisAlignment:
                                          MainAxisAlignment.center,
                                      crossAxisAlignment:
                                          CrossAxisAlignment.end,
                                      children: [
                                        _ConversationTime(
                                          value: conversation.lastMessageAt,
                                        ),
                                      ],
                                    ),
                                  ),
                                  onTap: () async {
                                    _markReadLocally(conversation.id);
                                    await Navigator.of(context).push(
                                      MaterialPageRoute(
                                        builder: (_) => ChatDetailPage(
                                          conversation: conversation,
                                        ),
                                      ),
                                    );
                                    await _reload();
                                  },
                                ),
                              ),
                            ),
                          ],
                        );
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _TopSectionButton extends StatelessWidget {
  const _TopSectionButton({
    required this.text,
    required this.selected,
    required this.count,
    required this.onTap,
  });
  final String text;
  final bool selected;
  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    borderRadius: BorderRadius.circular(6),
    onTap: onTap,
    child: Stack(
      clipBehavior: Clip.none,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 5),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              AnimatedDefaultTextStyle(
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOutCubic,
                style: TextStyle(
                  fontSize: 20,
                  height: 1.15,
                  fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                  color: selected
                      ? const Color(0xFF202124)
                      : const Color(0xFF8A8F98),
                ),
                child: Text(text),
              ),
              const SizedBox(height: 5),
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOutCubic,
                width: selected ? 22 : 0,
                height: 3,
                decoration: BoxDecoration(
                  color: const Color(0xFFF28C28),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ],
          ),
        ),
        if (count > 0)
          Positioned(right: -3, top: -2, child: _UnreadBadge(count: count)),
      ],
    ),
  );
}

class _ChatListSkeleton extends StatelessWidget {
  const _ChatListSkeleton();

  @override
  Widget build(BuildContext context) => ListView.separated(
    padding: const EdgeInsets.all(12),
    itemCount: 7,
    separatorBuilder: (_, _) => const SizedBox(height: 8),
    itemBuilder: (_, _) => const DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.all(Radius.circular(8)),
      ),
      child: Padding(
        padding: EdgeInsets.all(14),
        child: Row(
          children: [
            SkeletonBox(
              width: 48,
              height: 48,
              borderRadius: BorderRadius.all(Radius.circular(24)),
            ),
            SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SkeletonBox(width: 104, height: 14),
                  SizedBox(height: 10),
                  SkeletonBox(width: double.infinity, height: 12),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.count, this.muted = false});
  final int count;
  final bool muted;

  @override
  Widget build(BuildContext context) => Container(
    constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
    padding: const EdgeInsets.symmetric(horizontal: 5),
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: muted ? Colors.grey : Colors.red,
      shape: BoxShape.circle,
    ),
    child: Text(
      count > 99 ? '99+' : '$count',
      style: const TextStyle(color: Colors.white, fontSize: 10),
    ),
  );
}

class _SwipeAction extends StatelessWidget {
  const _SwipeAction({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 72,
    height: double.infinity,
    child: Material(
      color: color,
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: Colors.white, size: 20),
            const SizedBox(height: 3),
            Text(
              label,
              style: const TextStyle(color: Colors.white, fontSize: 11),
            ),
          ],
        ),
      ),
    ),
  );
}

class ConversationSwipeTile extends StatefulWidget {
  const ConversationSwipeTile({
    super.key,
    required this.child,
    required this.pinned,
    required this.canDelete,
    required this.onPin,
    required this.onDelete,
    this.itemId,
    this.controller,
  });
  final Widget child;
  final bool pinned;
  final bool canDelete;
  final VoidCallback onPin;
  final VoidCallback onDelete;
  final int? itemId;
  final ConversationSwipeController? controller;

  @override
  State<ConversationSwipeTile> createState() => _ConversationSwipeTileState();
}

class _ConversationSwipeTileState extends State<ConversationSwipeTile> {
  double _offset = 0;

  @override
  void initState() {
    super.initState();
    widget.controller?.addListener(_syncWithController);
  }

  @override
  void didUpdateWidget(covariant ConversationSwipeTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.removeListener(_syncWithController);
      widget.controller?.addListener(_syncWithController);
    }
  }

  void _syncWithController() {
    if (_offset != 0 && widget.controller?.value != widget.itemId && mounted) {
      setState(() => _offset = 0);
    }
  }

  void _close() {
    if (_offset != 0) setState(() => _offset = 0);
    widget.controller?.close(widget.itemId);
  }

  @override
  void dispose() {
    widget.controller?.removeListener(_syncWithController);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TapRegion(
    onTapOutside: (_) => _close(),
    child: ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Stack(
        alignment: Alignment.centerRight,
        children: [
          Positioned.fill(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                _SwipeAction(
                  icon: widget.pinned
                      ? Icons.push_pin_outlined
                      : Icons.push_pin,
                  label: widget.pinned ? '取消置顶' : '置顶',
                  color: const Color(0xFFF2A34B),
                  onTap: () {
                    _close();
                    widget.onPin();
                  },
                ),
                _SwipeAction(
                  icon: Icons.delete_outline,
                  label: widget.canDelete ? '删除' : '不可删除',
                  color: widget.canDelete
                      ? const Color(0xFFE44B4B)
                      : Colors.grey,
                  onTap: () {
                    _close();
                    widget.onDelete();
                  },
                ),
              ],
            ),
          ),
          AnimatedContainer(
            key: widget.itemId == null
                ? null
                : ValueKey('swipe-card-${widget.itemId}'),
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOut,
            transform: Matrix4.translationValues(_offset, 0, 0),
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: _offset == 0 ? null : _close,
              onHorizontalDragStart: (_) {
                if (widget.itemId != null) {
                  widget.controller?.open(widget.itemId!);
                }
              },
              onHorizontalDragUpdate: (details) => setState(() {
                _offset = (_offset + details.delta.dx).clamp(-144, 0);
              }),
              onHorizontalDragEnd: (_) {
                final open = _offset < -48;
                setState(() => _offset = open ? -144 : 0);
                if (open && widget.itemId != null) {
                  widget.controller?.open(widget.itemId!);
                } else {
                  widget.controller?.close(widget.itemId);
                }
              },
              child: IgnorePointer(ignoring: _offset != 0, child: widget.child),
            ),
          ),
        ],
      ),
    ),
  );
}

class ConversationSwipeController extends ValueNotifier<int?> {
  ConversationSwipeController() : super(null);

  void open(int itemId) => value = itemId;

  void close([int? itemId]) {
    if (itemId == null || value == itemId) value = null;
  }
}

class _ConversationAvatar extends StatelessWidget {
  const _ConversationAvatar({required this.conversation});

  final ChatConversation conversation;

  @override
  Widget build(BuildContext context) {
    final imageUrl = conversation.imageUrl;
    final hasImage = imageUrl != null && imageUrl.isNotEmpty;

    return CircleAvatar(
      foregroundImage: hasImage
          ? NetworkImage(ApiConfig.resolveImageUrl(imageUrl))
          : null,
      onForegroundImageError: hasImage ? (_, _) {} : null,
      child: Icon(
        conversation.isDirect ? Icons.person_outline : Icons.groups_outlined,
      ),
    );
  }
}

class _ConversationTime extends StatelessWidget {
  const _ConversationTime({required this.value});

  final String? value;

  @override
  Widget build(BuildContext context) {
    return Text(
      formatChatListTime(value),
      style: Theme.of(
        context,
      ).textTheme.labelSmall?.copyWith(color: Colors.grey),
    );
  }
}
