import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../moment_models.dart';
import '../pages/moment_detail_page.dart';
import '../pages/moment_feed_page.dart';
import '../pages/moment_privacy_page.dart';
import '../pages/shop_order_page.dart';
import '../services/api_client.dart';
import '../services/moment_api.dart';
import '../utils/moment_time_formatter.dart';
import 'app_notice.dart';
import 'moment_image_viewer.dart';
import 'moment_reaction_picker.dart';
import 'ui_utils.dart';

/// 动态列表卡片。
///
/// 详情页不再复用该卡片，而是使用独立的 MomentDetailPost，避免列表页的
/// “热门评论、内容截断、整卡点击”等交互被带入详情页。
class MomentCard extends StatefulWidget {
  const MomentCard({
    super.key,
    required this.moment,
    this.detail = false,
    this.onChanged,
    this.onDeleted,
    this.highlightCommentId,
  });

  final Moment moment;

  /// 仅保留用于兼容旧调用。新的详情页不应再设置为 true。
  final bool detail;
  final ValueChanged<Moment>? onChanged;
  final VoidCallback? onDeleted;
  final int? highlightCommentId;

  @override
  State<MomentCard> createState() => _MomentCardState();
}

class _MomentCardState extends State<MomentCard> {
  late Moment _moment;
  bool _working = false;
  Timer? _timeTimer;
  MomentComment? _hotComment;

  CustomerApi get _api => CustomerApi(AppScope.of(context).api);

  @override
  void initState() {
    super.initState();
    _moment = widget.moment;
    _hotComment = widget.moment.hotComment;
    _timeTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (mounted) setState(() {});
    });
    if (!widget.detail) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadHotComment());
    }
  }

  @override
  void didUpdateWidget(covariant MomentCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.moment != widget.moment) {
      _moment = widget.moment;
      _hotComment = widget.moment.hotComment;
      if (!widget.detail) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _loadHotComment());
      }
    }
  }

  @override
  void dispose() {
    _timeTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadHotComment() async {
    if (widget.detail || _hotComment != null || _moment.commentCount <= 0) {
      return;
    }
    try {
      final result = await _api.momentComments(_moment.id, pageSize: 10);
      if (!mounted || result.items.isEmpty) return;
      setState(() => _hotComment = selectHotMomentComment(result.items));
    } catch (_) {
      // 热门评论属于附加信息，加载失败不影响动态主体。
    }
  }

  void _replaceMoment(Moment value) {
    if (!mounted) return;
    setState(() {
      _moment = value;
    });
    widget.onChanged?.call(value);
  }

  Future<void> _like() async {
    if (_working) return;
    setState(() => _working = true);
    try {
      final value = await _api.setMomentLiked(_moment.id, !_moment.liked);
      _replaceMoment(value);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<void> _react(String emoji) async {
    final old = _moment.reactions
        .where((item) => item.emoji == emoji)
        .firstOrNull;
    try {
      final value = await _api.setMomentReaction(
        _moment.id,
        emoji,
        !(old?.selected ?? false),
      );
      _replaceMoment(value);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _editMoment() async {
    final updated = await Navigator.of(context).push<Moment>(
      MaterialPageRoute(builder: (_) => MomentComposerPage(moment: _moment)),
    );
    if (updated != null) _replaceMoment(updated);
  }

  Future<void> _deleteMoment() async {
    final confirmed = await confirmDestructiveAction(
      context,
      title: '删除动态？',
      message: '动态及其评论都会被删除，删除后无法恢复。',
    );
    if (!confirmed || !mounted) return;

    try {
      await _api.deleteMoment(_moment.id);
      if (!mounted) return;
      AppNotice.show(context, '动态已删除');
      widget.onDeleted?.call();
      if (widget.detail && Navigator.of(context).canPop()) {
        Navigator.of(context).pop();
      }
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _share({int? commentId}) async {
    try {
      final conversations = await _api.conversations();
      if (!mounted) return;

      await showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        backgroundColor: Colors.white,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
        ),
        builder: (sheetContext) => SafeArea(
          child: SizedBox(
            height: 420,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(20, 2, 20, 14),
                  child: Text(
                    '分享到聊天',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF202124),
                    ),
                  ),
                ),
                const Divider(height: 1, color: Color(0xFFEEEEEE)),
                Expanded(
                  child: conversations.isEmpty
                      ? const Center(
                          child: Text(
                            '暂无可分享的聊天',
                            style: TextStyle(color: Color(0xFF8A8F98)),
                          ),
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          itemCount: conversations.length,
                          separatorBuilder: (_, _) => const Divider(
                            height: 1,
                            indent: 72,
                            color: Color(0xFFF1F2F4),
                          ),
                          itemBuilder: (_, index) {
                            final item = conversations[index];
                            return ListTile(
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 18,
                                vertical: 2,
                              ),
                              leading: const CircleAvatar(
                                backgroundColor: Color(0xFFFFF0E2),
                                child: Icon(
                                  Icons.chat_bubble_outline_rounded,
                                  color: Color(0xFFE77910),
                                ),
                              ),
                              title: Text(
                                item.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              enabled: item.isActive,
                              trailing: const Icon(
                                Icons.chevron_right_rounded,
                                size: 20,
                              ),
                              onTap: () async {
                                try {
                                  await _api.shareMoment(
                                    conversationId: item.id,
                                    momentId: _moment.id,
                                    commentId: commentId,
                                  );
                                  if (!sheetContext.mounted) return;
                                  Navigator.pop(sheetContext);
                                  if (mounted) {
                                    AppNotice.show(context, '已分享到聊天');
                                  }
                                } on ApiException catch (error) {
                                  if (mounted) {
                                    AppNotice.show(context, error.message);
                                  }
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
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  void _open() {
    if (widget.detail) return;
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => MomentDetailPage(momentId: _moment.id)),
    );
  }

  void _openAuthor() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => UserMomentsPage(
          userId: _moment.authorId,
          nickname: _moment.authorNickname,
        ),
      ),
    );
  }

  void _openTopic(MomentTopic topic) {
    Navigator.of(
      context,
    ).push(MaterialPageRoute(builder: (_) => MomentTopicPage(topic: topic)));
  }

  @override
  Widget build(BuildContext context) {
    final displayTime = _shouldShowEdited(_moment)
        ? '编辑于 ${formatMomentTime(_moment.editedAt!)}'
        : formatMomentTime(_moment.publishedAt ?? _moment.createdAt);

    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: _open,
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 15, 16, 8),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(16)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _MomentAuthorHeader(
                moment: _moment,
                displayTime: displayTime,
                onAuthorTap: _openAuthor,
                onEdit: _moment.mine ? _editMoment : null,
                onDelete: _moment.mine ? _deleteMoment : null,
              ),
              const SizedBox(height: 13),
              Text(
                _moment.title,
                style: const TextStyle(
                  fontSize: 18,
                  height: 1.3,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF202124),
                ),
              ),
              const SizedBox(height: 7),
              Text(
                _moment.content,
                maxLines: widget.detail ? null : 5,
                overflow: widget.detail ? null : TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 15,
                  height: 1.58,
                  color: Color(0xFF3C4043),
                ),
              ),
              if (!widget.detail && _moment.content.length > 150)
                const Padding(
                  padding: EdgeInsets.only(top: 4),
                  child: Text(
                    '展开全文',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFFE77910),
                    ),
                  ),
                ),
              if (_moment.images.isNotEmpty) ...[
                const SizedBox(height: 12),
                _MomentImages(images: _moment.images),
              ],
              if (_moment.shopId != null) ...[
                const SizedBox(height: 12),
                _MomentShopLink(moment: _moment),
              ],
              if (_moment.topics.isNotEmpty) ...[
                const SizedBox(height: 11),
                Wrap(
                  spacing: 7,
                  runSpacing: 7,
                  children: [
                    for (final topic in _moment.topics)
                      _TopicChip(topic: topic, onTap: () => _openTopic(topic)),
                  ],
                ),
              ],
              if (_moment.visibility == 'PRIVATE') ...[
                const SizedBox(height: 10),
                const _Badge(
                  text: '仅自己可见',
                  icon: Icons.lock_outline_rounded,
                  color: Color(0xFF697077),
                ),
              ],
              if (_moment.reactions.isNotEmpty) ...[
                const SizedBox(height: 12),
                _ReactionBar(reactions: _moment.reactions, onReact: _react),
              ],
              const SizedBox(height: 10),
              const Divider(height: 1, color: Color(0xFFEEEEEE)),
              SizedBox(
                height: 48,
                child: Row(
                  children: [
                    Expanded(
                      child: _Action(
                        icon: _moment.liked
                            ? Icons.favorite_rounded
                            : Icons.favorite_border_rounded,
                        text: _moment.likeCount == 0
                            ? '点赞'
                            : '${_moment.likeCount}',
                        active: _moment.liked,
                        onTap: _like,
                      ),
                    ),
                    const _ActionDivider(),
                    Expanded(
                      child: _Action(
                        icon: Icons.chat_bubble_outline_rounded,
                        text: _moment.commentCount == 0
                            ? '评论'
                            : '${_moment.commentCount}',
                        onTap: _open,
                      ),
                    ),
                    const _ActionDivider(),
                    Expanded(
                      child: _ReactionPickerAction(
                        reactions: _moment.reactions,
                        onSelected: _react,
                      ),
                    ),
                    const _ActionDivider(),
                    Expanded(
                      child: _Action(
                        icon: Icons.ios_share_rounded,
                        text: '分享',
                        onTap: _share,
                      ),
                    ),
                  ],
                ),
              ),
              if (!widget.detail && _hotComment != null) ...[
                const SizedBox(height: 10),
                _HotCommentPreview(comment: _hotComment!, onTap: _open),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _MomentAuthorHeader extends StatelessWidget {
  const _MomentAuthorHeader({
    required this.moment,
    required this.displayTime,
    required this.onAuthorTap,
    this.onEdit,
    this.onDelete,
  });

  final Moment moment;
  final String displayTime;
  final VoidCallback onAuthorTap;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      InkWell(
        onTap: onAuthorTap,
        customBorder: const CircleBorder(),
        child: CircleAvatar(
          radius: 21,
          backgroundColor: const Color(0xFFFFE8D2),
          backgroundImage: moment.authorAvatarUrl?.isNotEmpty == true
              ? NetworkImage(resolveCustomerImageUrl(moment.authorAvatarUrl!))
              : null,
          child: moment.authorAvatarUrl?.isNotEmpty == true
              ? null
              : const Icon(
                  Icons.person_outline_rounded,
                  size: 22,
                  color: Color(0xFF9C5A1B),
                ),
        ),
      ),
      const SizedBox(width: 11),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 6,
              runSpacing: 3,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                InkWell(
                  onTap: onAuthorTap,
                  child: Text(
                    moment.authorNickname,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF202124),
                    ),
                  ),
                ),
                if (moment.merchant)
                  const _Badge(text: '商家', color: Color(0xFFB75E0A)),
              ],
            ),
            const SizedBox(height: 2),
            Text(
              displayTime,
              style: const TextStyle(fontSize: 11.5, color: Color(0xFF92979F)),
            ),
          ],
        ),
      ),
      if (onEdit != null || onDelete != null)
        PopupMenuButton<String>(
          tooltip: '更多操作',
          padding: EdgeInsets.zero,
          icon: const Icon(Icons.more_horiz_rounded, color: Color(0xFF777B82)),
          onSelected: (value) {
            if (value == 'edit') onEdit?.call();
            if (value == 'delete') onDelete?.call();
          },
          itemBuilder: (_) => [
            if (onEdit != null)
              const PopupMenuItem(value: 'edit', child: Text('编辑动态')),
            if (onDelete != null)
              const PopupMenuItem(value: 'delete', child: Text('删除动态')),
          ],
        ),
    ],
  );
}

class _MomentShopLink extends StatelessWidget {
  const _MomentShopLink({required this.moment});

  final Moment moment;

  @override
  Widget build(BuildContext context) {
    final imageUrl = moment.dishImageUrl ?? moment.shopImageUrl;
    return InkWell(
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ShopOrderPage(
            shopId: moment.shopId!,
            highlightedDishId: moment.dishId,
          ),
        ),
      ),
      borderRadius: BorderRadius.circular(10),
      child: Ink(
        padding: const EdgeInsets.all(9),
        decoration: BoxDecoration(
          color: const Color(0xFFFFF7EF),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFFFFE1C2)),
        ),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: imageUrl?.isNotEmpty == true
                  ? Image.network(
                      resolveCustomerImageUrl(imageUrl!),
                      width: 48,
                      height: 48,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => const _ShopPlaceholder(),
                    )
                  : const _ShopPlaceholder(),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    moment.dishName ?? moment.shopName ?? '查看店铺',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF303134),
                    ),
                  ),
                  if (moment.dishName != null && moment.shopName != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      moment.shopName!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: Color(0xFF777B82),
                      ),
                    ),
                  ],
                  if (moment.dishPrice != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      '¥${moment.dishPrice!.toStringAsFixed(2)} · 月售${moment.dishSalesCount ?? 0}',
                      style: const TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFFE77910),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const Icon(
              Icons.chevron_right_rounded,
              size: 20,
              color: Color(0xFF9AA0A6),
            ),
          ],
        ),
      ),
    );
  }
}

class _ShopPlaceholder extends StatelessWidget {
  const _ShopPlaceholder();

  @override
  Widget build(BuildContext context) => const SizedBox.square(
    dimension: 48,
    child: ColoredBox(
      color: Colors.white,
      child: Icon(Icons.storefront_outlined, color: Color(0xFFE77910)),
    ),
  );
}

class _TopicChip extends StatelessWidget {
  const _TopicChip({required this.topic, required this.onTap});

  final MomentTopic topic;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(7),
    child: Ink(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF3E8),
        borderRadius: BorderRadius.circular(7),
      ),
      child: Text(
        '#${topic.name}',
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Color(0xFFD96C06),
        ),
      ),
    ),
  );
}

class _HotCommentPreview extends StatelessWidget {
  const _HotCommentPreview({required this.comment, required this.onTap});

  final MomentComment comment;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Ink(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(11, 7, 4, 7),
      decoration: const BoxDecoration(
        border: Border(left: BorderSide(color: Color(0xFFF2A04B), width: 2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(
                Icons.local_fire_department_rounded,
                size: 15,
                color: Color(0xFFE77910),
              ),
              SizedBox(width: 4),
              Text(
                '热门评论',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFFE77910),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          _HotCommentLine(comment: comment),
          if (comment.replies.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8, left: 29),
              child: _HotCommentLine(
                comment: comment.replies.first,
                compact: true,
              ),
            ),
        ],
      ),
    ),
  );
}

class _ReactionBar extends StatelessWidget {
  const _ReactionBar({required this.reactions, required this.onReact});

  final List<MomentReaction> reactions;
  final ValueChanged<String> onReact;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: 7,
    runSpacing: 7,
    children: [
      for (final item in reactions)
        _ReactionChip(
          emoji: item.emoji,
          count: item.count,
          selected: item.selected,
          onTap: () => onReact(item.emoji),
        ),
    ],
  );
}

class _ReactionChip extends StatelessWidget {
  const _ReactionChip({
    required this.emoji,
    required this.count,
    required this.selected,
    required this.onTap,
  });

  final String emoji;
  final int count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(9),
    child: Ink(
      height: 32,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        color: selected ? const Color(0xFFFFE8D2) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(9),
        border: selected ? Border.all(color: const Color(0xFFF3B77D)) : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(emoji, style: const TextStyle(fontSize: 16)),
          const SizedBox(width: 5),
          Text(
            '$count',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: selected
                  ? const Color(0xFFC9650A)
                  : const Color(0xFF5F6368),
            ),
          ),
        ],
      ),
    ),
  );
}

class _ReactionPickerAction extends StatelessWidget {
  const _ReactionPickerAction({
    required this.reactions,
    required this.onSelected,
  });

  final List<MomentReaction> reactions;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: () async {
      final value = await showMomentReactionPicker(
        context,
        counts: {for (final item in reactions) item.emoji: item.count},
        selected: {
          for (final item in reactions)
            if (item.selected) item.emoji,
        },
      );
      if (value != null) onSelected(value);
    },
    child: const _ActionContent(
      icon: Icons.add_reaction_outlined,
      text: '回应',
      active: false,
    ),
  );
}

class _Action extends StatelessWidget {
  const _Action({
    required this.icon,
    required this.text,
    required this.onTap,
    this.active = false,
  });

  final IconData icon;
  final String text;
  final VoidCallback onTap;
  final bool active;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: _ActionContent(icon: icon, text: text, active: active),
  );
}

class _ActionContent extends StatelessWidget {
  const _ActionContent({
    required this.icon,
    required this.text,
    required this.active,
  });

  final IconData icon;
  final String text;
  final bool active;

  @override
  Widget build(BuildContext context) => Center(
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          icon,
          size: 19,
          color: active ? const Color(0xFFE64646) : const Color(0xFF646A70),
        ),
        const SizedBox(width: 5),
        Text(
          text,
          style: TextStyle(
            fontSize: 12,
            fontWeight: active ? FontWeight.w600 : FontWeight.w500,
            color: active ? const Color(0xFFE64646) : const Color(0xFF646A70),
          ),
        ),
      ],
    ),
  );
}

class _ActionDivider extends StatelessWidget {
  const _ActionDivider();

  @override
  Widget build(BuildContext context) => const SizedBox(
    height: 18,
    child: VerticalDivider(width: 1, color: Color(0xFFEEEEEE)),
  );
}

class _MomentImages extends StatelessWidget {
  const _MomentImages({required this.images});

  final List<String> images;

  @override
  Widget build(BuildContext context) {
    final shown = images.take(9).toList();
    if (shown.length == 1) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: AspectRatio(
          aspectRatio: 16 / 10,
          child: _NetworkMomentImage(url: shown.first, images: shown, index: 0),
        ),
      );
    }

    if (shown.length == 2) {
      return Row(
        children: [
          for (var index = 0; index < shown.length; index++) ...[
            if (index > 0) const SizedBox(width: 5),
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(9),
                child: AspectRatio(
                  aspectRatio: 1,
                  child: _NetworkMomentImage(
                    url: shown[index],
                    images: shown,
                    index: index,
                  ),
                ),
              ),
            ),
          ],
        ],
      );
    }

    final columns = shown.length == 4 ? 2 : 3;
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: shown.length,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: columns,
        mainAxisSpacing: 5,
        crossAxisSpacing: 5,
      ),
      itemBuilder: (_, index) => ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: _NetworkMomentImage(
          url: shown[index],
          images: shown,
          index: index,
        ),
      ),
    );
  }
}

class _NetworkMomentImage extends StatelessWidget {
  const _NetworkMomentImage({
    required this.url,
    required this.images,
    required this.index,
  });

  final String url;
  final List<String> images;
  final int index;

  @override
  Widget build(BuildContext context) => MomentImagePreview(
    images: images,
    index: index,
    resolveUrl: resolveCustomerImageUrl,
    child: Image.network(
      resolveCustomerImageUrl(url),
      fit: BoxFit.cover,
      loadingBuilder: (_, child, progress) =>
          progress == null ? child : const SoftImageLoadingPlaceholder(),
      errorBuilder: (_, _, _) => const ColoredBox(
        color: Color(0xFFF0F1F3),
        child: Center(
          child: Icon(Icons.broken_image_outlined, color: Color(0xFF9AA0A6)),
        ),
      ),
    ),
  );
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text, required this.color, this.icon});

  final String text;
  final Color color;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    decoration: BoxDecoration(
      color: color.withValues(alpha: .11),
      borderRadius: BorderRadius.circular(5),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 10, color: color),
          const SizedBox(width: 3),
        ],
        Text(
          text,
          style: TextStyle(
            fontSize: 10,
            height: 1.2,
            fontWeight: FontWeight.w600,
            color: color,
          ),
        ),
      ],
    ),
  );
}

class _HotCommentLine extends StatelessWidget {
  const _HotCommentLine({required this.comment, this.compact = false});

  final MomentComment comment;
  final bool compact;

  void _openUser(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) =>
            UserMomentsPage(userId: comment.userId, nickname: comment.nickname),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      InkWell(
        onTap: () => _openUser(context),
        customBorder: const CircleBorder(),
        child: CircleAvatar(
          radius: compact ? 9 : 11,
          backgroundColor: const Color(0xFFE9ECEF),
          backgroundImage: comment.avatarUrl?.isNotEmpty == true
              ? NetworkImage(resolveCustomerImageUrl(comment.avatarUrl!))
              : null,
          child: comment.avatarUrl?.isNotEmpty == true
              ? null
              : Icon(
                  Icons.person_outline_rounded,
                  size: compact ? 11 : 13,
                  color: const Color(0xFF777B82),
                ),
        ),
      ),
      const SizedBox(width: 8),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Flexible(
                  child: Text(
                    comment.nickname,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: compact ? 11.5 : 12.5,
                      fontWeight: FontWeight.w700,
                      color: const Color(0xFF303134),
                    ),
                  ),
                ),
                if (comment.merchant) ...[
                  const SizedBox(width: 4),
                  const _MiniIdentityBadge(text: '商家'),
                ],
                if (comment.author) ...[
                  const SizedBox(width: 4),
                  const _MiniIdentityBadge(text: '作者'),
                ],
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    formatMomentTime(comment.createdAt),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: compact ? 9 : 10,
                      color: const Color(0xFF9AA0A6),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 3),
            Text(
              comment.content,
              maxLines: compact ? 1 : 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: compact ? 12 : 13,
                height: 1.4,
                color: const Color(0xFF44474C),
              ),
            ),
          ],
        ),
      ),
      if (!compact && comment.likeCount > 0) ...[
        const SizedBox(width: 8),
        const Icon(Icons.favorite_rounded, size: 12, color: Color(0xFFB1B5BB)),
        const SizedBox(width: 2),
        Text(
          '${comment.likeCount}',
          style: const TextStyle(fontSize: 11, color: Color(0xFF8A8F98)),
        ),
      ],
    ],
  );
}

class _MiniIdentityBadge extends StatelessWidget {
  const _MiniIdentityBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
    decoration: BoxDecoration(
      color: const Color(0xFFFFE9D2),
      borderRadius: BorderRadius.circular(3),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 9,
        height: 1.15,
        color: Color(0xFFB75E0A),
      ),
    ),
  );
}

MomentComment selectHotMomentComment(Iterable<MomentComment> comments) {
  final sorted = comments.toList()
    ..sort((left, right) {
      final byLikes = right.likeCount.compareTo(left.likeCount);
      if (byLikes != 0) return byLikes;
      final leftTime = DateTime.tryParse(left.createdAt);
      final rightTime = DateTime.tryParse(right.createdAt);
      return (rightTime ?? DateTime.fromMillisecondsSinceEpoch(0)).compareTo(
        leftTime ?? DateTime.fromMillisecondsSinceEpoch(0),
      );
    });
  return sorted.first;
}

bool _shouldShowEdited(Moment moment) {
  if (moment.editedAt?.isNotEmpty != true || moment.status != 'PUBLISHED') {
    return false;
  }
  final edited = DateTime.tryParse(moment.editedAt!);
  final published = DateTime.tryParse(moment.publishedAt ?? moment.createdAt);
  if (edited == null || published == null) return true;
  return edited.isAfter(published.add(const Duration(seconds: 1)));
}
