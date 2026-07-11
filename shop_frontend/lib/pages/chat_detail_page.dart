import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';

class ChatDetailPage extends StatefulWidget {
  const ChatDetailPage({super.key, required this.conversation});

  final ChatConversation conversation;

  @override
  State<ChatDetailPage> createState() => _ChatDetailPageState();
}

class _ChatDetailPageState extends State<ChatDetailPage> {
  late Future<_ChatDetailData> _future;
  final _input = TextEditingController();
  StreamSubscription? _subscription;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
    _subscription = ShopWebSocketService.instance
        .streamForModule('CHAT')
        .listen((event) {
          final id = int.tryParse(
            '${event.data['conversationId'] ?? event.data['id'] ?? ''}',
          );
          if (id == widget.conversation.id) _reload();
        });
  }

  Future<_ChatDetailData> _load() async {
    final messagesFuture = shopApi.fetchChatMessages(widget.conversation.id);
    final currentUserFuture = shopApi.fetchCurrentUser();
    final shopFuture = shopApi.fetchMyShop();
    final groupFuture = widget.conversation.isDirect
        ? Future<FamilyGroup?>.value(null)
        : shopApi
              .fetchFamilyGroup(widget.conversation.id)
              .then<FamilyGroup?>((value) => value)
              .catchError((_) => null);
    return _ChatDetailData(
      messages: await messagesFuture,
      currentUser: await currentUserFuture,
      shop: await shopFuture,
      group: await groupFuture,
    );
  }

  Future<void> _reload() async {
    final future = _load();
    if (mounted) {
      setState(() {
        _future = future;
      });
    }
    await future;
  }

  Future<void> _send(bool active) async {
    final content = _input.text.trim();
    if (content.isEmpty || _sending || !active) return;
    setState(() => _sending = true);
    try {
      await shopApi.sendChatMessage(widget.conversation.id, content);
      _input.clear();
      await _reload();
    } catch (error) {
      if (mounted) {
        final message = error is ApiException
            ? error.message
            : '网络连接失败，请检查网络后重试';
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(message)));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.conversation.title)),
    body: FutureBuilder<_ChatDetailData>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done)
          return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError) {
          return Center(
            child: TextButton(
              onPressed: _reload,
              child: const Text('消息加载失败，点击重试'),
            ),
          );
        }
        final data = snapshot.data!;
        final conversation = data.messages.conversation;
        return Column(
          children: [
            if (!conversation.isActive)
              const MaterialBanner(
                content: Text('该群聊已解散，历史消息仍可查看。'),
                actions: [],
              ),
            Expanded(
              child: _MessageList(
                messages: data.messages.messages,
                currentUser: data.currentUser,
                conversation: conversation,
                group: data.group,
                shop: data.shop,
              ),
            ),
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _input,
                        enabled: conversation.isActive,
                        maxLines: 4,
                        minLines: 1,
                        decoration: const InputDecoration(hintText: '输入消息'),
                        onSubmitted: (_) => _send(conversation.isActive),
                      ),
                    ),
                    IconButton(
                      tooltip: '发送',
                      onPressed: _sending
                          ? null
                          : () => _send(conversation.isActive),
                      icon: const Icon(Icons.send),
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    ),
  );
}

class _ChatDetailData {
  const _ChatDetailData({
    required this.messages,
    required this.currentUser,
    required this.shop,
    required this.group,
  });

  final ChatMessages messages;
  final CurrentUser currentUser;
  final ShopInfo shop;
  final FamilyGroup? group;
}

class _MessageList extends StatelessWidget {
  const _MessageList({
    required this.messages,
    required this.currentUser,
    required this.conversation,
    required this.group,
    required this.shop,
  });

  final List<ChatMessage> messages;
  final CurrentUser currentUser;
  final ChatConversation conversation;
  final FamilyGroup? group;
  final ShopInfo shop;

  @override
  Widget build(BuildContext context) {
    if (messages.isEmpty) return const Center(child: Text('开始聊天吧'));
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(12, 16, 12, 8),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        final message = messages[index];
        final previous = index == 0 ? null : messages[index - 1];
        final sentAt = message.sentAt;
        final previousAt = previous?.sentAt;
        final isMine = message.senderUserId == currentUser.id;
        final sender = _senderFor(message, isMine);
        return Column(
          children: [
            if (sentAt != null && shouldShowChatTimestamp(sentAt, previousAt))
              _Timestamp(value: sentAt),
            _MessageBubble(
              message: message,
              isMine: isMine,
              senderName: conversation.isDirect ? null : sender.name,
              avatarUrl: sender.avatarUrl,
            ),
          ],
        );
      },
    );
  }

  _Sender _senderFor(ChatMessage message, bool isMine) {
    if (isMine) return _Sender(shop.name, shop.coverImageUrl);
    if ((message.senderNickname ?? '').isNotEmpty) {
      return _Sender(message.senderNickname!, message.senderAvatarUrl);
    }
    final member = group?.members
        .where((item) => item.userId == message.senderUserId)
        .firstOrNull;
    if (member != null) return _Sender(member.nickname, member.avatarUrl);
    if (conversation.isDirect)
      return _Sender(
        conversation.directUserNickname ?? '对方',
        conversation.directUserAvatarUrl,
      );
    return _Sender('家庭成员', null);
  }
}

class _Sender {
  const _Sender(this.name, this.avatarUrl);

  final String name;
  final String? avatarUrl;
}

class _Timestamp extends StatelessWidget {
  const _Timestamp({required this.value});

  final DateTime value;

  @override
  Widget build(BuildContext context) {
    final local = value.toLocal();
    final today = DateTime.now();
    final prefix =
        local.year == today.year &&
            local.month == today.month &&
            local.day == today.day
        ? ''
        : '${local.month}/${local.day} ';
    final time = TimeOfDay.fromDateTime(local).format(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Text(
        '$prefix$time',
        style: Theme.of(
          context,
        ).textTheme.labelSmall?.copyWith(color: Colors.grey),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({
    required this.message,
    required this.isMine,
    required this.senderName,
    required this.avatarUrl,
  });

  final ChatMessage message;
  final bool isMine;
  final String? senderName;
  final String? avatarUrl;

  @override
  Widget build(BuildContext context) {
    final avatar = CircleAvatar(
      radius: 18,
      foregroundImage: avatarUrl == null || avatarUrl!.isEmpty
          ? null
          : NetworkImage(ApiConfig.resolveImageUrl(avatarUrl!)),
      onForegroundImageError: (_, _) {},
      child: const Icon(Icons.person_outline, size: 19),
    );
    final bubble = ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 280),
      child: Container(
        padding: message.isDishShare
            ? EdgeInsets.zero
            : const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
        decoration: BoxDecoration(
          color: isMine ? const Color(0xFFFFD6AA) : Colors.white,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: isMine ? const Color(0xFFFFC078) : const Color(0xFFE8E1DA),
          ),
        ),
        child: message.isDishShare
            ? _DishShareCard(message: message)
            : Text(message.content),
      ),
    );
    final content = Column(
      crossAxisAlignment: isMine
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        if (senderName != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 3),
            child: Text(
              senderName!,
              style: Theme.of(
                context,
              ).textTheme.labelSmall?.copyWith(color: Colors.grey),
            ),
          ),
        bubble,
      ],
    );
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: isMine
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        children: isMine
            ? [Flexible(child: content), const SizedBox(width: 8), avatar]
            : [avatar, const SizedBox(width: 8), Flexible(child: content)],
      ),
    );
  }
}

class _DishShareCard extends StatelessWidget {
  const _DishShareCard({required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 240,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: const BorderRadius.vertical(top: Radius.circular(7)),
          child: SizedBox(
            height: 128,
            width: double.infinity,
            child: message.dishImageUrl == null || message.dishImageUrl!.isEmpty
                ? const ColoredBox(
                    color: Color(0xFFFFF1E1),
                    child: Center(child: Icon(Icons.restaurant_menu_outlined)),
                  )
                : Image.network(
                    ApiConfig.resolveImageUrl(message.dishImageUrl!),
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => const ColoredBox(
                      color: Color(0xFFFFF1E1),
                      child: Center(
                        child: Icon(Icons.restaurant_menu_outlined),
                      ),
                    ),
                  ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                message.dishName ?? '餐品分享',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              if (message.dishPrice != null) ...[
                const SizedBox(height: 4),
                Text(
                  '¥${message.dishPrice!.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: Color(0xFFF28C28),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
              if ((message.dishDescription ?? '').isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  message.dishDescription!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: Colors.grey[700]),
                ),
              ],
              if (message.dishSalesCount != null) ...[
                const SizedBox(height: 6),
                Text(
                  '已售 ${message.dishSalesCount}',
                  style: Theme.of(
                    context,
                  ).textTheme.labelSmall?.copyWith(color: Colors.grey),
                ),
              ],
            ],
          ),
        ),
      ],
    ),
  );
}
