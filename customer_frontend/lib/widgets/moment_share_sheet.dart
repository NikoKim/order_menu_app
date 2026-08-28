import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../moment_models.dart';
import '../services/api_client.dart';
import '../services/moment_api.dart';
import '../services/moment_external_share.dart';
import 'app_notice.dart';
import 'moment_image_viewer.dart';

enum _MomentShareView { options, chat, external }

Future<void> showMomentShareSheet(
  BuildContext context, {
  required Moment moment,
  MomentComment? comment,
  int? commentId,
}) {
  final api = CustomerApi(AppScope.of(context).api);
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(8)),
    ),
    builder: (_) => _MomentShareSheet(
      hostContext: context,
      api: api,
      moment: moment,
      comment: comment,
      commentId: commentId ?? comment?.id,
    ),
  );
}

class _MomentShareSheet extends StatefulWidget {
  const _MomentShareSheet({
    required this.hostContext,
    required this.api,
    required this.moment,
    required this.comment,
    required this.commentId,
  });

  final BuildContext hostContext;
  final CustomerApi api;
  final Moment moment;
  final MomentComment? comment;
  final int? commentId;

  @override
  State<_MomentShareSheet> createState() => _MomentShareSheetState();
}

class _MomentShareSheetState extends State<_MomentShareSheet> {
  _MomentShareView _view = _MomentShareView.options;
  List<ChatConversation> _conversations = const [];
  bool _loadingChats = false;
  bool _sharing = false;
  bool _isolated = false;

  bool get _sharingComment => widget.commentId != null;

  Future<void> _openChat() async {
    setState(() {
      _view = _MomentShareView.chat;
      _loadingChats = true;
    });
    try {
      final conversations = await widget.api.conversations();
      if (mounted) setState(() => _conversations = conversations);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _loadingChats = false);
    }
  }

  Future<void> _sendToChat(ChatConversation conversation) async {
    if (_sharing || !conversation.isActive) return;
    setState(() => _sharing = true);
    try {
      await widget.api.shareMoment(
        conversationId: conversation.id,
        momentId: widget.moment.id,
        commentId: widget.commentId,
      );
      if (!mounted) return;
      Navigator.pop(context);
      if (widget.hostContext.mounted) {
        AppNotice.show(
          widget.hostContext,
          _sharingComment ? '评论已分享到聊天' : '已分享到聊天',
        );
      }
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _sharing = false);
    }
  }

  Future<void> _shareExternal() async {
    final hostContext = widget.hostContext;
    final moment = widget.moment;
    final commentId = widget.commentId;
    final mode = _isolated
        ? MomentExternalShareMode.isolated
        : MomentExternalShareMode.browse;
    Navigator.pop(context);
    if (!hostContext.mounted) return;
    await shareMomentExternally(
      hostContext,
      moment,
      commentId: commentId,
      selectedMode: mode,
    );
  }

  @override
  Widget build(BuildContext context) {
    final maxHeight = MediaQuery.sizeOf(context).height * .78;
    final sheetHeight = maxHeight > 580 ? 580.0 : maxHeight;
    return SafeArea(
      child: SizedBox(
        height: sheetHeight,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 0, 18, 14),
          child: Column(
            children: [
              _ShareHeader(
                title: switch (_view) {
                  _MomentShareView.options => _sharingComment ? '分享评论' : '分享动态',
                  _MomentShareView.chat => '选择聊天',
                  _MomentShareView.external => '分享到其他应用',
                },
                showBack: _view != _MomentShareView.options,
                onBack: () => setState(() => _view = _MomentShareView.options),
              ),
              _MomentSharePreview(
                moment: widget.moment,
                comment: widget.comment,
              ),
              const SizedBox(height: 12),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 180),
                  child: switch (_view) {
                    _MomentShareView.options => _ShareMethodOptions(
                      key: const ValueKey('share-options'),
                      sharingComment: _sharingComment,
                      onChat: _openChat,
                      onExternal: () =>
                          setState(() => _view = _MomentShareView.external),
                    ),
                    _MomentShareView.chat => _buildChatList(),
                    _MomentShareView.external => _buildExternalOptions(),
                  },
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: TextButton(
                  style: TextButton.styleFrom(
                    minimumSize: const Size.fromHeight(44),
                    backgroundColor: const Color(0xFFF2F3F5),
                    foregroundColor: const Color(0xFF686D74),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(7),
                    ),
                  ),
                  onPressed: () {
                    if (_view == _MomentShareView.options) {
                      Navigator.pop(context);
                    } else {
                      setState(() => _view = _MomentShareView.options);
                    }
                  },
                  child: Text(_view == _MomentShareView.options ? '取消' : '返回'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildChatList() {
    if (_loadingChats) {
      return const Center(child: CircularProgressIndicator(strokeWidth: 2));
    }
    if (_conversations.isEmpty) {
      return const Center(
        key: ValueKey('share-chat-empty'),
        child: Text('暂无可用会话', style: TextStyle(color: Color(0xFF777B82))),
      );
    }
    return ListView.separated(
      key: const ValueKey('share-chat-list'),
      itemCount: _conversations.length,
      separatorBuilder: (_, _) =>
          const Divider(height: 1, indent: 58, color: Color(0xFFF0F1F3)),
      itemBuilder: (_, index) {
        final conversation = _conversations[index];
        final imageUrl = conversation.imageUrl;
        return ListTile(
          enabled: conversation.isActive && !_sharing,
          contentPadding: const EdgeInsets.symmetric(horizontal: 4),
          leading: CircleAvatar(
            radius: 21,
            backgroundColor: const Color(0xFFF0F1F3),
            backgroundImage: imageUrl?.isNotEmpty == true
                ? NetworkImage(resolveCustomerImageUrl(imageUrl!))
                : null,
            child: imageUrl?.isNotEmpty == true
                ? null
                : Icon(
                    conversation.isDirect
                        ? Icons.person_outline_rounded
                        : Icons.group_outlined,
                    color: const Color(0xFF777B82),
                  ),
          ),
          title: Text(
            conversation.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: const Icon(Icons.chevron_right_rounded, size: 20),
          onTap: () => _sendToChat(conversation),
        );
      },
    );
  }

  Widget _buildExternalOptions() => Column(
    key: const ValueKey('share-external-options'),
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      CheckboxListTile(
        value: _isolated,
        contentPadding: EdgeInsets.zero,
        controlAffinity: ListTileControlAffinity.leading,
        title: Text('只分享此${_sharingComment ? '评论' : '动态'}'),
        subtitle: const Text('勾选后，对方不能进入动态浏览页'),
        onChanged: (value) => setState(() => _isolated = value ?? false),
      ),
      const Spacer(),
      SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: _shareExternal,
          icon: const Icon(Icons.ios_share_rounded),
          label: Text('分享${_sharingComment ? '评论' : '动态'}'),
        ),
      ),
    ],
  );
}

class _ShareHeader extends StatelessWidget {
  const _ShareHeader({
    required this.title,
    required this.showBack,
    required this.onBack,
  });

  final String title;
  final bool showBack;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 48,
    child: Stack(
      alignment: Alignment.center,
      children: [
        if (showBack)
          Align(
            alignment: Alignment.centerLeft,
            child: IconButton(
              tooltip: '返回',
              onPressed: onBack,
              icon: const Icon(Icons.arrow_back_rounded),
            ),
          ),
        Text(
          title,
          style: const TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w700,
            color: Color(0xFF202124),
          ),
        ),
      ],
    ),
  );
}

class _MomentSharePreview extends StatelessWidget {
  const _MomentSharePreview({required this.moment, required this.comment});

  final Moment moment;
  final MomentComment? comment;

  @override
  Widget build(BuildContext context) {
    final imageUrl =
        comment?.imageUrls.firstOrNull ?? moment.images.firstOrNull;
    final content = comment?.content ?? moment.content;
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 76),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: const Color(0xFFF8F9FA),
        border: Border.all(color: const Color(0xFFECEEF1)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          if (imageUrl != null) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Image.network(
                resolveCustomerImageUrl(imageUrl),
                width: 68,
                height: 68,
                fit: BoxFit.cover,
                loadingBuilder: (_, child, progress) => progress == null
                    ? child
                    : const SoftImageLoadingPlaceholder(width: 68, height: 68),
                errorBuilder: (_, _, _) => const SizedBox.square(
                  dimension: 68,
                  child: ColoredBox(color: Color(0xFFE9EAEC)),
                ),
              ),
            ),
            const SizedBox(width: 11),
          ],
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  comment?.nickname ?? moment.authorNickname,
                  style: const TextStyle(
                    fontSize: 11,
                    color: Color(0xFF777B82),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  moment.title.isEmpty ? '分享了一条动态' : moment.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  content,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 12,
                    height: 1.4,
                    color: Color(0xFF777B82),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ShareMethodOptions extends StatelessWidget {
  const _ShareMethodOptions({
    super.key,
    required this.sharingComment,
    required this.onChat,
    required this.onExternal,
  });

  final bool sharingComment;
  final VoidCallback onChat;
  final VoidCallback onExternal;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      _ShareMethodTile(
        icon: Icons.chat_bubble_outline_rounded,
        title: '分享到聊天',
        subtitle: '发送${sharingComment ? '评论' : '动态'}卡片给好友或群聊',
        onTap: onChat,
      ),
      const SizedBox(height: 8),
      _ShareMethodTile(
        icon: Icons.ios_share_rounded,
        title: '分享到其他应用',
        subtitle: '选择分享后的浏览范围',
        onTap: onExternal,
      ),
    ],
  );
}

class _ShareMethodTile extends StatelessWidget {
  const _ShareMethodTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: Ink(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFECEEF1)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(icon, size: 23, color: const Color(0xFFE77910)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: const TextStyle(
                    fontSize: 11,
                    color: Color(0xFF777B82),
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: Color(0xFF9AA0A6)),
        ],
      ),
    ),
  );
}

extension<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
