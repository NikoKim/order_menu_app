import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import 'chat_detail_page.dart';
import 'message_center_page.dart';

class ChatListPage extends StatefulWidget {
  const ChatListPage({super.key});

  @override
  State<ChatListPage> createState() => _ChatListPageState();
}

class _ChatListPageState extends State<ChatListPage>
    with WidgetsBindingObserver {
  late Future<List<ChatConversation>> _future;
  StreamSubscription? _chatSubscription;
  StreamSubscription? _notificationSubscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _future = shopApi.fetchConversations();
    _chatSubscription = ShopWebSocketService.instance
        .streamForModule('CHAT')
        .listen((_) => _reload());
    _notificationSubscription = ShopWebSocketService.instance
        .streamForModule('NOTIFICATION')
        .where(
          (event) =>
              event.action == 'CREATED' || event.action == 'READ_CHANGED',
        )
        .listen((_) => _reload());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _reload();
    }
  }

  Future<void> _reload() async {
    final future = shopApi.fetchConversations();
    if (mounted) {
      setState(() {
        _future = future;
      });
    }
    await future;
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _chatSubscription?.cancel();
    _notificationSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('聊天'),
      actions: [
        IconButton(
          tooltip: '消息通知',
          icon: const Icon(Icons.notifications_outlined),
          onPressed: () => Navigator.of(
            context,
          ).push(MaterialPageRoute(builder: (_) => const MessageCenterPage())),
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: _reload,
      child: FutureBuilder<List<ChatConversation>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done)
            return const Center(child: CircularProgressIndicator());
          if (snapshot.hasError) {
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
          final conversations = snapshot.data ?? const <ChatConversation>[];
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
            separatorBuilder: (_, _) => const Divider(height: 1, indent: 76),
            itemBuilder: (context, index) {
              final conversation = conversations[index];
              return ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 4,
                ),
                leading: _ConversationAvatar(conversation: conversation),
                title: Text(
                  conversation.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: Text(
                  conversation.isActive
                      ? ((conversation.lastMessageContent ?? '').isEmpty
                            ? (conversation.isDirect ? '私聊' : '家庭群聊')
                            : conversation.lastMessageContent!)
                      : '已解散，历史消息仍可查看',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    _ConversationTime(value: conversation.lastMessageAt),
                    if (conversation.shouldShowUnreadBadge) ...[
                      const SizedBox(height: 5),
                      _UnreadBadge(count: conversation.unreadCount),
                    ],
                  ],
                ),
                onTap: () async {
                  await Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) =>
                          ChatDetailPage(conversation: conversation),
                    ),
                  );
                  await _reload();
                },
              );
            },
          );
        },
      ),
    ),
    floatingActionButton: FloatingActionButton(
      tooltip: '发起私聊',
      onPressed: () => ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('可从顾客订单或家庭群成员发起私聊'))),
      child: const Icon(Icons.add_comment_outlined),
    ),
  );
}

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

class _ConversationAvatar extends StatelessWidget {
  const _ConversationAvatar({required this.conversation});

  final ChatConversation conversation;

  @override
  Widget build(BuildContext context) {
    final imageUrl = conversation.imageUrl;
    return CircleAvatar(
      foregroundImage: imageUrl == null || imageUrl.isEmpty
          ? null
          : NetworkImage(ApiConfig.resolveImageUrl(imageUrl)),
      onForegroundImageError: (_, _) {},
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
    final time = value == null ? null : DateTime.tryParse(value!);
    if (time == null) return const SizedBox(width: 24);
    final local = time.toLocal();
    final today = DateTime.now();
    final text =
        local.year == today.year &&
            local.month == today.month &&
            local.day == today.day
        ? TimeOfDay.fromDateTime(local).format(context)
        : '${local.month}/${local.day}';
    return Text(
      text,
      style: Theme.of(
        context,
      ).textTheme.labelSmall?.copyWith(color: Colors.grey),
    );
  }
}
