import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../services/latest_request.dart';
import '../widgets/app_notice.dart';
import 'family_group_info_page.dart';
import 'shop_order_page.dart';
import 'chat_user_detail_page.dart';

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
  ChatMessage? _quotedMessage;
  final Map<int, GlobalKey> _messageKeys = {};
  int? _highlightedMessageId;
  Timer? _highlightTimer;
  ChatBlock? _blockState;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    _api = CustomerApi(AppScope.of(context).api);
    _messagesFuture = _loadMessages();
    if (widget.conversation.isDirect &&
        widget.conversation.directUserId != null) {
      _loadBlockState();
    }
    _wsSubscription ??= CustomerWebSocketService.instance
        .streamForModule('CHAT')
        .listen((event) {
          final id = int.tryParse(
            '${event.data['conversationId'] ?? event.data['id'] ?? ''}',
          );
          if ({
                'MESSAGE_CREATED',
                'MESSAGE_UPDATED',
                'MESSAGE_DELETED',
              }.contains(event.action) &&
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
    if (_blockState?.blockedByMe == true) {
      AppNotice.show(context, '请先取消拉黑后再发送消息');
      return;
    }
    setState(() => _sending = true);
    try {
      final sent = await _api.sendChatMessage(
        widget.conversation.id,
        content,
        quotedMessageId: _quotedMessage?.id,
      );
      _input.clear();
      _quotedMessage = null;
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
        AppNotice.show(context, _friendlyError(error));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _loadBlockState() async {
    final userId = widget.conversation.directUserId;
    if (userId == null) return;
    try {
      final state = await _api.chatBlock(userId);
      if (mounted) setState(() => _blockState = state);
    } catch (_) {}
  }

  Future<void> _showMessageActions(ChatMessage message, bool mine) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.format_quote),
              title: const Text('引用'),
              onTap: () => Navigator.pop(context, 'quote'),
            ),
            if (mine && !message.isRecalled)
              ListTile(
                leading: const Icon(Icons.undo),
                title: const Text('撤回'),
                onTap: () => Navigator.pop(context, 'recall'),
              ),
            ListTile(
              leading: const Icon(Icons.delete_outline),
              title: const Text('删除'),
              onTap: () => Navigator.pop(context, 'delete'),
            ),
          ],
        ),
      ),
    );
    if (!mounted || action == null) return;
    try {
      if (action == 'quote') {
        setState(() => _quotedMessage = message);
      } else if (action == 'recall') {
        await _api.recallChatMessage(widget.conversation.id, message.id);
        _reload();
      } else if (action == 'delete') {
        await _api.deleteChatMessage(widget.conversation.id, message.id);
        final cached = _cachedMessages;
        if (cached != null && mounted) {
          final updated = ChatMessages(
            conversation: cached.conversation,
            messages: cached.messages
                .where((item) => item.id != message.id)
                .toList(),
          );
          setState(() {
            _cachedMessages = updated;
            _messagesFuture = Future.value(updated);
          });
        }
      }
    } catch (error) {
      if (mounted) AppNotice.show(context, _friendlyError(error));
    }
  }

  void _jumpToMessage(int messageId) {
    final target = _messageKeys[messageId]?.currentContext;
    if (target != null) {
      Scrollable.ensureVisible(
        target,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
        alignment: 0.25,
      );
      _highlightTimer?.cancel();
      setState(() => _highlightedMessageId = messageId);
      _highlightTimer = Timer(const Duration(milliseconds: 1400), () {
        if (mounted) setState(() => _highlightedMessageId = null);
      });
    } else {
      AppNotice.show(context, '原消息已删除或不可见');
    }
  }

  void _showDirectUserInfo() {
    final userId = widget.conversation.directUserId;
    if (userId == null) return;
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (_) => ChatUserDetailPage(
              userId: userId,
              nickname: widget.conversation.directUserNickname ?? '用户',
              avatarUrl: widget.conversation.directUserAvatarUrl,
            ),
          ),
        )
        .then((_) => _loadBlockState());
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    _highlightTimer?.cancel();
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
      backgroundColor: const Color(0xFFF5F6F8),
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
          if (widget.conversation.isDirect)
            IconButton(
              tooltip: '查看用户资料',
              icon: const Icon(Icons.person_outline),
              onPressed: _showDirectUserInfo,
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
                    return KeyedSubtree(
                      key: _messageKeys.putIfAbsent(message.id, GlobalKey.new),
                      child: Column(
                        children: [
                          if (timestamp != null &&
                              shouldShowChatTimestamp(previous, timestamp))
                            Padding(
                              padding: const EdgeInsets.only(
                                bottom: 12,
                                top: 4,
                              ),
                              child: Text(
                                formatChatMessageTime(timestamp),
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
                            onLongPress: () =>
                                _showMessageActions(message, mine),
                            onQuoteTap: message.quotedMessageId == null
                                ? null
                                : () =>
                                      _jumpToMessage(message.quotedMessageId!),
                            onReEdit:
                                mine &&
                                    message.isRecalled &&
                                    !message.isDishShare &&
                                    !message.isOrderShare
                                ? () => setState(
                                    () => _input.text = message.content,
                                  )
                                : null,
                            highlighted: _highlightedMessageId == message.id,
                          ),
                        ],
                      ),
                    );
                  },
                );
              },
            ),
          ),
          if (_quotedMessage != null)
            Material(
              color: const Color(0xFFFFF4E8),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 6, 8),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.format_quote, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('引用 ${_quotedMessage!.senderNickname ?? '用户'}'),
                          const SizedBox(height: 5),
                          if (_quotedMessage!.isDishShare)
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              alignment: Alignment.centerLeft,
                              child: DishShareCard(message: _quotedMessage!),
                            )
                          else if (_quotedMessage!.isOrderShare)
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              alignment: Alignment.centerLeft,
                              child: OrderShareCard(message: _quotedMessage!),
                            )
                          else
                            Text(
                              _quotedMessage!.content,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                        ],
                      ),
                    ),
                    IconButton(
                      tooltip: '取消引用',
                      icon: const Icon(Icons.close),
                      onPressed: () => setState(() => _quotedMessage = null),
                    ),
                  ],
                ),
              ),
            ),
          SafeArea(
            top: false,
            child: Container(
              color: Colors.white,
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _input,
                      enabled:
                          widget.conversation.isActive &&
                          _blockState?.blockedByMe != true,
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

class ChatMessageRow extends StatelessWidget {
  const ChatMessageRow({
    super.key,
    required this.message,
    required this.mine,
    required this.groupChat,
    this.fallbackAvatarUrl,
    this.fallbackName,
    this.onLongPress,
    this.onQuoteTap,
    this.onReEdit,
    this.highlighted = false,
  });
  final ChatMessage message;
  final bool mine;
  final bool groupChat;
  final String? fallbackAvatarUrl;
  final String? fallbackName;
  final VoidCallback? onLongPress;
  final VoidCallback? onQuoteTap;
  final VoidCallback? onReEdit;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    if (message.isSystem) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Text(
          message.content,
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 12, color: Color(0xFF888888)),
        ),
      );
    }
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
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: GestureDetector(
                onLongPress: onLongPress,
                child: message.isRecalled
                    ? InkWell(
                        onTap: onReEdit,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: Text(
                            mine
                                ? (onReEdit != null ? '撤回一条消息，重新编辑' : '撤回一条消息')
                                : '${message.senderNickname ?? '对方'}撤回一条消息',
                            style: const TextStyle(
                              fontSize: 12,
                              color: Color(0xFF888888),
                            ),
                          ),
                        ),
                      )
                    : message.isDishShare
                    ? DishShareCard(message: message)
                    : message.isOrderShare
                    ? OrderShareCard(message: message)
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
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (message.quotedMessageId != null)
                              QuotedMessagePreview(
                                message: message,
                                mine: mine,
                                onTap: onQuoteTap,
                              ),
                            Text(
                              message.content,
                              style: TextStyle(
                                color: mine
                                    ? Colors.white
                                    : const Color(0xFF242424),
                              ),
                            ),
                          ],
                        ),
                      ),
              ),
            ),
            if (message.deliveryFailed) ...[
              const SizedBox(width: 5),
              const Icon(Icons.error, color: Colors.red, size: 18),
            ],
          ],
        ),
        if (message.deliveryFailed)
          const Padding(
            padding: EdgeInsets.only(top: 5),
            child: Text(
              '对方已将您拉黑，无法发送消息\n还请您注意您的言行举止，互联网不是法外之地',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: Colors.redAccent),
            ),
          ),
      ],
    );
    return AnimatedContainer(
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOut,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      decoration: BoxDecoration(
        color: highlighted ? const Color(0xFFFFE0B8) : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
      ),
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
  Widget build(BuildContext context) => InkWell(
    onTap: message.shopId == null
        ? null
        : () => Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ShopOrderPage(
                shopId: message.shopId!,
                highlightedDishId: message.dishId,
              ),
            ),
          ),
    child: Container(
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
                    errorBuilder: (_, _, _) =>
                        const Icon(Icons.restaurant_menu),
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
                  if ((message.shopName ?? '').isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(
                      message.shopName!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11,
                        color: Color(0xFF777777),
                      ),
                    ),
                  ],
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
                        message.dishSales == null
                            ? ''
                            : '已售${message.dishSales}',
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
    ),
  );
}

class QuotedMessagePreview extends StatelessWidget {
  const QuotedMessagePreview({
    super.key,
    required this.message,
    required this.mine,
    this.onTap,
  });

  final ChatMessage message;
  final bool mine;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final quoted = message.quotedMessage?.toChatMessage();
    Widget content;
    if (quoted?.isDishShare == true) {
      content = FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: DishShareCard(message: quoted!),
      );
    } else if (quoted?.isOrderShare == true) {
      content = FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: OrderShareCard(message: quoted!),
      );
    } else {
      content = Text(
        quoted?.content ?? message.quotedContent ?? '消息',
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 12,
          color: mine ? Colors.white : const Color(0xFF666666),
        ),
      );
    }
    return InkWell(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.all(7),
        color: mine ? const Color(0x33FFFFFF) : const Color(0xFFE8E8E8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              message.quotedSenderName ?? '用户',
              style: TextStyle(
                fontSize: 11,
                color: mine ? Colors.white70 : const Color(0xFF777777),
              ),
            ),
            const SizedBox(height: 4),
            content,
          ],
        ),
      ),
    );
  }
}

class OrderShareCard extends StatelessWidget {
  const OrderShareCard({super.key, required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) => Container(
    width: 260,
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: Colors.white,
      border: Border.all(color: const Color(0xFFE7E7E7)),
      borderRadius: BorderRadius.circular(8),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                message.orderShopName ?? '订单分享',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            Text(
              _orderStatusLabel(message.orderStatus),
              style: const TextStyle(fontSize: 12, color: Color(0xFFF28C28)),
            ),
          ],
        ),
        const SizedBox(height: 5),
        Text(
          '订单 ${message.orderNo ?? ''}',
          style: const TextStyle(fontSize: 11, color: Color(0xFF777777)),
        ),
        if ((message.orderCreatedAt ?? '').isNotEmpty)
          Text(
            message.orderCreatedAt!.replaceFirst('T', ' '),
            style: const TextStyle(fontSize: 11, color: Color(0xFF888888)),
          ),
        const Divider(height: 16),
        for (final item in message.orderItems)
          Padding(
            padding: const EdgeInsets.only(bottom: 3),
            child: Text(
              '${item.dishName} × ${item.quantity}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12),
            ),
          ),
        Row(
          children: [
            const Spacer(),
            Text(
              message.orderTotalAmount == null
                  ? ''
                  : '合计 ¥${message.orderTotalAmount!.toStringAsFixed(2)}',
              style: const TextStyle(
                color: Color(0xFFE65A3A),
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
        if ((message.orderRemark ?? '').isNotEmpty) ...[
          const SizedBox(height: 5),
          Text(
            '备注：${message.orderRemark}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 11, color: Color(0xFF777777)),
          ),
        ],
      ],
    ),
  );
}

String _orderStatusLabel(String? status) => switch (status) {
  'PENDING' => '待处理',
  'ACCEPTED' => '已接单',
  'REJECTED' => '已拒绝',
  'COMPLETED' => '已完成',
  'CANCELED' => '已取消',
  _ => status ?? '',
};
