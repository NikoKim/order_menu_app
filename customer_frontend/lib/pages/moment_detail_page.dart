import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../main.dart';
import '../moment_models.dart';
import '../services/api_client.dart';
import '../services/moment_api.dart';
import '../services/moment_image.dart';
import '../widgets/app_notice.dart';
import '../widgets/moment_image_viewer.dart';
import '../widgets/moment_detail_post.dart';
import '../widgets/ui_utils.dart';
import '../utils/moment_time_formatter.dart';
import 'moment_privacy_page.dart';

class MomentDetailPage extends StatefulWidget {
  const MomentDetailPage({
    super.key,
    required this.momentId,
    this.highlightCommentId,
  });
  final int momentId;
  final int? highlightCommentId;
  @override
  State<MomentDetailPage> createState() => _MomentDetailPageState();
}

class _MomentDetailPageState extends State<MomentDetailPage> {
  final _scroll = ScrollController();
  final _comment = TextEditingController();
  final _commentFocus = FocusNode();
  final _commentsSectionKey = GlobalKey();
  final _commentKeys = <int, GlobalKey>{};
  final _replyPages = <int, int>{};
  final _expandedReplies = <int, List<MomentComment>>{};
  late Future<Moment> _momentFuture;
  List<MomentComment> _comments = const [];
  int _page = 1;
  bool _hasMore = true;
  bool _loadingComments = false;
  bool _sending = false;
  MomentComment? _replyingTo;
  XFile? _image;

  late CustomerApi _api;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();

    _scroll.addListener(() {
      if (_scroll.position.extentAfter < 300 && _hasMore) _loadComments();
    });
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _loadComments(refresh: true);
      if (widget.highlightCommentId != null) await _loadUntilHighlight();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_initialized) {
      _initialized = true;
      // 在这里安全地获取 context 并发起网络请求
      _api = CustomerApi(AppScope.of(context).api);
      _momentFuture = _api.moment(widget.momentId);
    }
  }

  @override
  void dispose() {
    _scroll.dispose();
    _comment.dispose();
    _commentFocus.dispose();
    super.dispose();
  }

  Future<void> _loadComments({bool refresh = false}) async {
    if (_loadingComments) return;
    setState(() => _loadingComments = true);
    try {
      final next = refresh ? 1 : _page;
      final result = await _api.momentComments(widget.momentId, page: next);
      if (!mounted) return;
      setState(() {
        _comments = refresh ? result.items : [..._comments, ...result.items];
        _page = next + 1;
        _hasMore = result.hasMore;
        _loadingComments = false;
      });
    } catch (e) {
      if (mounted) setState(() => _loadingComments = false);
    }
  }

  bool _hasHighlightLoaded() {
    final target = widget.highlightCommentId;
    if (target == null) return true;
    for (final comment in _comments) {
      if (comment.id == target ||
          comment.replies.any((item) => item.id == target)) {
        return true;
      }
      if (_expandedReplies[comment.id]?.any((item) => item.id == target) ==
          true) {
        return true;
      }
    }
    return false;
  }

  Future<void> _loadUntilHighlight() async {
    while (mounted && !_hasHighlightLoaded() && _hasMore) {
      final pageBefore = _page;
      await _loadComments();
      if (_page == pageBefore) break;
    }
    if (!_hasHighlightLoaded()) {
      for (final comment in _comments) {
        if (comment.replyCount <= comment.replies.length) continue;
        var page = 1;
        var hasMore = true;
        final replies = <MomentComment>[...comment.replies];
        final ids = replies.map((item) => item.id).toSet();
        while (mounted && !_hasHighlightLoaded() && hasMore) {
          final result = await _api.commentReplies(
            widget.momentId,
            comment.id,
            page: page,
          );
          for (final reply in result.items) {
            if (ids.add(reply.id)) replies.add(reply);
          }
          if (!mounted) return;
          setState(() {
            _expandedReplies[comment.id] = replies.toList();
            _replyPages[comment.id] = page + 1;
          });
          hasMore = result.hasMore;
          page++;
        }
        if (_hasHighlightLoaded()) break;
      }
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToHighlight());
  }

  void _scrollToHighlight() {
    final key = _commentKeys[widget.highlightCommentId];
    final target = key?.currentContext;
    if (target != null)
      Scrollable.ensureVisible(
        target,
        duration: const Duration(milliseconds: 500),
        curve: Curves.easeOutCubic,
        alignment: .3,
      );
  }

  Future<void> _loadReplies(MomentComment comment) async {
    final current = _expandedReplies[comment.id];
    final page = _replyPages[comment.id] ?? 1;
    try {
      final result = await _api.commentReplies(
        widget.momentId,
        comment.id,
        page: page,
      );
      if (!mounted) return;
      setState(() {
        _expandedReplies[comment.id] = [...?current, ...result.items];
        _replyPages[comment.id] = page + 1;
      });
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    }
  }

  Future<void> _send() async {
    if (_sending || (_comment.text.trim().isEmpty && _image == null)) return;
    setState(() => _sending = true);
    try {
      String? imageUrl;
      if (_image != null) {
        final path = await prepareMomentImage(_image!.path, comment: true);
        imageUrl = await _api.uploadMomentCommentImage(path);
      }
      final root = _replyingTo?.parentId ?? _replyingTo?.id;
      await _api.addMomentComment(
        widget.momentId,
        content: _comment.text,
        imageUrl: imageUrl,
        parentId: root,
        replyToCommentId: _replyingTo?.id,
      );
      _comment.clear();
      _commentFocus.unfocus();
      if (mounted)
        setState(() {
          _replyingTo = null;
          _image = null;
        });
      await _loadComments(refresh: true);
      _refreshMoment();
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _delete(MomentComment comment) async {
    final confirmed = await confirmDestructiveAction(
      context,
      title: comment.parentId == null ? '删除评论？' : '删除回复？',
      message: comment.parentId == null
          ? '这条评论和其下的回复将被删除，且无法恢复。'
          : '这条回复将被删除，且无法恢复。',
    );
    if (!confirmed || !mounted) return;
    try {
      await _api.deleteMomentComment(widget.momentId, comment.id);
      await _loadComments(refresh: true);
      _refreshMoment();
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    }
  }

  Future<void> _likeComment(MomentComment comment) async {
    try {
      await _api.setCommentLiked(widget.momentId, comment.id, !comment.liked);
      await _loadComments(refresh: true);
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    }
  }

  Future<void> _shareComment(MomentComment comment) async {
    try {
      final conversations = await _api.conversations();
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        builder: (sheetContext) => SafeArea(
          child: SizedBox(
            height: 420,
            child: Column(
              children: [
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text(
                    '分享评论到聊天',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                ),
                Expanded(
                  child: conversations.isEmpty
                      ? const Center(child: Text('暂无可分享的聊天'))
                      : ListView.builder(
                          itemCount: conversations.length,
                          itemBuilder: (_, index) {
                            final item = conversations[index];
                            return ListTile(
                              leading: const CircleAvatar(
                                child: Icon(Icons.chat_bubble_outline),
                              ),
                              title: Text(item.title),
                              enabled: item.isActive,
                              onTap: () async {
                                try {
                                  await _api.shareMoment(
                                    conversationId: item.id,
                                    momentId: widget.momentId,
                                    commentId: comment.id,
                                  );
                                  if (!sheetContext.mounted) return;
                                  Navigator.pop(sheetContext);
                                  if (mounted)
                                    AppNotice.show(context, '评论已分享到聊天');
                                } on ApiException catch (e) {
                                  if (mounted)
                                    AppNotice.show(context, e.message);
                                }
                              },
                            );
                          },
                        ),
                ),
              ],
            ),
          ),
        ),
      );
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    }
  }

  void _reply(MomentComment comment) {
    setState(() => _replyingTo = comment);
    _commentFocus.requestFocus();
  }

  void _setMoment(Moment value) {
    if (!mounted) return;
    final future = Future<Moment>.value(value);
    setState(() {
      _momentFuture = future;
    });
  }

  void _refreshMoment() {
    if (!mounted) return;
    final future = _api.moment(widget.momentId);
    setState(() {
      _momentFuture = future;
    });
  }

  void _scrollToComments() {
    final target = _commentsSectionKey.currentContext;
    if (target == null) return;
    Scrollable.ensureVisible(
      target,
      duration: const Duration(milliseconds: 420),
      curve: Curves.easeOutCubic,
      alignment: 0.04,
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF6F7F8),
    appBar: AppBar(
      title: const Text('动态详情', style: TextStyle(fontWeight: FontWeight.w700)),
      centerTitle: false,
      backgroundColor: Colors.white,
      foregroundColor: const Color(0xFF202124),
      scrolledUnderElevation: 0,
      surfaceTintColor: Colors.transparent,
    ),
    body: FutureBuilder<Moment>(
      future: _momentFuture,
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 36),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: const BoxDecoration(
                        color: Color(0xFFFFF0E2),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.article_outlined,
                        size: 34,
                        color: Color(0xFFE77910),
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '动态暂时无法查看',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF202124),
                      ),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      '它可能已被删除，也可能是网络暂时不可用',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 13,
                        height: 1.45,
                        color: Color(0xFF8A8F98),
                      ),
                    ),
                    const SizedBox(height: 14),
                    OutlinedButton.icon(
                      onPressed: _refreshMoment,
                      icon: const Icon(Icons.refresh_rounded, size: 18),
                      label: const Text('重新加载'),
                    ),
                  ],
                ),
              ),
            );
          }
          return const MomentFeedSkeleton(itemCount: 2);
        }
        return ListView(
          controller: _scroll,
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 28),
          children: [
            MomentDetailPost(
              moment: snapshot.data!,
              onChanged: _setMoment,
              onDeleted: () {
                if (Navigator.of(context).canPop()) Navigator.of(context).pop();
              },
              onCommentTap: _scrollToComments,
            ),
            const SizedBox(height: 12),
            Container(
              key: _commentsSectionKey,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 16, 18, 14),
                    child: Row(
                      children: [
                        const Text(
                          '全部评论',
                          style: TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 18,
                            color: Color(0xFF202124),
                          ),
                        ),
                        const SizedBox(width: 8),
                        if (snapshot.data!.commentCount > 0)
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 7,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF1F2F4),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              '${snapshot.data!.commentCount}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: Color(0xFF70757A),
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const Divider(height: 1, color: Color(0xFFEEEEEE)),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                    child: Column(
                      children: [
                        if (_comments.isEmpty && !_loadingComments)
                          const _EmptyComments(),
                        if (_comments.isEmpty && _loadingComments)
                          const Column(
                            children: [
                              CommentSkeleton(),
                              CommentSkeleton(),
                              CommentSkeleton(),
                            ],
                          ),
                        for (final comment in _comments)
                          _CommentTile(
                            key: _commentKeys.putIfAbsent(
                              comment.id,
                              GlobalKey.new,
                            ),
                            comment: comment,
                            highlighted:
                                widget.highlightCommentId == comment.id,
                            highlightCommentId: widget.highlightCommentId,
                            commentKeys: _commentKeys,
                            replies:
                                _expandedReplies[comment.id] ??
                                comment.replies.take(3).toList(),
                            expanded: _expandedReplies.containsKey(comment.id),
                            onReply: _reply,
                            onDelete: _delete,
                            onLike: _likeComment,
                            onShare: _shareComment,
                            onMoreReplies: () => _loadReplies(comment),
                            onCollapse: () => setState(() {
                              _expandedReplies.remove(comment.id);
                              _replyPages.remove(comment.id);
                            }),
                          ),
                        if (_loadingComments && _comments.isNotEmpty)
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 16),
                            child: SizedBox.square(
                              dimension: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    ),
    bottomNavigationBar: _CommentComposerBar(
      controller: _comment,
      focusNode: _commentFocus,
      replyingTo: _replyingTo,
      image: _image,
      sending: _sending,
      onCancelReply: () => setState(() => _replyingTo = null),
      onClearImage: () => setState(() => _image = null),
      onPickImage: () async {
        final image = await ImagePicker().pickImage(
          source: ImageSource.gallery,
          imageQuality: 86,
        );
        if (mounted) setState(() => _image = image);
      },
      onSend: _send,
    ),
  );
}

class _EmptyComments extends StatelessWidget {
  const _EmptyComments();

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: 34),
    child: Center(
      child: Column(
        children: [
          Icon(Icons.mode_comment_outlined, size: 34, color: Color(0xFFB7BBC1)),
          SizedBox(height: 10),
          Text(
            '还没有评论',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Color(0xFF70757A),
            ),
          ),
          SizedBox(height: 3),
          Text(
            '来说说你的想法吧',
            style: TextStyle(fontSize: 12, color: Color(0xFFA0A4AA)),
          ),
        ],
      ),
    ),
  );
}

class _CommentComposerBar extends StatelessWidget {
  const _CommentComposerBar({
    required this.controller,
    required this.focusNode,
    required this.replyingTo,
    required this.image,
    required this.sending,
    required this.onCancelReply,
    required this.onClearImage,
    required this.onPickImage,
    required this.onSend,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final MomentComment? replyingTo;
  final XFile? image;
  final bool sending;
  final VoidCallback onCancelReply;
  final VoidCallback onClearImage;
  final VoidCallback onPickImage;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) => SafeArea(
    top: false,
    child: Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFE8E9EB))),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (replyingTo != null)
            Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.fromLTRB(11, 7, 5, 7),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF4EA),
                borderRadius: BorderRadius.circular(9),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.reply_rounded,
                    size: 16,
                    color: Color(0xFFE77910),
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      '正在回复 ${replyingTo!.nickname}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF9A5314),
                      ),
                    ),
                  ),
                  InkWell(
                    onTap: onCancelReply,
                    borderRadius: BorderRadius.circular(16),
                    child: const Padding(
                      padding: EdgeInsets.all(5),
                      child: Icon(
                        Icons.close_rounded,
                        size: 17,
                        color: Color(0xFF8A8F98),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          if (image != null)
            Align(
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(9),
                      child: Image.file(
                        File(image!.path),
                        width: 66,
                        height: 66,
                        fit: BoxFit.cover,
                      ),
                    ),
                    Positioned(
                      right: -7,
                      top: -7,
                      child: Material(
                        color: const Color(0xFF4D5156),
                        shape: const CircleBorder(),
                        child: InkWell(
                          onTap: onClearImage,
                          customBorder: const CircleBorder(),
                          child: const Padding(
                            padding: EdgeInsets.all(3),
                            child: Icon(
                              Icons.close_rounded,
                              size: 14,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Material(
                color: const Color(0xFFF3F4F5),
                borderRadius: BorderRadius.circular(12),
                child: InkWell(
                  onTap: onPickImage,
                  borderRadius: BorderRadius.circular(12),
                  child: const SizedBox.square(
                    dimension: 44,
                    child: Icon(
                      Icons.image_outlined,
                      size: 22,
                      color: Color(0xFF4D5156),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  controller: controller,
                  focusNode: focusNode,
                  minLines: 1,
                  maxLines: 4,
                  textInputAction: TextInputAction.newline,
                  decoration: InputDecoration(
                    hintText: replyingTo == null
                        ? '写评论或回复…'
                        : '回复 ${replyingTo!.nickname}…',
                    hintStyle: const TextStyle(
                      fontSize: 14,
                      color: Color(0xFF9AA0A6),
                    ),
                    filled: true,
                    fillColor: const Color(0xFFF6F7F8),
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(
                        color: Color(0xFFF28C28),
                        width: 1.2,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              ValueListenableBuilder<TextEditingValue>(
                valueListenable: controller,
                builder: (_, value, __) {
                  final enabled =
                      !sending &&
                      (value.text.trim().isNotEmpty || image != null);
                  return Material(
                    color: enabled
                        ? const Color(0xFFF28C28)
                        : const Color(0xFFE3E5E8),
                    shape: const CircleBorder(),
                    child: InkWell(
                      onTap: enabled ? onSend : null,
                      customBorder: const CircleBorder(),
                      child: SizedBox.square(
                        dimension: 44,
                        child: sending
                            ? const Padding(
                                padding: EdgeInsets.all(12),
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : Icon(
                                Icons.arrow_upward_rounded,
                                size: 23,
                                color: enabled
                                    ? Colors.white
                                    : const Color(0xFF9AA0A6),
                              ),
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
        ],
      ),
    ),
  );
}

class _CommentTile extends StatefulWidget {
  const _CommentTile({
    super.key,
    required this.comment,
    required this.highlighted,
    required this.highlightCommentId,
    required this.commentKeys,
    required this.replies,
    required this.expanded,
    required this.onReply,
    required this.onDelete,
    required this.onLike,
    required this.onShare,
    required this.onMoreReplies,
    required this.onCollapse,
  });
  final MomentComment comment;
  final bool highlighted;
  final int? highlightCommentId;
  final Map<int, GlobalKey> commentKeys;
  final List<MomentComment> replies;
  final bool expanded;
  final ValueChanged<MomentComment> onReply;
  final ValueChanged<MomentComment> onDelete;
  final ValueChanged<MomentComment> onLike;
  final ValueChanged<MomentComment> onShare;
  final VoidCallback onMoreReplies;
  final VoidCallback onCollapse;
  @override
  State<_CommentTile> createState() => _CommentTileState();
}

class _CommentTileState extends State<_CommentTile>
    with SingleTickerProviderStateMixin {
  bool _contentExpanded = false;
  late final AnimationController _highlight = AnimationController(
    vsync: this,
    value: widget.highlighted ? 0.0 : 1.0,
    duration: const Duration(milliseconds: 1000),
  );
  @override
  void initState() {
    super.initState();
    if (widget.highlighted) _highlight.forward();
  }

  @override
  void didUpdateWidget(covariant _CommentTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.highlighted && !oldWidget.highlighted) {
      _highlight.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _highlight.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _highlight,
    builder: (_, child) => DecoratedBox(
      decoration: BoxDecoration(
        color: Color.lerp(
          const Color(0xFFFFF0DE),
          Colors.transparent,
          _highlight.value,
        ),
        borderRadius: BorderRadius.circular(8),
      ),
      child: child,
    ),
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => _openCommentAuthor(context, widget.comment),
            customBorder: const CircleBorder(),
            child: CircleAvatar(
              radius: 18,
              backgroundColor: const Color(0xFFFFE6CF),
              backgroundImage: widget.comment.avatarUrl?.isNotEmpty == true
                  ? NetworkImage(
                      resolveCustomerImageUrl(widget.comment.avatarUrl!),
                    )
                  : null,
              child: widget.comment.avatarUrl?.isNotEmpty == true
                  ? null
                  : const Icon(
                      Icons.person_outline_rounded,
                      size: 18,
                      color: Color(0xFFA85A18),
                    ),
            ),
          ),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(
                      child: Wrap(
                        spacing: 5,
                        runSpacing: 3,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          InkWell(
                            onTap: () =>
                                _openCommentAuthor(context, widget.comment),
                            child: Text(
                              widget.comment.nickname,
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF202124),
                              ),
                            ),
                          ),
                          if (widget.comment.merchant)
                            const _CommentBadge(text: '商家'),
                          if (widget.comment.author)
                            const _CommentBadge(text: '作者'),
                        ],
                      ),
                    ),
                    PopupMenuButton<String>(
                      tooltip: '评论操作',
                      padding: EdgeInsets.zero,
                      iconSize: 20,
                      icon: const Icon(
                        Icons.more_horiz_rounded,
                        color: Color(0xFF8A8F98),
                      ),
                      onSelected: (value) {
                        if (value == 'share') widget.onShare(widget.comment);
                        if (value == 'delete') widget.onDelete(widget.comment);
                      },
                      itemBuilder: (_) => [
                        const PopupMenuItem(
                          value: 'share',
                          child: Text('分享到聊天'),
                        ),
                        if (widget.comment.mine ||
                            AppScope.of(context).auth.user?.id ==
                                widget.comment.userId)
                          const PopupMenuItem(
                            value: 'delete',
                            child: Text('删除评论'),
                          ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 7),
                Text(
                  widget.comment.content,
                  maxLines: _contentExpanded ? null : 8,
                  overflow: _contentExpanded ? null : TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    height: 1.5,
                    color: Color(0xFF303134),
                  ),
                ),
                if (widget.comment.content.length > 240)
                  InkWell(
                    onTap: () =>
                        setState(() => _contentExpanded = !_contentExpanded),
                    borderRadius: BorderRadius.circular(6),
                    child: Padding(
                      padding: const EdgeInsets.only(top: 6, bottom: 2),
                      child: Text(
                        _contentExpanded ? '收起' : '展开评论',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFFE57300),
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                if (widget.comment.imageUrl?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(top: 10),
                    child: MomentImagePreview(
                      images: [widget.comment.imageUrl!],
                      index: 0,
                      resolveUrl: resolveCustomerImageUrl,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: Image.network(
                          resolveCustomerImageUrl(widget.comment.imageUrl!),
                          width: 148,
                          height: 148,
                          fit: BoxFit.cover,
                          loadingBuilder: (_, child, progress) =>
                              progress == null
                              ? child
                              : const SoftImageLoadingPlaceholder(
                                  width: 148,
                                  height: 148,
                                ),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        formatMomentTime(widget.comment.createdAt),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          color: Color(0xFF9AA0A6),
                        ),
                      ),
                    ),
                    _CommentAction(
                      icon: Icons.reply_rounded,
                      label: '回复',
                      onTap: () => widget.onReply(widget.comment),
                    ),
                    const SizedBox(width: 2),
                    _CommentAction(
                      icon: widget.comment.liked
                          ? Icons.favorite_rounded
                          : Icons.favorite_border_rounded,
                      label: '${widget.comment.likeCount}',
                      active: widget.comment.liked,
                      onTap: () => widget.onLike(widget.comment),
                    ),
                  ],
                ),
                if (widget.replies.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 9),
                    child: _ReplyThread(
                      replies: widget.replies,
                      highlightCommentId: widget.highlightCommentId,
                      commentKeys: widget.commentKeys,
                      onReply: widget.onReply,
                      onLike: widget.onLike,
                      onShare: widget.onShare,
                      onDelete: widget.onDelete,
                    ),
                  ),
                if (widget.comment.replyCount > widget.replies.length ||
                    widget.expanded)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Row(
                      children: [
                        if (widget.comment.replyCount > widget.replies.length)
                          TextButton.icon(
                            style: TextButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 4,
                              ),
                              minimumSize: const Size(0, 34),
                              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            ),
                            onPressed: widget.onMoreReplies,
                            icon: const Icon(
                              Icons.keyboard_arrow_down_rounded,
                              size: 17,
                            ),
                            label: Text(
                              '更多回复（${widget.comment.replyCount - widget.replies.length}）',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        if (widget.expanded)
                          TextButton(
                            style: TextButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                              ),
                              minimumSize: const Size(0, 34),
                              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            ),
                            onPressed: widget.onCollapse,
                            child: const Text(
                              '收起',
                              style: TextStyle(fontSize: 12),
                            ),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _ReplyThread extends StatelessWidget {
  const _ReplyThread({
    required this.replies,
    required this.highlightCommentId,
    required this.commentKeys,
    required this.onReply,
    required this.onLike,
    required this.onShare,
    required this.onDelete,
  });

  final List<MomentComment> replies;
  final int? highlightCommentId;
  final Map<int, GlobalKey> commentKeys;
  final ValueChanged<MomentComment> onReply;
  final ValueChanged<MomentComment> onLike;
  final ValueChanged<MomentComment> onShare;
  final ValueChanged<MomentComment> onDelete;

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      const Positioned(
        left: 13,
        top: 3,
        bottom: 10,
        child: VerticalDivider(
          width: 1,
          thickness: 1,
          color: Color(0xFFE1E4E8),
        ),
      ),
      Padding(
        padding: const EdgeInsets.only(left: 13),
        child: Column(
          children: [
            for (final reply in replies)
              _ReplyLine(
                key: commentKeys.putIfAbsent(reply.id, GlobalKey.new),
                reply: reply,
                highlighted: highlightCommentId == reply.id,
                onReply: onReply,
                onLike: onLike,
                onShare: onShare,
                onDelete: onDelete,
              ),
          ],
        ),
      ),
    ],
  );
}

class _ReplyLine extends StatelessWidget {
  const _ReplyLine({
    super.key,
    required this.reply,
    required this.highlighted,
    required this.onReply,
    required this.onLike,
    required this.onShare,
    required this.onDelete,
  });

  final MomentComment reply;
  final bool highlighted;
  final ValueChanged<MomentComment> onReply;
  final ValueChanged<MomentComment> onLike;
  final ValueChanged<MomentComment> onShare;
  final ValueChanged<MomentComment> onDelete;

  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
    tween: Tween(begin: highlighted ? 0 : 1, end: 1),
    duration: const Duration(milliseconds: 1100),
    builder: (_, value, child) => DecoratedBox(
      decoration: BoxDecoration(
        color: Color.lerp(const Color(0xFFFFE8C8), Colors.transparent, value),
        borderRadius: BorderRadius.circular(8),
      ),
      child: child,
    ),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 0, 9),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => _openCommentAuthor(context, reply),
            customBorder: const CircleBorder(),
            child: CircleAvatar(
              radius: 14,
              backgroundColor: const Color(0xFFE9ECEF),
              backgroundImage: reply.avatarUrl?.isNotEmpty == true
                  ? NetworkImage(resolveCustomerImageUrl(reply.avatarUrl!))
                  : null,
              child: reply.avatarUrl?.isNotEmpty == true
                  ? null
                  : const Icon(
                      Icons.person_outline_rounded,
                      size: 15,
                      color: Color(0xFF70757A),
                    ),
            ),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(
                      child: Wrap(
                        spacing: 4,
                        runSpacing: 3,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          InkWell(
                            onTap: () => _openCommentAuthor(context, reply),
                            child: Text(
                              reply.nickname,
                              style: const TextStyle(
                                fontSize: 12.5,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF303134),
                              ),
                            ),
                          ),
                          if (reply.merchant) const _CommentBadge(text: '商家'),
                          if (reply.author) const _CommentBadge(text: '作者'),
                        ],
                      ),
                    ),
                    PopupMenuButton<String>(
                      tooltip: '回复操作',
                      padding: EdgeInsets.zero,
                      iconSize: 17,
                      constraints: const BoxConstraints(
                        minWidth: 180,
                        maxWidth: 240,
                      ),
                      icon: const Icon(
                        Icons.more_horiz_rounded,
                        color: Color(0xFF8A8F98),
                      ),
                      onSelected: (value) {
                        if (value == 'share') onShare(reply);
                        if (value == 'delete') onDelete(reply);
                      },
                      itemBuilder: (_) => [
                        const PopupMenuItem(
                          value: 'share',
                          child: Text('分享到聊天'),
                        ),
                        if (reply.mine ||
                            AppScope.of(context).auth.user?.id == reply.userId)
                          const PopupMenuItem(
                            value: 'delete',
                            child: Text('删除回复'),
                          ),
                      ],
                    ),
                  ],
                ),
                if (reply.replyToNickname?.isNotEmpty == true) ...[
                  const SizedBox(height: 2),
                  Text(
                    '回复 ${reply.replyToNickname}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 10.5,
                      color: Color(0xFF8A8F98),
                    ),
                  ),
                ],
                const SizedBox(height: 5),
                Text(
                  reply.content,
                  style: const TextStyle(
                    fontSize: 13,
                    height: 1.45,
                    color: Color(0xFF3C4043),
                  ),
                ),
                if (reply.imageUrl?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: MomentImagePreview(
                      images: [reply.imageUrl!],
                      index: 0,
                      resolveUrl: resolveCustomerImageUrl,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.network(
                          resolveCustomerImageUrl(reply.imageUrl!),
                          width: 112,
                          height: 112,
                          fit: BoxFit.cover,
                          loadingBuilder: (_, child, progress) =>
                              progress == null
                              ? child
                              : const SoftImageLoadingPlaceholder(
                                  width: 112,
                                  height: 112,
                                ),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 5),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        formatMomentTime(reply.createdAt),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 10,
                          color: Color(0xFF9AA0A6),
                        ),
                      ),
                    ),
                    _CommentAction(
                      icon: Icons.reply_rounded,
                      label: '回复',
                      compact: true,
                      onTap: () => onReply(reply),
                    ),
                    _CommentAction(
                      icon: reply.liked
                          ? Icons.favorite_rounded
                          : Icons.favorite_border_rounded,
                      label: '${reply.likeCount}',
                      active: reply.liked,
                      compact: true,
                      onTap: () => onLike(reply),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _CommentBadge extends StatelessWidget {
  const _CommentBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
    decoration: BoxDecoration(
      color: const Color(0xFFFFEBDD),
      borderRadius: BorderRadius.circular(5),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 9,
        height: 1,
        color: Color(0xFFB75E0A),
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}

class _CommentAction extends StatelessWidget {
  const _CommentAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.active = false,
    this.compact = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool active;
  final bool compact;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: compact ? 5 : 7,
          vertical: compact ? 4 : 6,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: compact ? 14 : 16,
              color: active ? const Color(0xFFE85D4A) : const Color(0xFF70757A),
            ),
            const SizedBox(width: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: compact ? 10 : 11,
                color: active
                    ? const Color(0xFFE85D4A)
                    : const Color(0xFF70757A),
                fontWeight: active ? FontWeight.w600 : FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

void _openCommentAuthor(BuildContext context, MomentComment comment) {
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) =>
          UserMomentsPage(userId: comment.userId, nickname: comment.nickname),
    ),
  );
}
