import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../services/latest_request.dart';
import '../widgets/app_notice.dart';
import 'family_group_management_page.dart';
import 'chat_user_detail_page.dart';
import 'chat_essence_page.dart';

int chatVisualIndexFromChronologicalIndex(
    int chronologicalIndex,
    int messageCount,
    ) {
  assert(messageCount > 0);
  assert(chronologicalIndex >= 0 && chronologicalIndex < messageCount);
  return messageCount - 1 - chronologicalIndex;
}

bool chatImageUsesBubble(ChatMessage message) =>
    message.content.trim().isNotEmpty ||
        message.quotedMessageId != null ||
        message.imageExpired ||
        (message.imageUrl ?? '').isEmpty;

bool chatVisualIndexIsVisible(
    int visualIndex,
    Iterable<ItemPosition> positions,
    ) => positions.any(
      (position) =>
  position.index == visualIndex &&
      position.itemTrailingEdge > 0 &&
      position.itemLeadingEdge < 1,
);

const chatEmojiChoices = <String>[
  '😀','😁','😂','🤣','😃','😄','😅','😆','😊','🙂','🙃','😉',
  '😍','🥰','😘','😋','😎','🤩','🥳','🤗','🤭','🤔','🫡','🤐',
  '😴','😪','😢','😭','😤','😡','😱','😇','👍','👎','👌','✌️',
  '🤞','🤟','🤝','👏','🙌','🙏','💪','👊','👋','🫶','❤️','🧡',
  '💛','💚','💙','💜','🤍','💯','✨','🔥','⭐','🌟','💡','🎉',
  '🎊','🎁','🎂','🌹','💐','🌈','☀️','🌙','☁️','❄️','⚡','☔',
  '🍚','🍜','🥟','🍲','🍔','🍟','🍕','🥗','🍣','🍱','🍰','🍦',
  '🍭','☕','🥤','🧋','🍎','🍓','🍉','🍇','🥭','🍊','🍌','🥝',
  '✅','❌','❓','❗','⚠️','📍','📌','💬','📷','🎵','🏠','🏪',
  '🚗','🚀','⏰','💰',
];

class ChatScrollCoordinator {
  bool _hasObservedLatest = false;
  int? _latestMessageId;
  bool _navigatingToQuote = false;
  bool _pendingBottom = false;

  bool get isNavigatingToQuote => _navigatingToQuote;

  bool observeLatestMessage(int? messageId) {
    final shouldScroll = !_hasObservedLatest
        ? messageId != null
        : messageId != null &&
        (_latestMessageId == null || messageId > _latestMessageId!);
    _hasObservedLatest = true;
    _latestMessageId = messageId;
    return shouldScroll;
  }

  void beginQuoteNavigation() {
    _navigatingToQuote = true;
  }

  bool requestBottom() {
    if (!_navigatingToQuote) return true;
    _pendingBottom = true;
    return false;
  }

  bool endQuoteNavigation() {
    _navigatingToQuote = false;
    final shouldScroll = _pendingBottom;
    _pendingBottom = false;
    return shouldScroll;
  }
}

class ChatKeyboardInsetSettler {
  ChatKeyboardInsetSettler({
    required this.onSettled,
    this.delay = const Duration(milliseconds: 120),
  });

  final VoidCallback onSettled;
  final Duration delay;
  Timer? _timer;

  void schedule() {
    _timer?.cancel();
    _timer = Timer(delay, onSettled);
  }

  void dispose() => _timer?.cancel();
}

class ChatDetailPage extends StatefulWidget {
  const ChatDetailPage({super.key, required this.conversation});

  final ChatConversation conversation;

  @override
  State<ChatDetailPage> createState() => _ChatDetailPageState();
}

class _ChatDetailPageState extends State<ChatDetailPage>
    with WidgetsBindingObserver {
  late Future<_ChatDetailData> _future;
  final _input = TextEditingController();
  final _inputFocus = FocusNode();
  final _imagePicker = ImagePicker();
  final _itemScrollController = ItemScrollController();
  final _itemPositionsListener = ItemPositionsListener.create();
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
  bool _showEmoji = false;
  String? _pendingImagePath;
  ChatMessage? _latestUnreadEssence;
  int? _dismissedEssenceId;
  OverlayEntry? _actionOverlay;
  int _bottomScrollRequest = 0;
  final _scrollCoordinator = ChatScrollCoordinator();
  late final ChatKeyboardInsetSettler _keyboardInsetSettler;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _keyboardInsetSettler = ChatKeyboardInsetSettler(
      onSettled: () {
        if (mounted && _inputFocus.hasFocus) {
          _scheduleScrollToBottom(animate: false);
        }
      },
    );
    _input.addListener(_keepLatestAboveComposer);
    _future = _load();
    if (widget.conversation.isDirect &&
        widget.conversation.directUserId != null) {
      _loadBlockState();
    }
    if (!widget.conversation.isDirect) _loadLatestEssence();
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
      if (event.action.startsWith('ESSENCE_') &&
          id == widget.conversation.id) {
        _loadLatestEssence();
      }
    });
    _connectionSubscription = ShopWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) => _reload());
  }

  void _keepLatestAboveComposer() {
    if (_inputFocus.hasFocus && !_scrollCoordinator.isNavigatingToQuote) {
      _scheduleScrollToBottom(animate: false);
    }
  }

  @override
  void didChangeMetrics() {
    if (_inputFocus.hasFocus) _keyboardInsetSettler.schedule();
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
    if ((content.isEmpty && _pendingImagePath == null) || _sending || !active)
      return;
    if (_blockState?.blockedByMe == true) {
      AppNotice.show(context, '请先取消拉黑后再发送消息');
      return;
    }
    setState(() => _sending = true);
    try {
      final sent = _pendingImagePath == null
          ? await shopApi.sendChatMessage(
        widget.conversation.id,
        content,
        quotedMessageId: _quotedMessage?.id,
      )
          : await shopApi.sendChatImage(
        widget.conversation.id,
        _pendingImagePath!,
        content: content,
        quotedMessageId: _quotedMessage?.id,
      );
      _input.clear();
      _quotedMessage = null;
      _pendingImagePath = null;
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

  Future<void> _loadLatestEssence() async {
    try {
      final message = await shopApi.fetchLatestUnreadChatEssence(
        widget.conversation.id,
      );
      if (mounted && message?.id != _dismissedEssenceId) {
        setState(() => _latestUnreadEssence = message);
      }
    } catch (_) {}
  }

  Future<void> _pickImage(ImageSource source) async {
    try {
      final image = await _imagePicker.pickImage(
        source: source,
        imageQuality: 88,
        maxWidth: 2160,
        maxHeight: 2160,
      );
      if (image != null && mounted) {
        final path = await _compressImageIfNeeded(image.path);
        if (!mounted) return;
        setState(() {
          _pendingImagePath = path;
          _showEmoji = false;
        });
      }
    } catch (error) {
      if (mounted) {
        AppNotice.show(
          context,
          error is StateError ? '图片压缩后仍超过5MB，请选择较小的图片' : '无法读取图片，请检查相册或相机权限',
        );
      }
    }
  }

  Future<String> _compressImageIfNeeded(String path) async {
    final file = File(path);
    if (!await file.exists() || await file.length() <= 5 * 1024 * 1024) {
      return path;
    }
    final directory = await getTemporaryDirectory();
    const attempts = <(int, int)>[(70, 1920), (52, 1600), (36, 1280)];
    for (var index = 0; index < attempts.length; index++) {
      final (quality, dimension) = attempts[index];
      final target =
          '${directory.path}${Platform.pathSeparator}'
          'shop_chat_${DateTime.now().microsecondsSinceEpoch}_$index.jpg';
      final compressed = await FlutterImageCompress.compressAndGetFile(
        path,
        target,
        quality: quality,
        minWidth: dimension,
        minHeight: dimension,
        keepExif: false,
      );
      if (compressed != null && await compressed.length() <= 5 * 1024 * 1024) {
        return compressed.path;
      }
    }
    throw StateError('compressed image is still too large');
  }

  Future<void> _loadBlockState() async {
    final userId = widget.conversation.directUserId;
    if (userId == null) return;
    try {
      final state = await shopApi.fetchChatBlock(userId);
      if (mounted) setState(() => _blockState = state);
    } catch (_) {}
  }

  Future<void> _messageActions(
      ChatMessage message,
      bool mine,
      Offset position, {
        required bool canManageEssence,
      }) async {
    final action = await _showActionOverlay(
      position,
      mine: mine,
      recalled: message.isRecalled,
      canManageEssence: canManageEssence,
      isEssence: message.isEssence,
    );
    if (!mounted || action == null) return;
    try {
      if (action == 'quote') setState(() => _quotedMessage = message);
      if (action == 'recall')
        await shopApi.recallChatMessage(widget.conversation.id, message.id);
      if (action == 'delete')
        await shopApi.deleteChatMessage(widget.conversation.id, message.id);
      if (action == 'essence') {
        if (message.isEssence) {
          await shopApi.removeChatEssence(widget.conversation.id, message.id);
        } else {
          await shopApi.addChatEssence(widget.conversation.id, message.id);
        }
      }
      if (action != 'quote') await _reload();
    } catch (error) {
      if (mounted)
        AppNotice.show(
          context,
          error is ApiException ? error.message : '操作失败，请稍后重试',
        );
    }
  }

  Future<String?> _showActionOverlay(
      Offset position, {
        required bool mine,
        required bool recalled,
        required bool canManageEssence,
        required bool isEssence,
      }) {
    final completer = Completer<String?>();
    final overlay = Overlay.of(context, rootOverlay: true);
    _actionOverlay?.remove();
    late OverlayEntry entry;
    void close([String? value]) {
      if (!completer.isCompleted) completer.complete(value);
      entry.remove();
      if (identical(_actionOverlay, entry)) _actionOverlay = null;
    }

    final width = MediaQuery.sizeOf(context).width;
    entry = OverlayEntry(
      builder: (_) => Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: close,
            ),
          ),
          Positioned(
            left: (position.dx - 130).clamp(8.0, width - 268),
            top: (position.dy - 72).clamp(60.0, double.infinity),
            child: _MessageActionBar(
              mine: mine,
              recalled: recalled,
              canManageEssence: canManageEssence,
              isEssence: isEssence,
              onSelected: close,
            ),
          ),
        ],
      ),
    );
    _actionOverlay = entry;
    overlay.insert(entry);
    return completer.future;
  }

  void _jumpToQuote(int messageId) {
    final messages = _cachedData?.messages.messages ?? const <ChatMessage>[];
    final index = messages.indexWhere((message) => message.id == messageId);
    if (index < 0) return AppNotice.show(context, '原消息已删除或不可见');
    if (!_itemScrollController.isAttached) return;
    _bottomScrollRequest++;
    _scrollCoordinator.beginQuoteNavigation();
    final visualIndex = chatVisualIndexFromChronologicalIndex(
      index,
      messages.length,
    );
    final visible = chatVisualIndexIsVisible(
      visualIndex,
      _itemPositionsListener.itemPositions.value,
    );
    if (visible) {
      _itemScrollController.scrollTo(
        index: visualIndex,
        alignment: .35,
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
      );
    } else {
      _itemScrollController.jumpTo(index: visualIndex, alignment: .28);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_itemScrollController.isAttached) return;
        if (!chatVisualIndexIsVisible(
          visualIndex,
          _itemPositionsListener.itemPositions.value,
        )) {
          _itemScrollController.jumpTo(index: visualIndex, alignment: .18);
        }
      });
    }
    _highlightTimer?.cancel();
    _actionOverlay?.remove();
    _actionOverlay = null;
    setState(() => _highlightedMessageId = messageId);
    _highlightTimer = Timer(const Duration(milliseconds: 1400), () {
      if (mounted) {
        final pendingBottom = _scrollCoordinator.endQuoteNavigation();
        setState(() {
          _highlightedMessageId = null;
        });
        if (pendingBottom) _scheduleScrollToBottom(animate: true);
      }
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
            conversationId: widget.conversation.id,
            initialMuted: widget.conversation.muted,
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
    WidgetsBinding.instance.removeObserver(this);
    _keyboardInsetSettler.dispose();
    _actionOverlay?.remove();
    _actionOverlay = null;
    _subscription?.cancel();
    _connectionSubscription?.cancel();
    _highlightTimer?.cancel();
    _input.removeListener(_keepLatestAboveComposer);
    _input.dispose();
    _inputFocus.dispose();
    super.dispose();
  }

  void _scheduleScrollToBottom({required bool animate}) {
    if (!_scrollCoordinator.requestBottom()) return;
    final request = ++_bottomScrollRequest;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          request != _bottomScrollRequest ||
          _scrollCoordinator.isNavigatingToQuote ||
          !_itemScrollController.isAttached) {
        return;
      }
      if (animate) {
        _itemScrollController.scrollTo(
          index: 0,
          alignment: 0,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOut,
        );
      } else {
        _itemScrollController.jumpTo(index: 0, alignment: 0);
      }
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.white,
    resizeToAvoidBottomInset: true,
    appBar: AppBar(
      title: Text(widget.conversation.title, style: const TextStyle(fontWeight: FontWeight.w700)),
      backgroundColor: Colors.white,
      foregroundColor: const Color(0xFF242424),
      elevation: 0,
      scrolledUnderElevation: 0,
      actions: [
        if (!widget.conversation.isDirect)
          IconButton(
            tooltip: '群精华',
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) =>
                      ChatEssencePage(conversationId: widget.conversation.id),
                ),
              );
              if (mounted) {
                setState(() => _latestUnreadEssence = null);
                _loadLatestEssence();
              }
            },
            icon: const Icon(Icons.star_outline),
          ),
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
        final initialRender = _renderedMessageCount < 0;
        _renderedMessageCount = messageCount;
        final latestMessageId = data.messages.messages.isEmpty
            ? null
            : data.messages.messages.last.id;
        if (_scrollCoordinator.observeLatestMessage(latestMessageId)) {
          _scheduleScrollToBottom(animate: !initialRender);
        }
        return Column(
          children: [
            if (_latestUnreadEssence != null)
              Material(
                color: const Color(0xFFFFF4E8),
                child: ListTile(
                  dense: true,
                  leading: const Icon(Icons.star, color: Color(0xFFF28C28)),
                  title: const Text('有新的群精华'),
                  subtitle: Text(
                    _latestUnreadEssence!.content.isEmpty
                        ? '点击查看精华消息'
                        : _latestUnreadEssence!.content,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: IconButton(
                    tooltip: '关闭',
                    onPressed: () => setState(() {
                      _dismissedEssenceId = _latestUnreadEssence?.id;
                      _latestUnreadEssence = null;
                    }),
                    icon: const Icon(Icons.close),
                  ),
                  onTap: () async {
                    await Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => ChatEssencePage(
                          conversationId: widget.conversation.id,
                        ),
                      ),
                    );
                    if (mounted) setState(() => _latestUnreadEssence = null);
                  },
                ),
              ),
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
                controller: _itemScrollController,
                positionsListener: _itemPositionsListener,
                onLongPress: (message, mine, position) => _messageActions(
                  message,
                  mine,
                  position,
                  canManageEssence:
                  !conversation.isDirect &&
                      (data.group?.members.any(
                            (member) =>
                        member.userId == data.currentUser.id &&
                            member.role == 'OWNER',
                      ) ==
                          true),
                ),
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
                            else if (_quotedMessage!.isImage)
                                _ImageShareCard(message: _quotedMessage!)
                              else if (_quotedMessage!.isShopShare)
                                  _ShopShareCard(message: _quotedMessage!)
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
            if (_pendingImagePath != null)
              Container(
                color: Colors.white,
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
                child: Row(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: Image.file(
                        File(_pendingImagePath!),
                        width: 64,
                        height: 64,
                        fit: BoxFit.cover,
                      ),
                    ),
                    const SizedBox(width: 10),
                    const Expanded(child: Text('图片将随消息一起发送')),
                    IconButton(
                      tooltip: '移除图片',
                      icon: const Icon(Icons.close),
                      onPressed: () => setState(() => _pendingImagePath = null),
                    ),
                  ],
                ),
              ),
            SafeArea(
              top: false,
              child: Container(
                color: Colors.white,
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        PopupMenuButton<ImageSource>(
                          tooltip: '发送图片',
                          icon: const Icon(Icons.add_photo_alternate_outlined),
                          onSelected: _pickImage,
                          itemBuilder: (_) => const [
                            PopupMenuItem(
                              value: ImageSource.gallery,
                              child: ListTile(
                                leading: Icon(Icons.photo_library_outlined),
                                title: Text('从相册选择'),
                              ),
                            ),
                            PopupMenuItem(
                              value: ImageSource.camera,
                              child: ListTile(
                                leading: Icon(Icons.camera_alt_outlined),
                                title: Text('拍照'),
                              ),
                            ),
                          ],
                        ),
                        Expanded(
                          child: TextField(
                            controller: _input,
                            focusNode: _inputFocus,
                            enabled:
                            conversation.isActive &&
                                _blockState?.blockedByMe != true,
                            maxLines: 4,
                            minLines: 1,
                            decoration: const InputDecoration(hintText: '输入消息'),
                            onTap: () {
                              if (_showEmoji)
                                setState(() => _showEmoji = false);
                            },
                            onSubmitted: (_) => _send(conversation.isActive),
                          ),
                        ),
                        IconButton(
                          tooltip: '表情',
                          onPressed: () {
                            setState(() => _showEmoji = !_showEmoji);
                            if (_showEmoji) {
                              _inputFocus.unfocus();
                            } else {
                              _inputFocus.requestFocus();
                            }
                            _scheduleScrollToBottom(animate: false);
                          },
                          icon: Icon(
                            _showEmoji
                                ? Icons.keyboard_alt_outlined
                                : Icons.emoji_emotions_outlined,
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
                    if (_showEmoji)
                      _EmojiPanel(
                        onSelected: (emoji) {
                          final selection = _input.selection;
                          final start = selection.start < 0
                              ? _input.text.length
                              : selection.start;
                          _input.text = _input.text.replaceRange(
                            start,
                            start,
                            emoji,
                          );
                          _input.selection = TextSelection.collapsed(
                            offset: start + emoji.length,
                          );
                        },
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
    required this.positionsListener,
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
  final ItemScrollController controller;
  final ItemPositionsListener positionsListener;
  final void Function(ChatMessage message, bool mine, Offset position)
  onLongPress;
  final void Function(int messageId) onQuoteTap;
  final void Function(ChatMessage message) onReEdit;
  final int? highlightedMessageId;

  @override
  Widget build(BuildContext context) {
    if (messages.isEmpty) return const Center(child: Text('开始聊天吧'));
    return ScrollablePositionedList.builder(
      itemScrollController: controller,
      itemPositionsListener: positionsListener,
      reverse: true,
      padding: const EdgeInsets.fromLTRB(12, 16, 12, 8),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        final messageIndex = chatVisualIndexFromChronologicalIndex(
          index,
          messages.length,
        );
        final message = messages[messageIndex];
        final previous = messageIndex == 0 ? null : messages[messageIndex - 1];
        final sentAt = message.sentAt;
        final previousAt = previous?.sentAt;
        final isMine = message.senderUserId == currentUser.id;
        final sender = _senderFor(message, isMine);
        if (message.isSystem || message.isRecalled) {
          return Column(
            children: [
              if (sentAt != null && shouldShowChatTimestamp(sentAt, previousAt))
                _Timestamp(value: sentAt),
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: GestureDetector(
                  onLongPressStart: message.isRecalled
                      ? (details) =>
                      onLongPress(message, isMine, details.globalPosition)
                      : null,
                  child: InkWell(
                    onTap: isMine && !message.isSystem && message.type == 'TEXT'
                        ? () => onReEdit(message)
                        : null,
                    child: Text(
                      message.isRecalled
                          ? (isMine
                          ? '撤回一条消息${message.type == 'TEXT' ? '，重新编辑' : ''}'
                          : '${message.senderNickname ?? '对方'}撤回一条消息')
                          : message.content,
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ),
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
              onLongPress: (position) => onLongPress(message, isMine, position),
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

class _MessageActionBar extends StatefulWidget {
  const _MessageActionBar({
    required this.mine,
    required this.recalled,
    required this.canManageEssence,
    required this.isEssence,
    required this.onSelected,
  });
  final bool mine;
  final bool recalled;
  final bool canManageEssence;
  final bool isEssence;
  final ValueChanged<String?> onSelected;

  @override
  State<_MessageActionBar> createState() => _MessageActionBarState();
}

class _MessageActionBarState extends State<_MessageActionBar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 340),
  )..forward();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScaleTransition(
    scale: CurvedAnimation(parent: _controller, curve: Curves.easeOutBack),
    alignment: Alignment.bottomCenter,
    child: Material(
      elevation: 10,
      color: const Color(0xFF292929),
      borderRadius: BorderRadius.circular(8),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (!widget.recalled)
            _Action(
              icon: Icons.format_quote,
              label: '引用',
              onTap: () => widget.onSelected('quote'),
            ),
          if (widget.mine && !widget.recalled)
            _Action(
              icon: Icons.undo,
              label: '撤回',
              onTap: () => widget.onSelected('recall'),
            ),
          _Action(
            icon: Icons.delete_outline,
            label: '删除',
            onTap: () => widget.onSelected('delete'),
          ),
          if (widget.canManageEssence && !widget.recalled)
            _Action(
              icon: widget.isEssence ? Icons.star : Icons.star_outline,
              label: widget.isEssence ? '取消精华' : '精华',
              onTap: () => widget.onSelected('essence'),
            ),
        ],
      ),
    ),
  );
}

class _Action extends StatelessWidget {
  const _Action({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: Colors.white, size: 19),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(color: Colors.white, fontSize: 10),
          ),
        ],
      ),
    ),
  );
}

class _EmojiPanel extends StatelessWidget {
  const _EmojiPanel({required this.onSelected});
  final ValueChanged<String> onSelected;
  @override
  Widget build(BuildContext context) => SafeArea(
    top: false,
    child: Container(
      height: 210,
      color: Colors.white,
      padding: const EdgeInsets.all(12),
      child: GridView.builder(
        itemCount: chatEmojiChoices.length,
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 8,
          mainAxisSpacing: 8,
          crossAxisSpacing: 8,
        ),
        itemBuilder: (_, index) => InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () => onSelected(chatEmojiChoices[index]),
          child: Center(
            child: Text(
              chatEmojiChoices[index],
              style: const TextStyle(fontSize: 25),
            ),
          ),
        ),
      ),
    ),
  );
}

class ShopChatMessageContent extends StatelessWidget {
  const ShopChatMessageContent({
    super.key,
    required this.message,
    required this.isMine,
    required this.onQuoteTap,
  });

  final ChatMessage message;
  final bool isMine;
  final VoidCallback? onQuoteTap;

  @override
  Widget build(BuildContext context) {
    final card =
        message.isDishShare ||
            message.isOrderShare ||
            message.isImage ||
            message.isShopShare;
    final Widget content = message.isDishShare
        ? _DishShareCard(message: message)
        : message.isOrderShare
        ? _OrderShareCard(message: message)
        : message.isImage
        ? _ImageShareCard(message: message, isMine: isMine)
        : message.isShopShare
        ? _ShopShareCard(message: message)
        : Text(
      message.content,
      style: TextStyle(
        color: isMine ? Colors.white : const Color(0xFF242424),
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (message.quotedMessageId != null)
          Padding(
            padding: card
                ? const EdgeInsets.fromLTRB(8, 8, 8, 2)
                : EdgeInsets.zero,
            child: _QuotedMessagePreview(
              message: message,
              isMine: isMine,
              onTap: onQuoteTap,
            ),
          ),
        content,
      ],
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
  final ValueChanged<Offset> onLongPress;
  final VoidCallback? onQuoteTap;
  final VoidCallback? onReEdit;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final hasAvatar = avatarUrl != null && avatarUrl!.isNotEmpty;

    final avatar = CircleAvatar(
      radius: 18,
      foregroundImage: hasAvatar
          ? NetworkImage(ApiConfig.resolveImageUrl(avatarUrl!))
          : null,
      onForegroundImageError: hasAvatar
          ? (_, _) {}
          : null,
      child: const Icon(Icons.person_outline, size: 19),
    );
    final bubble = _PressableBubble(
      onLongPress: onLongPress,
      alignment: isMine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 280),
        child: Container(
          padding:
          message.isDishShare ||
              message.isOrderShare ||
              message.isImage ||
              message.isShopShare
              ? EdgeInsets.zero
              : const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
          decoration: BoxDecoration(
            color: (message.isImage && !chatImageUsesBubble(message)) ||
                message.isDishShare ||
                message.isOrderShare ||
                message.isShopShare
                ? Colors.transparent
                : (isMine ? const Color(0xFFF28C28) : const Color(0xFFF4F4F4)),
            borderRadius: BorderRadius.circular(8),
          ),
          child: ShopChatMessageContent(
            message: message,
            isMine: isMine,
            onQuoteTap: onQuoteTap,
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
            if (message.deliveryFailed) ...[
              const Icon(Icons.error, color: Colors.red, size: 18),
              const SizedBox(width: 5),
            ],
            Flexible(child: bubble),
          ],
        ),
        if (message.deliveryFailed)
          const SizedBox(
            width: 280,
            child: Padding(
              padding: EdgeInsets.only(top: 5),
              child: Text(
                '对方已将您拉黑，无法发送消息\n还请您注意您的言行举止，互联网不是法外之地',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11, color: Colors.redAccent),
              ),
            ),
          ),
        if (message.isEssence)
          const Padding(
            padding: EdgeInsets.only(top: 4),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.star, size: 13, color: Color(0xFFF28C28)),
                SizedBox(width: 3),
                Text(
                  '群精华',
                  style: TextStyle(fontSize: 11, color: Color(0xFFF28C28)),
                ),
              ],
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

class _PressableBubble extends StatefulWidget {
  const _PressableBubble({
    required this.child,
    required this.onLongPress,
    required this.alignment,
  });
  final Widget child;
  final ValueChanged<Offset> onLongPress;
  final Alignment alignment;

  @override
  State<_PressableBubble> createState() => _PressableBubbleState();
}

class _PressableBubbleState extends State<_PressableBubble> {
  double _scale = 1;

  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
    tween: Tween(begin: .58, end: 1),
    duration: const Duration(milliseconds: 420),
    curve: Curves.easeOutBack,
    builder: (context, entryScale, child) => AnimatedScale(
      scale: _scale * entryScale,
      duration: const Duration(milliseconds: 240),
      curve: _scale < 1 ? Curves.easeOut : Curves.elasticOut,
      alignment: widget.alignment,
      child: child,
    ),
    child: GestureDetector(
      onTapDown: (_) => setState(() => _scale = .94),
      onTapCancel: () => setState(() => _scale = 1),
      onTapUp: (_) => setState(() => _scale = 1),
      onLongPressStart: (details) {
        setState(() => _scale = 1.04);
        widget.onLongPress(details.globalPosition);
      },
      onLongPressEnd: (_) => setState(() => _scale = 1),
      child: widget.child,
    ),
  );
}

class _DishShareCard extends StatelessWidget {
  const _DishShareCard({required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
          onTap: () {},
          child: Container(
            width: 250,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              border: Border.all(color: const Color(0xFFF0E7DE)),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 64,
                  height: 64,
                  clipBehavior: Clip.antiAlias,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF7F7F7),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: message.dishImageUrl?.isNotEmpty == true
                      ? Image.network(
                    ApiConfig.resolveImageUrl(message.dishImageUrl!),
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Icon(
                      Icons.restaurant_menu,
                      color: Color(0xFFD0D0D0),
                    ),
                  )
                      : const Icon(
                    Icons.restaurant_menu,
                    color: Color(0xFFD0D0D0),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
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
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF242424),
                        ),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          const Icon(
                            Icons.storefront_outlined,
                            size: 13,
                            color: Color(0xFFF28C28),
                          ),
                          const SizedBox(width: 2),
                          Expanded(
                            child: Text(
                              message.shopName?.isNotEmpty == true
                                  ? message.shopName!
                                  : '商家',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 11,
                                color: Color(0xFF777777),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            message.dishPrice == null
                                ? ''
                                : '¥${message.dishPrice!.toStringAsFixed(2)}',
                            style: const TextStyle(
                              color: Color(0xFFE65A3A),
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              height: 1.0,
                            ),
                          ),
                          const Spacer(),
                          Text(
                            message.dishSalesCount == null
                                ? ''
                                : '已售${message.dishSalesCount}',
                            style: const TextStyle(
                              fontSize: 10,
                              color: Color(0xFF999999),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
    );
  }
}

class _ImageShareCard extends StatelessWidget {
  const _ImageShareCard({
    required this.message,
    this.isMine = false,
    this.interactive = true,
  });
  final ChatMessage message;
  final bool isMine;
  final bool interactive;

  @override
  Widget build(BuildContext context) {
    final expired = message.imageExpired || (message.imageUrl ?? '').isEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (message.content.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 8, 8, 7),
            child: Text(
              message.content,
              style: TextStyle(color: isMine ? Colors.white : null),
            ),
          ),
        if (expired)
          const SizedBox(
            width: 220,
            height: 120,
            child: Center(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.broken_image_outlined, color: Colors.grey),
                  SizedBox(width: 7),
                  Text('图片资源已过期', style: TextStyle(color: Colors.grey)),
                ],
              ),
            ),
          )
        else
          GestureDetector(
            key: ValueKey('chat-image-interaction-${message.id}'),
            onTap: interactive
                ? () => _openShopChatImage(context, message.imageUrl!)
                : null,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.network(
                ApiConfig.resolveImageUrl(message.imageUrl!),
                headers: shopApi.token?.isNotEmpty == true
                    ? {'Authorization': 'Bearer ${shopApi.token}'}
                    : null,
                width: 240,
                height: 220,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => const SizedBox(
                  width: 220,
                  height: 140,
                  child: Center(child: Text('图片加载失败')),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

void _openShopChatImage(BuildContext context, String imageUrl) {
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) => Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
          backgroundColor: Colors.black,
          foregroundColor: Colors.white,
          title: const Text('查看图片'),
        ),
        body: InteractiveViewer(
          minScale: .5,
          maxScale: 5,
          boundaryMargin: const EdgeInsets.all(80),
          child: Center(
            child: Image.network(
              ApiConfig.resolveImageUrl(imageUrl),
              headers: shopApi.token?.isNotEmpty == true
                  ? {'Authorization': 'Bearer ${shopApi.token}'}
                  : null,
              fit: BoxFit.contain,
            ),
          ),
        ),
      ),
    ),
  );
}

class _ShopShareCard extends StatelessWidget {
  const _ShopShareCard({required this.message});
  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
          onTap: () {},
          child: Container(
            width: 250,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              border: Border.all(color: const Color(0xFFF0E7DE)),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Container(
                  width: 48,
                  height: 48,
                  clipBehavior: Clip.antiAlias,
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFF4E8),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: message.sharedShopLogoUrl?.isNotEmpty == true
                      ? Image.network(
                    ApiConfig.resolveImageUrl(message.sharedShopLogoUrl!),
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Icon(
                      Icons.storefront_outlined,
                      color: Color(0xFFF28C28),
                      size: 24,
                    ),
                  )
                      : const Icon(
                    Icons.storefront_outlined,
                    color: Color(0xFFF28C28),
                    size: 24,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text(
                        '分享店铺',
                        style: TextStyle(
                          fontSize: 10,
                          color: Color(0xFFF28C28),
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        message.sharedShopName ?? '店铺',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF242424),
                        ),
                      ),
                      if (message.sharedShopDescription?.isNotEmpty == true) ...[
                        const SizedBox(height: 2),
                        Text(
                          message.sharedShopDescription!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 11,
                            color: Color(0xFF999999),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const Icon(
                  Icons.chevron_right,
                  color: Color(0xFFD0D0D0),
                  size: 18,
                ),
              ],
            ),
          ),
        ),
    );
  }
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
    } else if (quoted?.isImage == true) {
      content = _ImageShareCard(message: quoted!, interactive: false);
    } else if (quoted?.isShopShare == true) {
      content = _ShopShareCard(message: quoted!);
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