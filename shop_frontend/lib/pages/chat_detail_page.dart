import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../services/latest_request.dart';
import '../widgets/app_notice.dart';
import 'family_group_management_page.dart';
import 'chat_user_detail_page.dart';

class ChatDetailPage extends StatefulWidget {
  const ChatDetailPage({super.key, required this.conversation});

  final ChatConversation conversation;

  @override
  State<ChatDetailPage> createState() => _ChatDetailPageState();
}

class _ChatDetailPageState extends State<ChatDetailPage> {
  late Future<_ChatDetailData> _future;
  final _input = TextEditingController();
  final _scrollController = ScrollController();
  final _messageRequests = LatestRequest();
  StreamSubscription? _subscription;
  StreamSubscription<bool>? _connectionSubscription;
  bool _sending = false;
  _ChatDetailData? _cachedData;
  int _renderedMessageCount = -1;
  ChatMessage? _quotedMessage;
  int? _highlightedMessageId;
  Timer? _highlightTimer;
  ChatBlockState? _blockState;

  @override
  void initState() {
    super.initState();
    _future = _load();
    if (widget.conversation.isDirect &&
        widget.conversation.directUserId != null) {
      _loadBlockState();
    }
    _subscription = ShopWebSocketService.instance
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
              id == widget.conversation.id) {
            _reload();
          }
        });
    _connectionSubscription = ShopWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) => _reload());
  }

  Future<_ChatDetailData> _load() async {
    final generation = _messageRequests.begin();
    final messagesFuture = shopApi.fetchChatMessages(widget.conversation.id);
    final currentUserFuture = shopApi.fetchCurrentUser();
    final shopFuture = shopApi.fetchMyShop();
    final groupFuture = widget.conversation.isDirect
        ? Future<FamilyGroup?>.value(null)
        : shopApi
              .fetchFamilyGroup(widget.conversation.id)
              .then<FamilyGroup?>((value) => value)
              .catchError((_) => null);
    try {
      final data = _ChatDetailData(
        messages: await messagesFuture,
        currentUser: await currentUserFuture,
        shop: await shopFuture,
        group: await groupFuture,
      );
      if (_messageRequests.isLatest(generation)) {
        _cachedData = data;
        return data;
      }
      return _cachedData ?? data;
    } catch (_) {
      final cached = _cachedData;
      if (cached != null) return cached;
      rethrow;
    }
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
    if (_blockState?.blockedByMe == true) {
      AppNotice.show(context, '请先取消拉黑后再发送消息');
      return;
    }
    setState(() => _sending = true);
    try {
      final sent = await shopApi.sendChatMessage(
        widget.conversation.id,
        content,
        quotedMessageId: _quotedMessage?.id,
      );
      _input.clear();
      _quotedMessage = null;
      final cached = _cachedData;
      if (cached != null && mounted) {
        _messageRequests.begin();
        final messages =
            cached.messages.messages.any((message) => message.id == sent.id)
            ? cached.messages.messages
            : [...cached.messages.messages, sent];
        final updated = _ChatDetailData(
          messages: ChatMessages(
            conversation: cached.messages.conversation,
            messages: messages,
          ),
          currentUser: cached.currentUser,
          shop: cached.shop,
          group: cached.group,
        );
        _cachedData = updated;
        setState(() {
          _future = Future.value(updated);
        });
        _reload();
      } else {
        await _reload();
      }
    } catch (error, stackTrace) {
      debugPrint('发送消息报错: $error\n$stackTrace');

      if (mounted) {
        final message = error is ApiException
            ? error.message
            : '网络连接失败，请检查网络后重试';
        AppNotice.show(context, message);
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _loadBlockState() async {
    final userId = widget.conversation.directUserId;
    if (userId == null) return;
    try {
      final state = await shopApi.fetchChatBlock(userId);
      if (mounted) setState(() => _blockState = state);
    } catch (_) {}
  }

  Future<void> _messageActions(ChatMessage message, bool mine) async {
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
      if (action == 'quote') setState(() => _quotedMessage = message);
      if (action == 'recall')
        await shopApi.recallChatMessage(widget.conversation.id, message.id);
      if (action == 'delete')
        await shopApi.deleteChatMessage(widget.conversation.id, message.id);
      if (action != 'quote') await _reload();
    } catch (error) {
      if (mounted)
        AppNotice.show(
          context,
          error is ApiException ? error.message : '操作失败，请稍后重试',
        );
    }
  }

  void _jumpToQuote(int messageId) {
    final messages = _cachedData?.messages.messages ?? const <ChatMessage>[];
    final index = messages.indexWhere((message) => message.id == messageId);
    if (index < 0) return AppNotice.show(context, '原消息已删除或不可见');
    final target = (index * 96.0).clamp(
      0.0,
      _scrollController.hasClients
          ? _scrollController.position.maxScrollExtent
          : 0.0,
    );
    _scrollController.animateTo(
      target,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
    );
    _highlightTimer?.cancel();
    setState(() => _highlightedMessageId = messageId);
    _highlightTimer = Timer(const Duration(milliseconds: 1400), () {
      if (mounted) setState(() => _highlightedMessageId = null);
    });
  }

  void _showConversationInfo() {
    if (widget.conversation.isDirect) {
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
      return;
    }
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (_) => FamilyGroupManagementPage(
              conversationId: widget.conversation.id,
            ),
          ),
        )
        .then((_) => _reload());
  }

  @override
  void dispose() {
    _subscription?.cancel();
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
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F6F8),
    appBar: AppBar(
      title: Text(widget.conversation.title),
      actions: [
        IconButton(
          tooltip: widget.conversation.isDirect ? '用户资料' : '编辑群聊信息',
          onPressed: _showConversationInfo,
          icon: Icon(
            widget.conversation.isDirect
                ? Icons.person_outline
                : Icons.edit_outlined,
          ),
        ),
      ],
    ),
    body: FutureBuilder<_ChatDetailData>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done &&
            _cachedData == null)
          return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError && _cachedData == null) {
          return Center(
            child: TextButton(
              onPressed: _reload,
              child: const Text('消息加载失败，点击重试'),
            ),
          );
        }
        final data = snapshot.data ?? _cachedData!;
        final conversation = data.messages.conversation;
        final messageCount = data.messages.messages.length;
        if (_renderedMessageCount != messageCount) {
          final animate = _renderedMessageCount >= 0;
          _renderedMessageCount = messageCount;
          _scheduleScrollToBottom(animate: animate);
        }
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
                controller: _scrollController,
                onLongPress: _messageActions,
                onQuoteTap: _jumpToQuote,
                onReEdit: (message) =>
                    setState(() => _input.text = message.content),
                highlightedMessageId: _highlightedMessageId,
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
                            Text(
                              '引用 ${_quotedMessage!.senderNickname ?? '用户'}',
                            ),
                            const SizedBox(height: 5),
                            if (_quotedMessage!.isDishShare)
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                alignment: Alignment.centerLeft,
                                child: _DishShareCard(message: _quotedMessage!),
                              )
                            else if (_quotedMessage!.isOrderShare)
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                alignment: Alignment.centerLeft,
                                child: _OrderShareCard(
                                  message: _quotedMessage!,
                                ),
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
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _input,
                        enabled:
                            conversation.isActive &&
                            _blockState?.blockedByMe != true,
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
    required this.controller,
    required this.onLongPress,
    required this.onQuoteTap,
    required this.onReEdit,
    required this.highlightedMessageId,
  });

  final List<ChatMessage> messages;
  final CurrentUser currentUser;
  final ChatConversation conversation;
  final FamilyGroup? group;
  final ShopInfo shop;
  final ScrollController controller;
  final void Function(ChatMessage message, bool mine) onLongPress;
  final void Function(int messageId) onQuoteTap;
  final void Function(ChatMessage message) onReEdit;
  final int? highlightedMessageId;

  @override
  Widget build(BuildContext context) {
    if (messages.isEmpty) return const Center(child: Text('开始聊天吧'));
    return ListView.builder(
      controller: controller,
      padding: const EdgeInsets.fromLTRB(12, 16, 12, 8),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        final message = messages[index];
        final previous = index == 0 ? null : messages[index - 1];
        final sentAt = message.sentAt;
        final previousAt = previous?.sentAt;
        final isMine = message.senderUserId == currentUser.id;
        final sender = _senderFor(message, isMine);
        if (message.isSystem) {
          return Column(
            children: [
              if (sentAt != null && shouldShowChatTimestamp(sentAt, previousAt))
                _Timestamp(value: sentAt),
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  message.content,
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
              ),
            ],
          );
        }
        return Column(
          children: [
            if (sentAt != null && shouldShowChatTimestamp(sentAt, previousAt))
              _Timestamp(value: sentAt),
            _MessageBubble(
              message: message,
              isMine: isMine,
              senderName: conversation.isDirect ? null : sender.name,
              avatarUrl: sender.avatarUrl,
              onLongPress: () => onLongPress(message, isMine),
              onQuoteTap: message.quotedMessageId == null
                  ? null
                  : () => onQuoteTap(message.quotedMessageId!),
              onReEdit:
                  isMine &&
                      message.isRecalled &&
                      !message.isDishShare &&
                      !message.isOrderShare
                  ? () => onReEdit(message)
                  : null,
              highlighted: highlightedMessageId == message.id,
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
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Text(
        formatChatMessageTime(value),
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
    required this.onLongPress,
    required this.onQuoteTap,
    required this.onReEdit,
    required this.highlighted,
  });

  final ChatMessage message;
  final bool isMine;
  final String? senderName;
  final String? avatarUrl;
  final VoidCallback onLongPress;
  final VoidCallback? onQuoteTap;
  final VoidCallback? onReEdit;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final hasAvatar = avatarUrl != null && avatarUrl!.isNotEmpty; // 提取一个布尔值方便判断

    final avatar = CircleAvatar(
      radius: 18,
      foregroundImage: hasAvatar
          ? NetworkImage(ApiConfig.resolveImageUrl(avatarUrl!))
          : null,
      onForegroundImageError: hasAvatar
          ? (_, _) {} // 有图片时才给回调
          : null,     // 没图片时必须为 null
      child: const Icon(Icons.person_outline, size: 19),
    );
    final bubble = GestureDetector(
      onLongPress: onLongPress,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 280),
        child: Container(
          padding: message.isDishShare || message.isOrderShare
              ? EdgeInsets.zero
              : const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
          decoration: BoxDecoration(
            color: isMine ? const Color(0xFFF28C28) : const Color(0xFFF4F4F4),
            borderRadius: BorderRadius.circular(8),
          ),
          child: message.isRecalled
              ? InkWell(
                  onTap: onReEdit,
                  child: Text(
                    isMine
                        ? (onReEdit != null ? '撤回一条消息，重新编辑' : '撤回一条消息')
                        : '${message.senderNickname ?? '对方'}撤回一条消息',
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                )
              : message.isDishShare
              ? _DishShareCard(message: message)
              : message.isOrderShare
              ? _OrderShareCard(message: message)
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (message.quotedMessageId != null)
                      _QuotedMessagePreview(
                        message: message,
                        isMine: isMine,
                        onTap: onQuoteTap,
                      ),
                    Text(
                      message.content,
                      style: TextStyle(
                        color: isMine ? Colors.white : const Color(0xFF242424),
                      ),
                    ),
                  ],
                ),
        ),
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
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(child: bubble),
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
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      decoration: BoxDecoration(
        color: highlighted ? const Color(0xFFFFE0B8) : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
      ),
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
                  ApiConfig.resolveImageUrl(message.dishImageUrl!),
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
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  message.dishName ?? '分享菜品',
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
                  (message.dishDescription ?? '').isEmpty
                      ? '查看菜品详情'
                      : message.dishDescription!,
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
                      message.dishSalesCount == null
                          ? ''
                          : '已售${message.dishSalesCount}',
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

class _QuotedMessagePreview extends StatelessWidget {
  const _QuotedMessagePreview({
    required this.message,
    required this.isMine,
    required this.onTap,
  });

  final ChatMessage message;
  final bool isMine;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final quoted = message.quotedMessage;
    Widget content;
    if (quoted?.isDishShare == true) {
      content = FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: _DishShareCard(message: quoted!),
      );
    } else if (quoted?.isOrderShare == true) {
      content = FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: _OrderShareCard(message: quoted!),
      );
    } else {
      content = Text(
        quoted?.content ?? message.quotedContent ?? '消息',
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 12,
          color: isMine ? Colors.white : Colors.grey[700],
        ),
      );
    }
    return InkWell(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.all(7),
        color: isMine ? const Color(0x33FFFFFF) : const Color(0xFFE4E4E4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              message.quotedSenderName ?? '用户',
              style: TextStyle(
                fontSize: 11,
                color: isMine ? Colors.white70 : Colors.grey[600],
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

class _OrderShareCard extends StatelessWidget {
  const _OrderShareCard({required this.message});

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
