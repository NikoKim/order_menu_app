import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
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
  late Future<List<ChatConversation>> _conversationsFuture;
  StreamSubscription<CustomerWsMessage>? _wsSubscription;
  bool _initialized = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    _api = CustomerApi(AppScope.of(context).api);
    _conversationsFuture = _api.conversations();
    _wsSubscription = CustomerWebSocketService.instance
        .streamForModule('CHAT')
        .listen((_) {
          if (mounted) _reload();
        });
  }

  void _reload() => setState(() => _conversationsFuture = _api.conversations());

  Future<void> _startCustomerChat() async {
    final conversation = await showModalBottomSheet<ChatConversation>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _CustomerSearchSheet(api: _api),
      /*builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheetState) {
          Future<void> search() async {
            setSheetState(() {
              searching = true;
              errorText = null;
            });
            try {
              final result = await _api.searchChatCustomers(controller.text);
              if (!sheetContext.mounted) return;
              setSheetState(() {
                users = result;
                searched = true;
                searching = false;
              });
            } catch (error) {
              if (!sheetContext.mounted) return;
              setSheetState(() {
                errorText = _friendlyChatError(error);
                searched = true;
                searching = false;
              });
            }
          }

          return Padding(
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
                    controller: controller,
                    autofocus: true,
                    decoration: InputDecoration(
                      hintText: '搜索顾客昵称',
                      suffixIcon: IconButton(
                        tooltip: '搜索',
                        icon: const Icon(Icons.search),
                        onPressed: searching ? null : search,
                      ),
                    ),
                    onSubmitted: (_) {
                      if (!searching) search();
                    },
                  ),
                  Expanded(
                    child: searching
                        ? const Center(child: CircularProgressIndicator())
                        : errorText != null
                        ? Center(child: Text(errorText!))
                        : searched && users.isEmpty
                        ? const Center(child: Text('当前用户不存在'))
                        : ListView.builder(
                            itemCount: users.length,
                            itemBuilder: (_, index) => ListTile(
                              leading: const CircleAvatar(
                                child: Icon(Icons.person),
                              ),
                              title: Text(users[index].nickname),
                              onTap: () async {
                                try {
                                  final conversation = await _api
                                      .startChatWithCustomer(users[index].id);
                                  if (!sheetContext.mounted) return;
                                  Navigator.of(sheetContext).pop(conversation);
                                } catch (error) {
                                  if (mounted) {
                                    ScaffoldMessenger.of(
                                      this.context,
                                    ).showSnackBar(
                                      SnackBar(
                                        content: Text(
                                          _friendlyChatError(error),
                                        ),
                                      ),
                                    );
                                  }
                                }
                              },
                            ),
                          ),
                  ),
                ],
              ),
            ),
          );
        },
      ),*/
    );
    if (conversation != null && mounted) {
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ChatDetailPage(conversation: conversation),
        ),
      );
      if (mounted) _reload();
    }
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
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
      onRefresh: () async => _reload(),
      child: FutureBuilder<List<ChatConversation>>(
        future: _conversationsFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: TextButton(
                onPressed: _reload,
                child: const Text('聊天加载失败，点击重试'),
              ),
            );
          }
          final conversations = snapshot.data ?? const [];
          if (conversations.isEmpty) {
            return ListView(
              children: const [
                SizedBox(height: 180),
                Center(child: Text('暂无聊天')),
              ],
            );
          }
          return ListView.separated(
            itemCount: conversations.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final item = conversations[index];
              return ListTile(
                leading: CircleAvatar(
                  backgroundImage: item.imageUrl?.isNotEmpty == true
                      ? NetworkImage(resolveCustomerImageUrl(item.imageUrl!))
                      : null,
                  child: item.imageUrl?.isNotEmpty == true
                      ? null
                      : Icon(item.isDirect ? Icons.person : Icons.groups),
                ),
                title: Text(item.title),
                subtitle: Text(
                  item.lastMessageContent?.isNotEmpty == true
                      ? item.lastMessageContent!
                      : (item.isActive
                            ? (item.isDirect ? '私聊' : '家庭群聊')
                            : '已解散'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: item.showUnreadBadge
                    ? Badge(
                        label: Text(
                          item.unreadCount > 99 ? '99+' : '${item.unreadCount}',
                        ),
                        child: const Icon(Icons.chevron_right),
                      )
                    : const Icon(Icons.chevron_right),
                onTap: () async {
                  await Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => ChatDetailPage(conversation: item),
                    ),
                  );
                  if (mounted) _reload();
                },
              );
            },
          );
        },
      ),
    ),
  );
}

String _friendlyChatError(Object error) {
  if (error is ApiException) return error.message;
  return '网络连接失败，请检查网络后重试';
}
