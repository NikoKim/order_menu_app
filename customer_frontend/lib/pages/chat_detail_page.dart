import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../services/latest_request.dart';
import 'family_group_info_page.dart';

class ChatDetailPage extends StatefulWidget {
  const ChatDetailPage({super.key, required this.conversation});
  final ChatConversation conversation;
  @override
  State<ChatDetailPage> createState() => _ChatDetailPageState();
}

class _ChatDetailPageState extends State<ChatDetailPage> {
  late final CustomerApi _api;
  late Future<ChatMessages> _messagesFuture;
  final _input = TextEditingController();
  final _scrollController = ScrollController();
  final _messageRequests = LatestRequest();
  StreamSubscription<CustomerWsMessage>? _wsSubscription;
  StreamSubscription<bool>? _connectionSubscription;
  bool _sending = false;
  bool _initialized = false;
  ChatMessages? _cachedMessages;
  int _renderedMessageCount = -1;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    _api = CustomerApi(AppScope.of(context).api);
    _messagesFuture = _loadMessages();
    _wsSubscription ??= CustomerWebSocketService.instance
        .streamForModule('CHAT')
        .listen((event) {
          final id = int.tryParse(
            '${event.data['conversationId'] ?? event.data['id'] ?? ''}',
          );
          if (event.action == 'MESSAGE_CREATED' &&
              id == widget.conversation.id &&
              mounted) {
            _reload();
          }
        });
    _connectionSubscription ??= CustomerWebSocketService
        .instance
        .connectionStream
        .where((connected) => connected)
        .listen((_) {
          if (mounted) _reload();
        });
  }

  Future<ChatMessages> _loadMessages() async {
    final generation = _messageRequests.begin();
    final result = await _api.chatMessages(widget.conversation.id);
    if (_messageRequests.isLatest(generation)) {
      _cachedMessages = result;
      return result;
    }
    return _cachedMessages ?? result;
  }

  void _reload() {
    final future = _loadMessages().catchError((Object error) {
      final cached = _cachedMessages;
      if (cached != null) return cached;
      throw error;
    });
    setState(() {
      _messagesFuture = future;
    });
  }

  Future<void> _send() async {
    final content = _input.text.trim();
    if (content.isEmpty || _sending || !widget.conversation.isActive) return;
    setState(() => _sending = true);
    try {
      final sent = await _api.sendChatMessage(widget.conversation.id, content);
      _input.clear();
      final cached = _cachedMessages;
      if (cached != null && mounted) {
        _messageRequests.begin();
        final updated = ChatMessages(
          conversation: cached.conversation,
          messages: [...cached.messages, sent],
        );
        _cachedMessages = updated;
        setState(() => _messagesFuture = Future.value(updated));
      } else {
        _reload();
      }
    } catch (error) {
      // The server may have committed the message even if the response was
      // lost. Reconcile once before reporting a send failure.
      final currentUserId = AppScope.of(context).auth.user?.id;
      try {
        final recovered = await _api.chatMessages(widget.conversation.id);
        final delivered = recovered.messages.any(
          (message) =>
              message.senderUserId == currentUserId &&
              message.content == content,
        );
        if (delivered) {
          _input.clear();
          _messageRequests.begin();
          _cachedMessages = recovered;
          if (mounted) {
            setState(() {
              _messagesFuture = Future.value(recovered);
            });
          }
          return;
        }
      } catch (_) {}
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_friendlyError(error))));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    _input.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scheduleScrollToBottom({required bool animate}) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollController.hasClients) return;
      final target = _scrollController.position.maxScrollExtent;
      if (animate) {
        _scrollController.animateTo(
          target,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOut,
        );
      } else {
        _scrollController.jumpTo(target);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final user = AppScope.of(context).auth.user;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.conversation.title),
        actions: [
          if (!widget.conversation.isDirect)
            IconButton(
              tooltip: '群聊信息',
              icon: const Icon(Icons.info_outline),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => FamilyGroupInfoPage(
                    conversationId: widget.conversation.id,
                  ),
                ),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          if (!widget.conversation.isActive)
            const MaterialBanner(
              content: Text('该群聊已解散，历史消息仍可查看。'),
              actions: <Widget>[],
            ),
          Expanded(
            child: FutureBuilder<ChatMessages>(
              future: _messagesFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done &&
                    _cachedMessages == null) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError && _cachedMessages == null) {
                  return Center(
                    child: TextButton(
                      onPressed: _reload,
                      child: const Text('消息加载失败，点击重试'),
                    ),
                  );
                }
                final messages = (snapshot.data ?? _cachedMessages)!.messages;
                if (_renderedMessageCount != messages.length) {
                  final animate = _renderedMessageCount >= 0;
                  _renderedMessageCount = messages.length;
                  _scheduleScrollToBottom(animate: animate);
                }
                if (messages.isEmpty) return const Center(child: Text('开始聊天吧'));
                return ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.all(16),
                  itemCount: messages.length,
                  itemBuilder: (context, index) {
                    final messageIndex = index;
                    final message = messages[messageIndex];
                    final mine = message.senderUserId == user?.id;
                    final timestamp = DateTime.tryParse(message.createdAt);
                    final previous = messageIndex == 0
                        ? null
                        : DateTime.tryParse(
                            messages[messageIndex - 1].createdAt,
                          );
                    return Column(
                      children: [
                        if (timestamp != null &&
                            shouldShowChatTimestamp(previous, timestamp))
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12, top: 4),
                            child: Text(
                              _formatTime(timestamp),
                              style: const TextStyle(
                                fontSize: 12,
                                color: Color(0xFF8A8A8A),
                              ),
                            ),
                          ),
                        ChatMessageRow(
                          message: message,
                          mine: mine,
                          groupChat: !widget.conversation.isDirect,
                          fallbackAvatarUrl: mine
                              ? user?.avatarUrl
                              : widget.conversation.directUserAvatarUrl,
                          fallbackName: mine
                              ? user?.nickname
                              : widget.conversation.directUserNickname,
                        ),
                      ],
                    );
                  },
                );
              },
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _input,
                      enabled: widget.conversation.isActive,
                      minLines: 1,
                      maxLines: 4,
                      decoration: const InputDecoration(hintText: '输入消息'),
                    ),
                  ),
                  IconButton(
                    tooltip: '发送',
                    onPressed: _sending ? null : _send,
                    icon: const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _friendlyError(Object error) {
  if (error is ApiException) return error.message;
  return '网络连接失败，请检查网络后重试';
}

String _formatTime(DateTime value) {
  final local = value.toLocal();
  String two(int number) => number.toString().padLeft(2, '0');
  return '${local.month}月${local.day}日 ${two(local.hour)}:${two(local.minute)}';
}

class ChatMessageRow extends StatelessWidget {
  const ChatMessageRow({
    super.key,
    required this.message,
    required this.mine,
    required this.groupChat,
    this.fallbackAvatarUrl,
    this.fallbackName,
  });
  final ChatMessage message;
  final bool mine;
  final bool groupChat;
  final String? fallbackAvatarUrl;
  final String? fallbackName;

  @override
  Widget build(BuildContext context) {
    final avatarUrl = message.senderAvatarUrl?.isNotEmpty == true
        ? message.senderAvatarUrl
        : fallbackAvatarUrl;
    final name = message.senderNickname?.isNotEmpty == true
        ? message.senderNickname!
        : (fallbackName ?? '用户');
    final avatar = CircleAvatar(
      radius: 18,
      backgroundImage: avatarUrl?.isNotEmpty == true
          ? NetworkImage(resolveCustomerImageUrl(avatarUrl!))
          : null,
      child: avatarUrl?.isNotEmpty == true
          ? null
          : Text(name.isEmpty ? '?' : name.substring(0, 1)),
    );
    final bubble = Column(
      crossAxisAlignment: mine
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        if (groupChat)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Text(
              name,
              style: const TextStyle(fontSize: 12, color: Color(0xFF777777)),
            ),
          ),
        message.isDishShare
            ? DishShareCard(message: message)
            : Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 9,
                ),
                constraints: const BoxConstraints(maxWidth: 280),
                decoration: BoxDecoration(
                  color: mine
                      ? const Color(0xFFF28C28)
                      : const Color(0xFFF4F4F4),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  message.content,
                  style: TextStyle(
                    color: mine ? Colors.white : const Color(0xFF242424),
                  ),
                ),
              ),
      ],
    );
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        mainAxisAlignment: mine
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: mine
            ? [Flexible(child: bubble), const SizedBox(width: 8), avatar]
            : [avatar, const SizedBox(width: 8), Flexible(child: bubble)],
      ),
    );
  }
}

class DishShareCard extends StatelessWidget {
  const DishShareCard({super.key, required this.message});
  final ChatMessage message;
  @override
  Widget build(BuildContext context) => Container(
    width: 260,
    clipBehavior: Clip.antiAlias,
    decoration: BoxDecoration(
      color: Colors.white,
      border: Border.all(color: const Color(0xFFE7E7E7)),
      borderRadius: BorderRadius.circular(8),
    ),
    child: Row(
      children: [
        SizedBox(
          width: 88,
          height: 104,
          child: message.dishImageUrl?.isNotEmpty == true
              ? Image.network(
                  resolveCustomerImageUrl(message.dishImageUrl!),
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) => const Icon(Icons.restaurant_menu),
                )
              : const ColoredBox(
                  color: Color(0xFFF3F3F3),
                  child: Icon(Icons.restaurant_menu),
                ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  message.dishName?.isNotEmpty == true
                      ? message.dishName!
                      : '分享菜品',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 4),
                Text(
                  message.dishDescription?.isNotEmpty == true
                      ? message.dishDescription!
                      : '查看菜品详情',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF777777),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text(
                      message.dishPrice == null
                          ? ''
                          : '¥${message.dishPrice!.toStringAsFixed(2)}',
                      style: const TextStyle(
                        color: Color(0xFFE65A3A),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const Spacer(),
                    Text(
                      message.dishSales == null ? '' : '已售${message.dishSales}',
                      style: const TextStyle(
                        fontSize: 11,
                        color: Color(0xFF888888),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}
