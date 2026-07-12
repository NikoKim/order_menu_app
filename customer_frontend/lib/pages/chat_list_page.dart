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

class _ChatListPageState extends State<ChatListPage> {
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
  bool _searching = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    _api = CustomerApi(AppScope.of(context).api);

    // 首次进入加载数据
    _loadData();

    // 监听 WebSocket，随时静默获取最新数据
    _wsSubscription = CustomerWebSocketService.instance
        .streamForModule('CHAT')
        .listen((_) {
          if (mounted) _loadData();
        });

    _connectionSubscription = CustomerWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) {
          if (mounted) _loadData();
        });
  }

  // 核心：无感刷新逻辑
  Future<void> _loadData() async {
    final id = ++_fetchId;
    try {
      final data = await _api.conversations();
      // 只有当前请求是最新的，才更新 UI
      if (mounted && id == _fetchId) {
        setState(() {
          _conversations = data;
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

  @override
  void dispose() {
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    _searchDebounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(
      title: const Text('聊天'),
      backgroundColor: const Color(0xFFF5F5F5),
      surfaceTintColor: Colors.transparent,
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
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      itemCount: conversations.length,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final item = conversations[index];
        return Material(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          clipBehavior: Clip.antiAlias,
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 4,
            ),
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
                if (item.showUnreadBadge)
                  Positioned(
                    right: -7,
                    top: -7,
                    child: _UnreadBadge(count: item.unreadCount),
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
                    padding: const EdgeInsets.symmetric(
                      horizontal: 5,
                      vertical: 2,
                    ),
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
        );
      },
    );
  }
}

// ---------------- 以下辅助组件保留原有实现即可 ----------------

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) => Container(
    constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
    padding: const EdgeInsets.symmetric(horizontal: 5),
    alignment: Alignment.center,
    decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
    child: Text(
      count > 99 ? '99+' : '$count',
      style: const TextStyle(color: Colors.white, fontSize: 10),
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
