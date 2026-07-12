import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../services/latest_request.dart';
import 'chat_detail_page.dart';

class ChatListPage extends StatefulWidget {
  const ChatListPage({super.key});

  @override
  State<ChatListPage> createState() => _ChatListPageState();
}

class _CustomerSearchSheet extends StatefulWidget {
  const _CustomerSearchSheet({required this.api});
  final CustomerApi api;

  @override
  State<_CustomerSearchSheet> createState() => _CustomerSearchSheetState();
}

class _CustomerSearchSheetState extends State<_CustomerSearchSheet> {
  final _controller = TextEditingController();
  List<ChatUser> _users = const [];
  bool _searched = false;
  bool _searching = false;
  String? _error;

  Future<void> _search() async {
    if (_searching) return;
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final result = await widget.api.searchChatCustomers(_controller.text);
      if (!mounted) return;
      setState(() {
        _users = result;
        _searched = true;
        _searching = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = _friendlyChatError(error);
        _searched = true;
        _searching = false;
      });
    }
  }

  Future<void> _open(ChatUser user) async {
    try {
      final conversation = await widget.api.startChatWithCustomer(user.id);
      if (mounted) Navigator.of(context).pop(conversation);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(_friendlyChatError(error))));
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(
      bottom: MediaQuery.viewInsetsOf(context).bottom,
      left: 16,
      right: 16,
      top: 16,
    ),
    child: SizedBox(
      height: 420,
      child: Column(
        children: [
          TextField(
            controller: _controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '搜索顾客昵称',
              suffixIcon: IconButton(
                tooltip: '搜索',
                icon: const Icon(Icons.search),
                onPressed: _searching ? null : _search,
              ),
            ),
            onSubmitted: (_) => _search(),
          ),
          Expanded(
            child: _searching
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                ? Center(child: Text(_error!))
                : _searched && _users.isEmpty
                ? const Center(child: Text('当前用户不存在'))
                : ListView.builder(
                    itemCount: _users.length,
                    itemBuilder: (_, index) => ListTile(
                      leading: const CircleAvatar(child: Icon(Icons.person)),
                      title: Text(_users[index].nickname),
                      onTap: () => _open(_users[index]),
                    ),
                  ),
          ),
        ],
      ),
    ),
  );
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

  Future<void> _startCustomerChat() async {
    final conversation = await showModalBottomSheet<ChatConversation>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _CustomerSearchSheet(api: _api),
    );
    if (conversation != null && mounted) {
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ChatDetailPage(conversation: conversation),
        ),
      );
      if (mounted) _loadData(); // 从聊天详情返回时刷新
    }
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('聊天')),
    floatingActionButton: FloatingActionButton(
      tooltip: '发起私聊',
      onPressed: _startCustomerChat,
      child: const Icon(Icons.person_search),
    ),
    body: RefreshIndicator(
      onRefresh: _loadData, // 下拉刷新直接绑定异步加载方法
      child: _buildBody(),
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
      itemCount: conversations.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final item = conversations[index];
        return ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 4,
          ),
          leading: CircleAvatar(
            backgroundImage: item.imageUrl?.isNotEmpty == true
                ? NetworkImage(resolveCustomerImageUrl(item.imageUrl!))
                : null,
            child: item.imageUrl?.isNotEmpty == true
                ? null
                : Icon(item.isDirect ? Icons.person : Icons.groups),
          ),
          title: Text(
            item.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            item.lastMessageContent?.isNotEmpty == true
                ? item.lastMessageContent!
                : (item.isActive
                ? (item.isDirect ? '私聊' : '家庭群聊')
                : '已解散'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: SizedBox(
            width: 58,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                _ConversationTime(value: item.lastMessageAt),
                if (item.showUnreadBadge) ...[
                  const SizedBox(height: 5),
                  _UnreadBadge(count: item.unreadCount),
                ],
              ],
            ),
          ),
          onTap: () async {
            _markReadLocally(item.id);
            await Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => ChatDetailPage(conversation: item),
              ),
            );
            if (mounted) _loadData(); // 从详情页后退回来时主动获取最新数据
          },
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