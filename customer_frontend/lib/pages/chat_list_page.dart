import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../widgets/app_notice.dart';
import 'chat_detail_page.dart';

class ChatListPage extends StatefulWidget {
  const ChatListPage({super.key});

  @override
  State<ChatListPage> createState() => _ChatListPageState();
}

class _ChatListPageState extends State<ChatListPage>
    with WidgetsBindingObserver {
  late CustomerApi _api;
  // 直接使用本地状态变量驱动 UI，彻底抛弃 FutureBuilder
  List<ChatConversation>? _conversations;
  int _fetchId = 0; // 用于防止网络请求竞态覆盖
  bool _hasError = false;

  StreamSubscription<CustomerWsMessage>? _wsSubscription;
  StreamSubscription<bool>? _connectionSubscription;
  bool _initialized = false;
  final _searchController = TextEditingController();
  List<ChatUser> _searchResults = const [];
  Timer? _searchDebounce;
  Timer? _refreshTimer;
  bool _searching = false;
  final Set<int> _pinningIds = {};
  final Map<int, bool> _pinnedOverrides = {};
  final Set<int> _deletingIds = {};
  final Map<int, HiddenConversationOverride> _hiddenOverrides = {};
  final ValueNotifier<Object?> _openSwipeTile = ValueNotifier(null);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    WidgetsBinding.instance.addObserver(this);
    _api = CustomerApi(AppScope.of(context).api);

    // 首次进入加载数据
    _loadData();

    // 监听 WebSocket，随时静默获取最新数据
    _wsSubscription = CustomerWebSocketService.instance
        .streamForModule('CHAT')
        .listen((event) {
          final conversationId = int.tryParse(
            '${event.data['conversationId'] ?? event.data['id'] ?? ''}',
          );
          if (event.action == 'MESSAGE_CREATED' && conversationId != null) {
            _hiddenOverrides.remove(conversationId);
          }
          if (mounted) _loadData();
        });

    _connectionSubscription = CustomerWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) {
          if (mounted) _loadData();
        });
    _refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted) _loadData();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _loadData();
  }

  // 核心：无感刷新逻辑
  Future<void> _loadData() async {
    final id = ++_fetchId;
    try {
      final data = await _api.conversations();
      // 只有当前请求是最新的，才更新 UI
      if (mounted && id == _fetchId) {
        final serverIds = data.map((item) => item.id).toSet();
        final confirmedPinnedIds = <int>[];
        final confirmedHiddenIds = <int>[];
        final restoredByMessage = data
            .where((item) {
              final override = _hiddenOverrides[item.id];
              return override != null && override.hasNewMessage(item);
            })
            .map((item) => item.id)
            .toList();
        for (final conversationId in restoredByMessage) {
          _hiddenOverrides.remove(conversationId);
        }
        final merged = data
            .where((item) => !_hiddenOverrides.containsKey(item.id))
            .map((item) {
              final pinned = _pinnedOverrides[item.id];
              if (pinned == null) return item;
              if (!_pinningIds.contains(item.id) && item.pinned == pinned) {
                confirmedPinnedIds.add(item.id);
              }
              return item.withPreferences(pinned: pinned);
            })
            .toList();
        for (final conversationId in _hiddenOverrides.keys) {
          if (!_deletingIds.contains(conversationId) &&
              !serverIds.contains(conversationId)) {
            confirmedHiddenIds.add(conversationId);
          }
        }
        for (final conversationId in confirmedPinnedIds) {
          _pinnedOverrides.remove(conversationId);
        }
        for (final conversationId in confirmedHiddenIds) {
          _hiddenOverrides.remove(conversationId);
        }
        setState(() {
          _conversations = merged;
          _hasError = false;
        });
      }
    } catch (_) {
      if (mounted && id == _fetchId) {
        setState(() {
          if (_conversations == null) _hasError = true;
        });
      }
    }
  }

  // 本地预判已读，瞬间响应，随后即使服务端数据回来也会自动覆盖
  void _markReadLocally(int conversationId) {
    if (_conversations == null) return;
    setState(() {
      _conversations = _conversations!
          .map((c) => c.id == conversationId ? c.markedRead() : c)
          .toList();
    });
  }

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    if (value.trim().isEmpty) {
      setState(() {
        _searchResults = const [];
        _searching = false;
      });
      return;
    }
    setState(() {
      _searchResults = const [];
      _searching = true;
    });
    _searchDebounce = Timer(const Duration(milliseconds: 280), () async {
      if (!mounted) return;
      try {
        final users = await _api.searchChatCustomers(value);
        if (mounted && _searchController.text == value) {
          setState(() {
            _searchResults = users;
            _searching = false;
          });
        }
      } catch (error) {
        if (mounted && _searchController.text == value) {
          setState(() => _searching = false);
          AppNotice.show(context, _friendlyChatError(error));
        }
      }
    });
  }

  Future<void> _openSearchResult(ChatUser user) async {
    try {
      final conversation = await _api.startChatWithCustomer(user.id);
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ChatDetailPage(conversation: conversation),
        ),
      );
      _searchController.clear();
      setState(() => _searchResults = const []);
      _loadData();
    } catch (error) {
      if (mounted) AppNotice.show(context, _friendlyChatError(error));
    }
  }

  Future<void> _setPinned(ChatConversation item) async {
    if (_pinningIds.contains(item.id)) return;
    final pinned = !item.pinned;
    final before = _conversations;
    // 让操作前已经发出的列表请求失效。
    _fetchId++;
    _pinningIds.add(item.id);
    _pinnedOverrides[item.id] = pinned;
    if (before != null) {
      setState(() {
        _conversations = before
            .map(
              (conversation) => conversation.id == item.id
                  ? conversation.withPreferences(pinned: pinned)
                  : conversation,
            )
            .toList();
      });
    }
    try {
      final preferences = await _api.updateConversationPreferences(
        item.id,
        pinned: pinned,
      );
      _pinnedOverrides[item.id] = preferences.pinned;
      if (mounted) {
        setState(() {
          _conversations = _conversations
              ?.map(
                (conversation) => conversation.id == item.id
                    ? conversation.withPreferences(pinned: preferences.pinned)
                    : conversation,
              )
              .toList();
        });
      }
    } catch (error) {
      _pinnedOverrides.remove(item.id);
      if (mounted) {
        setState(() => _conversations = before);
        AppNotice.show(context, _friendlyChatError(error));
      }
    } finally {
      _pinningIds.remove(item.id);
    }
  }

  Future<void> _hideConversation(ChatConversation item) async {
    if (_deletingIds.contains(item.id)) return;
    final before = _conversations;
    _fetchId++;
    _deletingIds.add(item.id);
    _hiddenOverrides[item.id] = HiddenConversationOverride(
      messageVersion: item.messageVersion,
    );
    if (before != null) {
      setState(
        () => _conversations = before
            .where((conversation) => conversation.id != item.id)
            .toList(),
      );
    }
    try {
      final result = await _api.hideConversation(
        item.id,
        expectedMessageVersion: item.messageVersion,
      );
      if (!result.hidden) {
        _hiddenOverrides.remove(item.id);
        if (mounted &&
            _conversations?.any((entry) => entry.id == item.id) != true) {
          setState(() => _conversations = [...?_conversations, item]);
        }
      }
    } catch (error) {
      _hiddenOverrides.remove(item.id);
      if (mounted) {
        setState(() => _conversations = before);
        AppNotice.show(context, _friendlyChatError(error));
      }
    } finally {
      _deletingIds.remove(item.id);
      if (mounted) await _loadData();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    _searchDebounce?.cancel();
    _refreshTimer?.cancel();
    _searchController.dispose();
    _openSwipeTile.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF7F7F7),
    appBar: AppBar(
      title: const Text('聊天', style: TextStyle(fontWeight: FontWeight.bold)),
      backgroundColor: const Color(0xFFF7F7F7),
      foregroundColor: const Color(0xFF242424),
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
    ),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 10),
          child: TextField(
            controller: _searchController,
            onChanged: _onSearchChanged,
            decoration: InputDecoration(
              hintText: '搜索用户',
              prefixIcon: const Icon(Icons.search),
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
              suffixIcon: _searching
                  ? const Padding(
                      padding: EdgeInsets.all(14),
                      child: SizedBox.square(
                        dimension: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    )
                  : _searchController.text.trim().isNotEmpty
                  ? IconButton(
                      tooltip: '关闭搜索',
                      onPressed: () {
                        _searchController.clear();
                        _searchDebounce?.cancel();
                        setState(() {
                          _searchResults = const [];
                          _searching = false;
                        });
                      },
                      icon: const Icon(Icons.close),
                    )
                  : null,
            ),
          ),
        ),
        if (_searchResults.isNotEmpty)
          ConstrainedBox(
            constraints: const BoxConstraints(maxHeight: 220),
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: _searchResults.length,
              itemBuilder: (_, index) => Container(
                margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundImage:
                        _searchResults[index].avatarUrl?.isNotEmpty == true
                        ? NetworkImage(
                            resolveCustomerImageUrl(
                              _searchResults[index].avatarUrl!,
                            ),
                          )
                        : null,
                    child: _searchResults[index].avatarUrl?.isNotEmpty == true
                        ? null
                        : const Icon(Icons.person_outline),
                  ),
                  title: Text(_searchResults[index].nickname),
                  onTap: () => _openSearchResult(_searchResults[index]),
                ),
              ),
            ),
          ),
        if (shouldShowNoChatUser(
          query: _searchController.text,
          searching: _searching,
          resultCount: _searchResults.length,
        ))
          Expanded(
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 28,
                  vertical: 20,
                ),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text('没有该用户'),
              ),
            ),
          )
        else if (shouldShowConversationsForSearch(_searchController.text))
          Expanded(
            child: RefreshIndicator(onRefresh: _loadData, child: _buildBody()),
          )
        else
          const Spacer(),
      ],
    ),
  );

  Widget _buildBody() {
    // 1. 首次加载中
    if (_conversations == null && !_hasError) {
      return const Center(child: CircularProgressIndicator());
    }

    // 2. 加载失败（使用 ListView 确保依然可以下拉刷新）
    if (_hasError) {
      return ListView(
        children: [
          const SizedBox(height: 180),
          Center(
            child: TextButton(
              onPressed: _loadData,
              child: const Text('聊天加载失败，点击重试'),
            ),
          ),
        ],
      );
    }

    final conversations = _conversations!;

    // 3. 数据为空
    if (conversations.isEmpty) {
      return ListView(
        children: const [
          SizedBox(height: 180),
          Center(child: Text('暂无聊天')),
        ],
      );
    }

    // 4. 正常渲染列表（无感刷新：只要 _conversations 更新，ListView 瞬间重绘）
    final pinned = conversations.where((item) => item.pinned).toList()
      ..sort((a, b) => (b.pinnedAt ?? '').compareTo(a.pinnedAt ?? ''));
    final regular = conversations.where((item) => !item.pinned).toList();
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      children: [
        if (pinned.isNotEmpty) ...[
          const _GroupHeader('置顶会话'),
          for (final item in pinned) ...[
            _buildConversationTile(item),
            const SizedBox(height: 10),
          ],
        ],
        if (regular.isNotEmpty && pinned.isNotEmpty) const _GroupHeader('其他会话'),
        for (final item in regular) ...[
          _buildConversationTile(item),
          const SizedBox(height: 10),
        ],
      ],
    );
  }

  Widget _buildConversationTile(
    ChatConversation item,
  ) => _SwipeConversationTile(
    key: ValueKey(item.id),
    coordinator: _openSwipeTile,
    pinned: item.pinned,
    onPin: () => _setPinned(item),
    onDelete: () => _hideConversation(item),
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        leading: Stack(
          clipBehavior: Clip.none,
          children: [
            CircleAvatar(
              backgroundImage: item.imageUrl?.isNotEmpty == true
                  ? NetworkImage(resolveCustomerImageUrl(item.imageUrl!))
                  : null,
              child: item.imageUrl?.isNotEmpty == true
                  ? null
                  : Icon(item.isDirect ? Icons.person : Icons.groups),
            ),
            if (item.showUnreadBadge || item.showMutedBadge)
              Positioned(
                right: -7,
                top: -7,
                child: _UnreadBadge(
                  count: item.unreadCount,
                  muted: item.showMutedBadge,
                ),
              ),
          ],
        ),
        title: Row(
          children: [
            Flexible(
              child: Text(
                item.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (!item.isDirect) ...[
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFE8CF),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text(
                  '群聊',
                  style: TextStyle(fontSize: 10, color: Color(0xFFB76512)),
                ),
              ),
            ],
          ],
        ),
        subtitle: Text(
          item.lastMessageContent?.isNotEmpty == true
              ? item.lastMessageContent!
              : (item.isActive ? (item.isDirect ? '私聊' : '家庭群聊') : '已解散'),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: _ConversationTime(value: item.lastMessageAt),
        onTap: () async {
          _markReadLocally(item.id);
          await Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ChatDetailPage(conversation: item),
            ),
          );
          if (mounted) _loadData(); // 从详情页后退回来时主动获取最新数据
        },
      ),
    ),
  );
}

// ---------------- 以下辅助组件保留原有实现即可 ----------------

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
      color: muted ? const Color(0xFFB9BEC5) : Colors.red,
      shape: BoxShape.circle,
    ),
    child: Text(
      count > 99 ? '99+' : '$count',
      style: const TextStyle(color: Colors.white, fontSize: 10),
    ),
  );
}

class _GroupHeader extends StatelessWidget {
  const _GroupHeader(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 4, 4, 8),
    child: Text(
      text,
      style: Theme.of(context).textTheme.labelLarge?.copyWith(
        color: const Color(0xFF777777),
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}

class _SwipeConversationTile extends StatefulWidget {
  const _SwipeConversationTile({
    super.key,
    required this.child,
    required this.pinned,
    required this.onPin,
    required this.onDelete,
    required this.coordinator,
  });

  final Widget child;
  final bool pinned;
  final VoidCallback onPin;
  final VoidCallback onDelete;
  final ValueNotifier<Object?> coordinator;

  @override
  State<_SwipeConversationTile> createState() => _SwipeConversationTileState();
}

class _SwipeConversationTileState extends State<_SwipeConversationTile> {
  final Object _token = Object();
  double _offset = 0;

  @override
  void initState() {
    super.initState();
    widget.coordinator.addListener(_handleOpenTileChanged);
  }

  @override
  void didUpdateWidget(covariant _SwipeConversationTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.coordinator == widget.coordinator) return;
    oldWidget.coordinator.removeListener(_handleOpenTileChanged);
    widget.coordinator.addListener(_handleOpenTileChanged);
  }

  void _handleOpenTileChanged() {
    if (widget.coordinator.value != _token && _offset != 0 && mounted) {
      setState(() => _offset = 0);
    }
  }

  void _close() {
    if (_offset != 0) setState(() => _offset = 0);
    if (widget.coordinator.value == _token) widget.coordinator.value = null;
  }

  @override
  void dispose() {
    widget.coordinator.removeListener(_handleOpenTileChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TapRegion(
    onTapOutside: (_) => _close(),
    child: ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Stack(
        alignment: Alignment.centerRight,
        children: [
          Positioned.fill(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                _SwipeAction(
                  color: const Color(0xFFF2A34B),
                  icon: widget.pinned
                      ? Icons.push_pin_outlined
                      : Icons.push_pin,
                  label: widget.pinned ? '取消置顶' : '置顶',
                  onTap: () {
                    _close();
                    widget.onPin();
                  },
                ),
                _SwipeAction(
                  color: const Color(0xFFE44B4B),
                  icon: Icons.delete_outline,
                  label: '删除',
                  onTap: () {
                    _close();
                    widget.onDelete();
                  },
                ),
              ],
            ),
          ),
          AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOut,
            transform: Matrix4.translationValues(_offset, 0, 0),
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: _offset == 0 ? null : _close,
              onHorizontalDragStart: (_) {
                if (widget.coordinator.value != _token) {
                  widget.coordinator.value = _token;
                }
              },
              onHorizontalDragUpdate: (details) {
                if (widget.coordinator.value != _token) {
                  widget.coordinator.value = _token;
                }
                setState(
                  () => _offset = (_offset + details.delta.dx).clamp(-144, 0),
                );
              },
              onHorizontalDragEnd: (_) {
                setState(() => _offset = _offset < -48 ? -144 : 0);
              },
              child: widget.child,
            ),
          ),
        ],
      ),
    ),
  );
}

class HiddenConversationOverride {
  const HiddenConversationOverride({required this.messageVersion});

  final int messageVersion;

  bool hasNewMessage(ChatConversation conversation) {
    return conversation.messageVersion > messageVersion;
  }
}

class _SwipeAction extends StatelessWidget {
  const _SwipeAction({
    required this.color,
    required this.icon,
    required this.label,
    required this.onTap,
  });
  final Color color;
  final IconData icon;
  final String label;
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
            Icon(icon, color: Colors.white, size: 21),
            const SizedBox(height: 3),
            Text(
              label,
              style: const TextStyle(color: Colors.white, fontSize: 12),
            ),
          ],
        ),
      ),
    ),
  );
}

class _ConversationTime extends StatelessWidget {
  const _ConversationTime({required this.value});
  final String? value;

  @override
  Widget build(BuildContext context) => Text(
    formatChatListTime(value),
    style: Theme.of(context).textTheme.labelSmall?.copyWith(color: Colors.grey),
  );
}

String _friendlyChatError(Object error) {
  if (error is ApiException) return error.message;
  return '网络连接失败，请检查网络后重试';
}

bool shouldShowConversationsForSearch(String query) => query.trim().isEmpty;

bool shouldShowNoChatUser({
  required String query,
  required bool searching,
  required int resultCount,
}) => query.trim().isNotEmpty && !searching && resultCount == 0;
