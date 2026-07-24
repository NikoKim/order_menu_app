import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../services/latest_request.dart';
import '../widgets/app_notice.dart';
import '../widgets/chat_action_overlay.dart';
import '../widgets/moment_image_viewer.dart';
import '../widgets/ui_utils.dart';
import 'chat_essence_page.dart';
import 'family_group_info_page.dart';
import 'shop_order_page.dart';
import 'chat_user_detail_page.dart';
import 'moment_detail_page.dart';

int chatVisualIndexFromChronologicalIndex(
  int chronologicalIndex,
  int messageCount,
) {
  assert(messageCount > 0);
  assert(chronologicalIndex >= 0 && chronologicalIndex < messageCount);
  return messageCount - 1 - chronologicalIndex;
}

bool chatVisualIndexIsVisible(
  int visualIndex,
  Iterable<ItemPosition> positions,
) => positions.any(
  (position) =>
      position.index == visualIndex &&
      position.itemTrailingEdge > 0 &&
      position.itemLeadingEdge < 1,
);

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

  void reset() {
    _hasObservedLatest = false;
    _latestMessageId = null;
    _navigatingToQuote = false;
    _pendingBottom = false;
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
  static const _messagePageSize = 50;

  late CustomerApi _api;
  late Future<ChatMessages> _messagesFuture;
  final _input = TextEditingController();
  final _inputFocus = FocusNode();
  final _itemScrollController = ItemScrollController();
  final _itemPositionsListener = ItemPositionsListener.create();
  final _messageRequests = LatestRequest();
  StreamSubscription<CustomerWsMessage>? _wsSubscription;
  StreamSubscription<bool>? _connectionSubscription;
  bool _sending = false;
  bool _isLoadingMore = false;
  bool _hasLoadedHistory = false;
  bool _initialized = false;
  ChatMessages? _cachedMessages;
  int _renderedMessageCount = -1;
  ChatMessage? _quotedMessage;
  int? _highlightedMessageId;
  Timer? _highlightTimer;
  ChatBlock? _blockState;
  String? _pendingImagePath;
  bool _showEmoji = false;
  ChatMessage? _latestUnreadEssence;
  int _bottomScrollRequest = 0;
  int? _lastMarkedReadMessageId;
  int? _markingReadMessageId;
  Timer? _presenceHeartbeatTimer;
  Timer? _typingPollTimer;
  Timer? _typingDebounceTimer;
  Timer? _typingIdleTimer;
  Timer? _typingRefreshTimer;
  bool _typingPublished = false;
  Set<int> _remoteTypingUserIds = const {};
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
    _itemPositionsListener.itemPositions.addListener(
      _handleItemPositionsChanged,
    );
    _input.addListener(_handleTypingInputChanged);
  }

  @override
  void didUpdateWidget(covariant ChatDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_initialized || oldWidget.conversation.id == widget.conversation.id) {
      return;
    }
    _stopTyping(conversationId: oldWidget.conversation.id);
    _remoteTypingUserIds = const {};
    _messageRequests.begin();
    _cachedMessages = null;
    _renderedMessageCount = -1;
    _isLoadingMore = false;
    _hasLoadedHistory = false;
    _lastMarkedReadMessageId = null;
    _markingReadMessageId = null;
    _quotedMessage = null;
    _highlightedMessageId = null;
    _blockState = null;
    _latestUnreadEssence = null;
    _scrollCoordinator.reset();
    _messagesFuture = _loadMessages();
    if (!widget.conversation.isDirect) _loadLatestEssence();
    if (widget.conversation.isDirect &&
        widget.conversation.directUserId != null) {
      _loadBlockState();
    }
    _pollTypingUsers();
  }

  @override
  void didChangeMetrics() {
    if (_inputFocus.hasFocus) _keyboardInsetSettler.schedule();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _sendPresenceHeartbeat();
      _pollTypingUsers();
    } else if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      _stopTyping();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    _api = CustomerApi(AppScope.of(context).api);
    _startPresenceTracking();
    _messagesFuture = _loadMessages();
    if (!widget.conversation.isDirect) _loadLatestEssence();
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

  void _startPresenceTracking() {
    _sendPresenceHeartbeat();
    _pollTypingUsers();
    _presenceHeartbeatTimer?.cancel();
    _presenceHeartbeatTimer = Timer.periodic(
      const Duration(seconds: 40),
      (_) => _sendPresenceHeartbeat(),
    );
    _typingPollTimer?.cancel();
    _typingPollTimer = Timer.periodic(
      const Duration(seconds: 3),
      (_) => _pollTypingUsers(),
    );
  }

  void _sendPresenceHeartbeat() {
    unawaited(_api.heartbeatPresence().catchError((_) {}));
  }

  Future<void> _pollTypingUsers() async {
    final conversationId = widget.conversation.id;
    try {
      final users = await _api.typingUsers(conversationId);
      if (!mounted || widget.conversation.id != conversationId) return;
      if (_sameUserIds(users, _remoteTypingUserIds)) return;
      setState(() => _remoteTypingUserIds = users);
    } catch (_) {
      if (mounted &&
          widget.conversation.id == conversationId &&
          _remoteTypingUserIds.isNotEmpty) {
        setState(() => _remoteTypingUserIds = const {});
      }
    }
  }

  bool _sameUserIds(Set<int> left, Set<int> right) =>
      left.length == right.length && left.every(right.contains);

  void _handleTypingInputChanged() {
    if (_input.text.trim().isEmpty ||
        !widget.conversation.isActive ||
        _blockState?.blockedByMe == true) {
      _stopTyping();
      return;
    }
    _typingIdleTimer?.cancel();
    _typingIdleTimer = Timer(const Duration(seconds: 2), () => _stopTyping());
    if (!_typingPublished && _typingDebounceTimer == null) {
      _typingDebounceTimer = Timer(const Duration(milliseconds: 250), () {
        _typingDebounceTimer = null;
        if (_input.text.trim().isNotEmpty && mounted) {
          _publishTyping();
        }
      });
    }
  }

  void _publishTyping() {
    _typingPublished = true;
    final conversationId = widget.conversation.id;
    unawaited(
      _api.setConversationTyping(conversationId, true).catchError((_) {}),
    );
    _typingRefreshTimer ??= Timer.periodic(const Duration(seconds: 4), (_) {
      if (!_typingPublished || _input.text.trim().isEmpty) return;
      unawaited(
        _api.setConversationTyping(conversationId, true).catchError((_) {}),
      );
    });
  }

  void _stopTyping({int? conversationId}) {
    _typingDebounceTimer?.cancel();
    _typingDebounceTimer = null;
    _typingIdleTimer?.cancel();
    _typingIdleTimer = null;
    _typingRefreshTimer?.cancel();
    _typingRefreshTimer = null;
    if (!_typingPublished || !_initialized) return;
    _typingPublished = false;
    unawaited(
      _api
          .setConversationTyping(
            conversationId ?? widget.conversation.id,
            false,
          )
          .catchError((_) {}),
    );
  }

  String get _typingLabel {
    if (_remoteTypingUserIds.isEmpty) return '';
    if (widget.conversation.isDirect) {
      return '${widget.conversation.directUserNickname ?? '对方'}正在输入...';
    }
    final names = <String>[];
    for (final userId in _remoteTypingUserIds) {
      final messages = _cachedMessages?.messages ?? const <ChatMessage>[];
      final match = messages.reversed.where(
        (message) => message.senderUserId == userId,
      );
      final name = match.isEmpty ? null : match.first.senderNickname;
      if (name != null && name.trim().isNotEmpty) names.add(name.trim());
    }
    if (names.isEmpty) return '有人正在输入...';
    final visible = names.take(2).join('、');
    return _remoteTypingUserIds.length > 2
        ? '$visible 等人正在输入...'
        : '$visible 正在输入...';
  }

  Future<void> _loadLatestEssence() async {
    try {
      final message = await _api.latestUnreadEssence(widget.conversation.id);
      if (mounted) setState(() => _latestUnreadEssence = message);
    } catch (_) {}
  }

  Future<ChatMessages> _loadMessages() async {
    final conversationId = widget.conversation.id;
    final generation = _messageRequests.begin();
    final result = await _api.chatMessages(
      conversationId,
      limit: _messagePageSize,
    );
    if (_messageRequests.isLatest(generation) &&
        widget.conversation.id == conversationId) {
      final merged = _mergeLatestPage(result);
      _cachedMessages = merged;
      _scheduleMarkLatestRead(merged);
      return merged;
    }
    return _cachedMessages ?? result;
  }

  ChatMessages _mergeLatestPage(ChatMessages latest) {
    final cached = _cachedMessages;
    if (cached == null || !_hasLoadedHistory) return latest;
    return ChatMessages(
      conversation: latest.conversation,
      messages: _mergeMessages(cached.messages, latest.messages),
      hasMore: cached.hasMore,
      nextBeforeId: cached.nextBeforeId,
    );
  }

  List<ChatMessage> _mergeMessages(
    Iterable<ChatMessage> older,
    Iterable<ChatMessage> newer,
  ) {
    final byId = <int, ChatMessage>{};
    for (final message in older) {
      byId[message.id] = message;
    }
    for (final message in newer) {
      byId[message.id] = message;
    }
    final merged = byId.values.toList()
      ..sort((left, right) => left.id.compareTo(right.id));
    return merged;
  }

  Future<void> _loadMoreMessages() async {
    final cached = _cachedMessages;
    final beforeId = cached?.nextBeforeId;
    if (_isLoadingMore ||
        cached == null ||
        !cached.hasMore ||
        beforeId == null) {
      return;
    }
    final conversationId = widget.conversation.id;
    setState(() => _isLoadingMore = true);
    try {
      final page = await _api.chatMessages(
        conversationId,
        beforeId: beforeId,
        limit: _messagePageSize,
      );
      if (!mounted || widget.conversation.id != conversationId) return;
      final current = _cachedMessages;
      if (current == null || current.nextBeforeId != beforeId) return;
      _hasLoadedHistory = true;
      final nextBeforeId = page.nextBeforeId;
      final updated = ChatMessages(
        conversation: current.conversation,
        messages: _mergeMessages(page.messages, current.messages),
        hasMore:
            page.hasMore &&
            nextBeforeId != null &&
            nextBeforeId != beforeId &&
            page.messages.isNotEmpty,
        nextBeforeId: nextBeforeId,
      );
      _cachedMessages = updated;
      // In a reversed list, prepending chronological history leaves every
      // existing message at the same visual index, preserving scroll position.
      setState(() => _messagesFuture = Future.value(updated));
    } catch (error) {
      if (mounted && widget.conversation.id == conversationId) {
        AppNotice.show(context, _friendlyError(error));
      }
    } finally {
      if (mounted && widget.conversation.id == conversationId) {
        setState(() => _isLoadingMore = false);
      }
    }
  }

  void _handleItemPositionsChanged() {
    final cached = _cachedMessages;
    if (cached == null || !cached.hasMore || _isLoadingMore) return;
    final positions = _itemPositionsListener.itemPositions.value;
    if (positions.isEmpty || cached.messages.isEmpty) return;
    final oldestThreshold = cached.messages.length > 3
        ? cached.messages.length - 3
        : 0;
    if (positions.any(
      (position) =>
          position.index >= oldestThreshold && position.itemLeadingEdge < 1,
    )) {
      _loadMoreMessages();
    }
  }

  void _scheduleMarkLatestRead(ChatMessages messages) {
    if (messages.messages.isEmpty) return;
    final conversationId = widget.conversation.id;
    final latestMessageId = messages.messages.last.id;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || widget.conversation.id != conversationId) return;
      _markLatestRead(conversationId, latestMessageId);
    });
  }

  Future<void> _markLatestRead(int conversationId, int messageId) async {
    if ((_lastMarkedReadMessageId ?? -1) >= messageId ||
        (_markingReadMessageId ?? -1) >= messageId) {
      return;
    }
    _markingReadMessageId = messageId;
    try {
      await _api.markConversationRead(conversationId, messageId);
      if (mounted && widget.conversation.id == conversationId) {
        _lastMarkedReadMessageId = messageId;
      }
    } catch (_) {
      // A later refresh or WebSocket event retries without blocking the chat.
    } finally {
      if (_markingReadMessageId == messageId) _markingReadMessageId = null;
    }
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
    final imagePath = _pendingImagePath;
    final quotedMessageId = _quotedMessage?.id;
    final sendStartedAt = DateTime.now();
    final currentUserId = AppScope.of(context).auth.user?.id;
    if ((content.isEmpty && _pendingImagePath == null) ||
        _sending ||
        !widget.conversation.isActive) {
      return;
    }
    if (_blockState?.blockedByMe == true) {
      AppNotice.show(context, '请先取消拉黑后再发送消息');
      return;
    }
    _stopTyping();
    setState(() => _sending = true);
    try {
      final sent = imagePath == null
          ? await _api.sendChatMessage(
              widget.conversation.id,
              content,
              quotedMessageId: quotedMessageId,
            )
          : await _api.sendChatImage(
              conversationId: widget.conversation.id,
              filePath: imagePath,
              content: content,
              quotedMessageId: quotedMessageId,
            );
      _input.clear();
      _pendingImagePath = null;
      _quotedMessage = null;
      final cached = _cachedMessages;
      if (cached != null && mounted) {
        _messageRequests.begin();
        final updated = ChatMessages(
          conversation: cached.conversation,
          messages: [
            ...cached.messages.where((message) => message.id != sent.id),
            sent,
          ],
          hasMore: cached.hasMore,
          nextBeforeId: cached.nextBeforeId,
        );
        _cachedMessages = updated;
        _scheduleMarkLatestRead(updated);
        setState(() => _messagesFuture = Future.value(updated));
      } else {
        _reload();
      }
    } catch (error) {
      try {
        final recovered = await _api.chatMessages(widget.conversation.id);
        final delivered = recovered.messages.any((message) {
          final createdAt = DateTime.tryParse(message.createdAt);
          final recent =
              createdAt == null ||
              !createdAt.isBefore(
                sendStartedAt.subtract(const Duration(seconds: 5)),
              );
          return imagePath == null &&
              message.senderUserId == currentUserId &&
              message.content == content &&
              !message.isImageShare &&
              recent;
        });
        if (delivered) {
          _input.clear();
          _pendingImagePath = null;
          _quotedMessage = null;
          _messageRequests.begin();
          final merged = _mergeLatestPage(recovered);
          _cachedMessages = merged;
          _scheduleMarkLatestRead(merged);
          if (mounted) {
            setState(() {
              _messagesFuture = Future.value(merged);
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

  Future<void> _pickImage(ImageSource source) async {
    try {
      final image = await ImagePicker().pickImage(
        source: source,
        imageQuality: 82,
        maxWidth: 2160,
        maxHeight: 2160,
      );
      if (image != null && mounted) {
        final imagePath = await _compressImageIfNeeded(image.path);
        if (!mounted) return;
        setState(() {
          _pendingImagePath = imagePath;
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
    String smallestPath = path;
    var smallestLength = await file.length();
    const attempts = <(int, int)>[(70, 1920), (52, 1600), (36, 1280)];
    for (var index = 0; index < attempts.length; index++) {
      final (quality, dimension) = attempts[index];
      final target =
          '${directory.path}${Platform.pathSeparator}'
          'chat_${DateTime.now().microsecondsSinceEpoch}_$index.jpg';
      final compressed = await FlutterImageCompress.compressAndGetFile(
        path,
        target,
        quality: quality,
        minWidth: dimension,
        minHeight: dimension,
        keepExif: false,
      );
      if (compressed == null) continue;
      final length = await compressed.length();
      if (length < smallestLength) {
        smallestLength = length;
        smallestPath = compressed.path;
      }
      if (length <= 5 * 1024 * 1024) return compressed.path;
    }
    if (smallestLength <= 5 * 1024 * 1024) return smallestPath;
    throw StateError('compressed image is still too large');
  }

  void _insertEmoji(String emoji) {
    final selection = _input.selection;
    final start = selection.isValid ? selection.start : _input.text.length;
    final end = selection.isValid ? selection.end : _input.text.length;
    _input.value = TextEditingValue(
      text: _input.text.replaceRange(start, end, emoji),
      selection: TextSelection.collapsed(offset: start + emoji.length),
    );
  }

  Future<void> _loadBlockState() async {
    final userId = widget.conversation.directUserId;
    if (userId == null) return;
    try {
      final state = await _api.chatBlock(userId);
      if (mounted) setState(() => _blockState = state);
    } catch (_) {}
  }

  Future<void> _handleMessageAction(
    ChatMessage message,
    bool mine,
    String action,
  ) async {
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
            hasMore: cached.hasMore,
            nextBeforeId: cached.nextBeforeId,
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
    final messages = _cachedMessages?.messages ?? const <ChatMessage>[];
    final index = messages.indexWhere((message) => message.id == messageId);
    if (index < 0 || !_itemScrollController.isAttached) {
      AppNotice.show(context, '原消息已删除或不可见');
      return;
    }
    _bottomScrollRequest++;
    _scrollCoordinator.beginQuoteNavigation();
    final visualIndex = chatVisualIndexFromChronologicalIndex(
      index,
      messages.length,
    );
    if (chatVisualIndexIsVisible(
      visualIndex,
      _itemPositionsListener.itemPositions.value,
    )) {
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
              conversationId: widget.conversation.id,
            ),
          ),
        )
        .then((_) => _loadBlockState());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _keyboardInsetSettler.dispose();
    _itemPositionsListener.itemPositions.removeListener(
      _handleItemPositionsChanged,
    );
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    _highlightTimer?.cancel();
    _presenceHeartbeatTimer?.cancel();
    _typingPollTimer?.cancel();
    _stopTyping();
    _input.removeListener(_handleTypingInputChanged);
    _input.dispose();
    _inputFocus.dispose();
    super.dispose();
  }

  void _dismissComposerInput() {
    FocusManager.instance.primaryFocus?.unfocus();
    if (_showEmoji && mounted) {
      setState(() => _showEmoji = false);
    }
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
  Widget build(BuildContext context) {
    final user = AppScope.of(context).auth.user;
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: Text(
          widget.conversation.title,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF242424),
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
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
          if (_latestUnreadEssence != null)
            Material(
              color: const Color(0xFFFFF2DF),
              child: InkWell(
                onTap: () async {
                  await _api.markEssencesRead(
                    widget.conversation.id,
                    throughMessageId: _latestUnreadEssence!.id,
                  );
                  if (!mounted || !context.mounted) return;
                  setState(() => _latestUnreadEssence = null);
                  await Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => ChatEssencePage(
                        conversationId: widget.conversation.id,
                      ),
                    ),
                  );
                },
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
                  child: Row(
                    children: [
                      const Icon(Icons.auto_awesome, color: Color(0xFFE08222)),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Text(
                          _latestUnreadEssence!.content.isEmpty
                              ? '有一条新的群精华消息'
                              : _latestUnreadEssence!.content,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      IconButton(
                        tooltip: '关闭',
                        icon: const Icon(Icons.close, size: 19),
                        onPressed: () =>
                            setState(() => _latestUnreadEssence = null),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          Expanded(
            child: Listener(
              behavior: HitTestBehavior.translucent,
              onPointerDown: (_) => _dismissComposerInput(),
              child: FutureBuilder<ChatMessages>(
                future: _messagesFuture,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done &&
                      _cachedMessages == null) {
                    return ListView.builder(
                      padding: const EdgeInsets.all(16),
                      itemCount: 8,
                      itemBuilder: (context, index) {
                        final isMine = index % 2 == 0;
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 16),
                          child: Row(
                            mainAxisAlignment: isMine
                                ? MainAxisAlignment.end
                                : MainAxisAlignment.start,
                            children: [
                              if (!isMine)
                                const ShimmerBox(
                                  width: 36,
                                  height: 36,
                                  borderRadius: 18,
                                ),
                              if (!isMine) const SizedBox(width: 8),
                              ShimmerBox(
                                width: 150 + (index * 20 % 50).toDouble(),
                                height: 40,
                                borderRadius: 8,
                              ),
                              if (isMine) const SizedBox(width: 8),
                              if (isMine)
                                const ShimmerBox(
                                  width: 36,
                                  height: 36,
                                  borderRadius: 18,
                                ),
                            ],
                          ),
                        );
                      },
                    );
                  }
                  final chatMessages = (snapshot.data ?? _cachedMessages)!;
                  final messages = chatMessages.messages;
                  final initialRender = _renderedMessageCount < 0;
                  _renderedMessageCount = messages.length;
                  final latestMessageId = messages.isEmpty
                      ? null
                      : messages.last.id;
                  if (_scrollCoordinator.observeLatestMessage(
                    latestMessageId,
                  )) {
                    _scheduleScrollToBottom(animate: !initialRender);
                  }
                  if (messages.isEmpty) {
                    return const Center(child: Text('开始聊天吧'));
                  }
                  return Align(
                    alignment: Alignment.topCenter,
                    child: SizedBox(
                      width: double.infinity,
                      child: ScrollablePositionedList.builder(
                        itemScrollController: _itemScrollController,
                        itemPositionsListener: _itemPositionsListener,
                        reverse: true,
                        shrinkWrap: true,
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                        itemCount:
                            messages.length +
                            (chatMessages.hasMore || _isLoadingMore ? 1 : 0),
                        itemBuilder: (context, index) {
                          if (index >= messages.length) {
                            return SizedBox(
                              height: 44,
                              child: Center(
                                child: _isLoadingMore
                                    ? const SizedBox.square(
                                        dimension: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const SizedBox.shrink(),
                              ),
                            );
                          }
                          final messageIndex =
                              chatVisualIndexFromChronologicalIndex(
                                index,
                                messages.length,
                              );
                          final message = messages[messageIndex];
                          final mine = message.senderUserId == user?.id;
                          final timestamp = DateTime.tryParse(
                            message.createdAt,
                          );
                          final previous = messageIndex == 0
                              ? null
                              : DateTime.tryParse(
                                  messages[messageIndex - 1].createdAt,
                                );
                          return Column(
                            key: ValueKey(message.id),
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
                                onAction: (action) =>
                                    _handleMessageAction(message, mine, action),
                                onQuoteTap: message.quotedMessageId == null
                                    ? null
                                    : () => _jumpToMessage(
                                        message.quotedMessageId!,
                                      ),
                                onReEdit:
                                    mine &&
                                        message.isRecalled &&
                                        !message.isDishShare &&
                                        !message.isOrderShare
                                    ? () => setState(
                                        () => _input.text = message.content,
                                      )
                                    : null,
                                highlighted:
                                    _highlightedMessageId == message.id,
                              ),
                            ],
                          );
                        },
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          AnimatedSize(
            duration: const Duration(milliseconds: 160),
            child: _remoteTypingUserIds.isEmpty
                ? const SizedBox.shrink()
                : Container(
                    width: double.infinity,
                    color: Colors.white,
                    padding: const EdgeInsets.fromLTRB(52, 5, 12, 3),
                    child: Text(
                      _typingLabel,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Color(0xFF6F6F6F),
                        fontSize: 12,
                      ),
                    ),
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
                          else if (_quotedMessage!.isImageShare)
                            ChatImageCard(message: _quotedMessage!)
                          else if (_quotedMessage!.isShopShare)
                            ShopShareCard(message: _quotedMessage!)
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
              child: Row(
                children: [
                  PopupMenuButton<ImageSource>(
                    tooltip: '添加图片',
                    icon: const Icon(Icons.add_photo_alternate_outlined),
                    onSelected: _pickImage,
                    itemBuilder: (_) => const [
                      PopupMenuItem(
                        value: ImageSource.gallery,
                        child: Text('从相册选择'),
                      ),
                      PopupMenuItem(
                        value: ImageSource.camera,
                        child: Text('拍照'),
                      ),
                    ],
                  ),
                  Expanded(
                    child: TextField(
                      controller: _input,
                      focusNode: _inputFocus,
                      onTap: () {
                        if (_showEmoji) setState(() => _showEmoji = false);
                      },
                      enabled:
                          widget.conversation.isActive &&
                          _blockState?.blockedByMe != true,
                      minLines: 1,
                      maxLines: 4,
                      decoration: const InputDecoration(hintText: '输入消息'),
                    ),
                  ),
                  IconButton(
                    tooltip: '表情',
                    onPressed: () {
                      setState(() => _showEmoji = !_showEmoji);
                      if (_showEmoji) _inputFocus.unfocus();
                      _scheduleScrollToBottom(animate: false);
                    },
                    icon: Icon(
                      _showEmoji
                          ? Icons.keyboard_outlined
                          : Icons.emoji_emotions_outlined,
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
          if (_showEmoji) _EmojiPanel(onSelected: _insertEmoji),
        ],
      ),
    );
  }
}

String _friendlyError(Object error) {
  if (error is ApiException) return error.message;
  return '网络连接失败，请检查网络后重试';
}

const customerChatEmojis = <String>[
  '😀',
  '😁',
  '😂',
  '🤣',
  '😃',
  '😄',
  '😅',
  '😆',
  '😊',
  '🙂',
  '🙃',
  '😉',
  '😍',
  '🥰',
  '😘',
  '😋',
  '😎',
  '🤩',
  '🥳',
  '🤗',
  '🤭',
  '🤔',
  '🫡',
  '🤐',
  '😴',
  '😪',
  '😢',
  '😭',
  '😤',
  '😡',
  '😱',
  '😇',
  '👍',
  '👎',
  '👌',
  '✌️',
  '🤞',
  '🤟',
  '🤝',
  '👏',
  '🙌',
  '🙏',
  '💪',
  '👊',
  '👋',
  '🫶',
  '❤️',
  '🧡',
  '💛',
  '💚',
  '💙',
  '💜',
  '🤍',
  '💯',
  '✨',
  '🔥',
  '⭐',
  '🌟',
  '💡',
  '🎉',
  '🎊',
  '🎁',
  '🎂',
  '🌹',
  '💐',
  '🌈',
  '☀️',
  '🌙',
  '☁️',
  '❄️',
  '⚡',
  '☔',
  '🍚',
  '🍜',
  '🥟',
  '🍲',
  '🍔',
  '🍟',
  '🍕',
  '🥗',
  '🍣',
  '🍱',
  '🍰',
  '🍦',
  '🍭',
  '☕',
  '🥤',
  '🧋',
  '🍎',
  '🍓',
  '🍉',
  '🍇',
  '🥭',
  '🍊',
  '🍌',
  '🥝',
  '✅',
  '❌',
  '❓',
  '❗',
  '⚠️',
  '📍',
  '📌',
  '💬',
  '📷',
  '🎵',
  '🏠',
  '🏪',
  '🚗',
  '🚀',
  '⏰',
  '💰',
];

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
        itemCount: customerChatEmojis.length,
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 8,
          mainAxisSpacing: 8,
          crossAxisSpacing: 8,
        ),
        itemBuilder: (_, index) => InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () => onSelected(customerChatEmojis[index]),
          child: Center(
            child: Text(
              customerChatEmojis[index],
              style: const TextStyle(fontSize: 25),
            ),
          ),
        ),
      ),
    ),
  );
}

class ChatMessageRow extends StatelessWidget {
  const ChatMessageRow({
    super.key,
    required this.message,
    required this.mine,
    required this.groupChat,
    this.fallbackAvatarUrl,
    this.fallbackName,
    this.onAction,
    this.onQuoteTap,
    this.onReEdit,
    this.highlighted = false,
  });
  final ChatMessage message;
  final bool mine;
  final bool groupChat;
  final String? fallbackAvatarUrl;
  final String? fallbackName;
  final ValueChanged<String>? onAction;
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
    if (message.isRecalled) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 12, top: 2),
        child: Center(
          child: ChatActionOverlay(
            actions: const [ChatAction('delete', '删除', Icons.delete_outline)],
            onSelected: (action) => onAction?.call(action),
            child: InkWell(
              onTap: onReEdit,
              borderRadius: BorderRadius.circular(6),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 5,
                ),
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
            ),
          ),
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
    final Widget messageContent = message.isDishShare
        ? DishShareCard(message: message)
        : message.isOrderShare
        ? OrderShareCard(message: message)
        : message.isImageShare
        ? ChatImageCard(message: message, mine: mine)
        : message.isShopShare
        ? ShopShareCard(message: message)
        : message.isMomentShare
        ? MomentShareCard(message: message)
        : Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            constraints: const BoxConstraints(maxWidth: 280),
            decoration: BoxDecoration(
              color: mine ? const Color(0xFFF28C28) : const Color(0xFFF4F4F4),
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
                    color: mine ? Colors.white : const Color(0xFF242424),
                  ),
                ),
              ],
            ),
          );
    final isShareContent =
        message.isDishShare ||
        message.isOrderShare ||
        message.isImageShare ||
        message.isShopShare ||
        message.isMomentShare;
    final Widget actionContent =
        isShareContent && message.quotedMessageId != null
        ? Column(
            crossAxisAlignment: mine
                ? CrossAxisAlignment.end
                : CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 280),
                child: QuotedMessagePreview(
                  message: message,
                  mine: mine,
                  onTap: onQuoteTap,
                ),
              ),
              messageContent,
            ],
          )
        : messageContent;
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
            if (message.deliveryFailed) ...[
              const Icon(Icons.error, color: Colors.red, size: 18),
              const SizedBox(width: 5),
            ],
            Flexible(
              child: ChatActionOverlay(
                actions: [
                  if (!message.isRecalled)
                    const ChatAction('quote', '引用', Icons.format_quote),
                  if (mine && !message.isRecalled)
                    const ChatAction('recall', '撤回', Icons.undo),
                  const ChatAction('delete', '删除', Icons.delete_outline),
                ],
                onSelected: (action) => onAction?.call(action),
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
                    : actionContent,
              ),
            ),
          ],
        ),
        if (message.essence)
          const Padding(
            padding: EdgeInsets.only(top: 4),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.auto_awesome, size: 13, color: Color(0xFFE08222)),
                SizedBox(width: 3),
                Text(
                  '精华',
                  style: TextStyle(fontSize: 11, color: Color(0xFFE08222)),
                ),
              ],
            ),
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
    final animatedBubble = TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.18, end: 1),
      duration: const Duration(milliseconds: 320),
      curve: Curves.easeOutBack,
      builder: (context, value, child) => Transform.scale(
        scale: value,
        alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
        child: child,
      ),
      child: bubble,
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
            ? [
                Flexible(child: animatedBubble),
                const SizedBox(width: 8),
                avatar,
              ]
            : [
                avatar,
                const SizedBox(width: 8),
                Flexible(child: animatedBubble),
              ],
      ),
    );
  }
}

class ChatImageCard extends StatelessWidget {
  const ChatImageCard({super.key, required this.message, this.mine = false});
  final ChatMessage message;
  final bool mine;

  @override
  Widget build(BuildContext context) {
    final expired = message.imageExpired || (message.imageUrl ?? '').isEmpty;
    final hasText = message.content.trim().isNotEmpty;
    final hasBubble = hasText || message.quotedMessageId != null || expired;
    return Container(
      width: 240,
      padding: hasBubble ? const EdgeInsets.all(8) : EdgeInsets.zero,
      decoration: BoxDecoration(
        color: hasBubble
            ? (mine ? const Color(0xFFF28C28) : const Color(0xFFF4F4F4))
            : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (hasText) ...[
            Padding(
              padding: const EdgeInsets.fromLTRB(3, 2, 3, 8),
              child: Text(
                message.content,
                style: TextStyle(color: mine ? Colors.white : null),
              ),
            ),
          ],
          if (expired)
            const SizedBox(
              height: 88,
              child: Center(
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.broken_image_outlined, color: Color(0xFF999999)),
                    SizedBox(width: 7),
                    Text('图片资源已过期', style: TextStyle(color: Color(0xFF777777))),
                  ],
                ),
              ),
            )
          else
            GestureDetector(
              onTap: () => _openCustomerChatImage(context, message.imageUrl!),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(7),
                child: Image.network(
                  resolveCustomerImageUrl(message.imageUrl!),
                  headers: AppScope.of(context).api.token?.isNotEmpty == true
                      ? {
                          'Authorization':
                              'Bearer ${AppScope.of(context).api.token}',
                        }
                      : null,
                  width: hasBubble ? 224 : 240,
                  height: 180,
                  fit: BoxFit.cover,
                  loadingBuilder: (_, child, progress) => progress == null
                      ? child
                      : SoftImageLoadingPlaceholder(
                          width: hasBubble ? 224 : 240,
                          height: 180,
                        ),
                  errorBuilder: (_, _, _) => const SizedBox(
                    height: 88,
                    child: Center(child: Text('图片加载失败')),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

void _openCustomerChatImage(BuildContext context, String imageUrl) {
  final token = AppScope.of(context).api.token;
  openMomentImageViewer(
    context,
    images: [imageUrl],
    initialIndex: 0,
    resolveUrl: resolveCustomerImageUrl,
    headers: token?.isNotEmpty == true
        ? {'Authorization': 'Bearer $token'}
        : null,
  );
}

class MomentShareCard extends StatelessWidget {
  const MomentShareCard({super.key, required this.message});
  final ChatMessage message;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(8),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: message.sharedMomentId == null
          ? null
          : () => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => MomentDetailPage(
                  momentId: message.sharedMomentId!,
                  highlightCommentId: message.sharedMomentCommentId,
                ),
              ),
            ),
      child: Container(
        width: 270,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          border: Border.all(color: const Color(0xFFEAEAEA)),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            if (message.sharedMomentCoverUrl?.isNotEmpty == true) ...[
              ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: Image.network(
                  resolveCustomerImageUrl(message.sharedMomentCoverUrl!),
                  width: 66,
                  height: 66,
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) => const SizedBox.square(
                    dimension: 66,
                    child: ColoredBox(color: Color(0xFFF3F3F3)),
                  ),
                ),
              ),
              const SizedBox(width: 10),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        message.sharedMomentCommentId == null
                            ? Icons.dynamic_feed_outlined
                            : Icons.mode_comment_outlined,
                        size: 16,
                        color: Color(0xFFF28C28),
                      ),
                      const SizedBox(width: 5),
                      Expanded(
                        child: Text(
                          message.sharedMomentTitle ?? '动态',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                  Text(
                    message.sharedCommentPreview?.isNotEmpty == true
                        ? message.sharedCommentPreview!
                        : (message.sharedMomentPreview?.isNotEmpty == true
                              ? message.sharedMomentPreview!
                              : (message.sharedMomentContent ?? '')),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF666666),
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    message.sharedMomentCommentId == null
                        ? (message.sharedMomentAuthorName ?? '')
                        : '查看动态中的评论',
                    style: const TextStyle(
                      fontSize: 11,
                      color: Color(0xFF999999),
                    ),
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

class ShopShareCard extends StatelessWidget {
  const ShopShareCard({super.key, required this.message});
  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(left: 8),
      child: Material(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: message.sharedShopId == null
              ? null
              : () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) =>
                        ShopOrderPage(shopId: message.sharedShopId!),
                  ),
                ),
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
                          resolveCustomerImageUrl(message.sharedShopLogoUrl!),
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
                      if (message.sharedShopDescription?.isNotEmpty ==
                          true) ...[
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
      ),
    );
  }
}

class DishShareCard extends StatelessWidget {
  const DishShareCard({super.key, required this.message});
  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(left: 8),
      child: Material(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
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
                          resolveCustomerImageUrl(message.dishImageUrl!),
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
                            message.dishSales == null
                                ? ''
                                : '已售${message.dishSales}',
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
      ),
    );
  }
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
    } else if (quoted?.isImageShare == true) {
      content = ChatImageCard(message: quoted!);
    } else if (quoted?.isShopShare == true) {
      content = ShopShareCard(message: quoted!);
    } else if (quoted?.isMomentShare == true) {
      content = MomentShareCard(message: quoted!);
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
