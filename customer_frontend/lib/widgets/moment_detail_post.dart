import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../moment_models.dart';
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

/// 动态详情页专用正文组件。
///
/// 它和列表页 MomentCard 使用不同布局：不截断正文、不整卡跳转、
/// 不请求或展示热门评论，评论由详情页下方的评论区独立负责。
class MomentDetailPost extends StatefulWidget {
  const MomentDetailPost({
    super.key,
    required this.moment,
    required this.onCommentTap,
    this.onChanged,
    this.onDeleted,
  });

  final Moment moment;
  final VoidCallback onCommentTap;
  final ValueChanged<Moment>? onChanged;
  final VoidCallback? onDeleted;

  @override
  State<MomentDetailPost> createState() => _MomentDetailPostState();
}

class _MomentDetailPostState extends State<MomentDetailPost> {
  late Moment _moment;
  bool _working = false;
  Timer? _timeTimer;

  CustomerApi get _api => CustomerApi(AppScope.of(context).api);

  @override
  void initState() {
    super.initState();
    _moment = widget.moment;
    _timeTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void didUpdateWidget(covariant MomentDetailPost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.moment != widget.moment) {
      _moment = widget.moment;
    }
  }

  @override
  void dispose() {
    _timeTimer?.cancel();
    super.dispose();
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
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _share() async {
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

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 17, 12, 0),
            child: _DetailAuthorHeader(
              moment: _moment,
              displayTime: displayTime,
              onAuthorTap: _openAuthor,
              onEdit: _moment.mine ? _editMoment : null,
              onDelete: _moment.mine ? _deleteMoment : null,
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _moment.title,
                  style: const TextStyle(
                    fontSize: 22,
                    height: 1.3,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF202124),
                  ),
                ),
                const SizedBox(height: 11),
                SelectableText(
                  _moment.content,
                  style: const TextStyle(
                    fontSize: 16,
                    height: 1.7,
                    color: Color(0xFF303134),
                  ),
                ),
                if (_moment.images.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  _DetailImages(images: _moment.images),
                ],
                if (_moment.shopId != null) ...[
                  const SizedBox(height: 15),
                  _DetailShopLink(moment: _moment),
                ],
                if (_moment.topics.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final topic in _moment.topics)
                        _DetailTopicChip(
                          topic: topic,
                          onTap: () => _openTopic(topic),
                        ),
                    ],
                  ),
                ],
                if (_moment.visibility == 'PRIVATE') ...[
                  const SizedBox(height: 12),
                  const _DetailBadge(
                    text: '仅自己可见',
                    icon: Icons.lock_outline_rounded,
                    color: Color(0xFF697077),
                  ),
                ],
                if (_moment.reactions.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final item in _moment.reactions)
                        _DetailReactionChip(
                          emoji: item.emoji,
                          count: item.count,
                          selected: item.selected,
                          onTap: () => _react(item.emoji),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 17),
          const Divider(height: 1, color: Color(0xFFEEEEEE)),
          SizedBox(
            height: 54,
            child: Row(
              children: [
                Expanded(
                  child: _DetailAction(
                    icon: _moment.liked
                        ? Icons.favorite_rounded
                        : Icons.favorite_border_rounded,
                    label: _moment.likeCount == 0
                        ? '点赞'
                        : '点赞 ${_moment.likeCount}',
                    active: _moment.liked,
                    onTap: _like,
                  ),
                ),
                const _DetailActionDivider(),
                Expanded(
                  child: _DetailAction(
                    icon: Icons.chat_bubble_outline_rounded,
                    label: _moment.commentCount == 0
                        ? '评论'
                        : '评论 ${_moment.commentCount}',
                    onTap: widget.onCommentTap,
                  ),
                ),
                const _DetailActionDivider(),
                Expanded(
                  child: _DetailReactionPicker(
                    reactions: _moment.reactions,
                    onSelected: _react,
                  ),
                ),
                const _DetailActionDivider(),
                Expanded(
                  child: _DetailAction(
                    icon: Icons.ios_share_rounded,
                    label: '分享',
                    onTap: _share,
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

class _DetailAuthorHeader extends StatelessWidget {
  const _DetailAuthorHeader({
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
    children: [
      InkWell(
        onTap: onAuthorTap,
        customBorder: const CircleBorder(),
        child: CircleAvatar(
          radius: 23,
          backgroundColor: const Color(0xFFFFE8D2),
          backgroundImage: moment.authorAvatarUrl?.isNotEmpty == true
              ? NetworkImage(resolveCustomerImageUrl(moment.authorAvatarUrl!))
              : null,
          child: moment.authorAvatarUrl?.isNotEmpty == true
              ? null
              : const Icon(
                  Icons.person_outline_rounded,
                  size: 24,
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
                    style: const TextStyle(
                      fontSize: 15.5,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF202124),
                    ),
                  ),
                ),
                if (moment.merchant)
                  const _DetailBadge(text: '商家', color: Color(0xFFB75E0A)),
              ],
            ),
            const SizedBox(height: 3),
            Text(
              displayTime,
              style: const TextStyle(fontSize: 12, color: Color(0xFF92979F)),
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

class _DetailShopLink extends StatelessWidget {
  const _DetailShopLink({required this.moment});

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
      borderRadius: BorderRadius.circular(12),
      child: Ink(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: const Color(0xFFFFF7EF),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFFFDFC0)),
        ),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(9),
              child: imageUrl?.isNotEmpty == true
                  ? Image.network(
                      resolveCustomerImageUrl(imageUrl!),
                      width: 56,
                      height: 56,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => const _DetailShopPlaceholder(),
                    )
                  : const _DetailShopPlaceholder(),
            ),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    moment.dishName ?? moment.shopName ?? '查看店铺',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF303134),
                    ),
                  ),
                  if (moment.dishName != null && moment.shopName != null) ...[
                    const SizedBox(height: 3),
                    Text(
                      moment.shopName!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF777B82),
                      ),
                    ),
                  ],
                  if (moment.dishDescription?.isNotEmpty == true) ...[
                    const SizedBox(height: 3),
                    Text(
                      moment.dishDescription!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: Color(0xFF8A8F98),
                      ),
                    ),
                  ],
                  if (moment.dishPrice != null) ...[
                    const SizedBox(height: 3),
                    Text(
                      '¥${moment.dishPrice!.toStringAsFixed(2)} · 月售${moment.dishSalesCount ?? 0}',
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFFE77910),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const Icon(Icons.chevron_right_rounded, color: Color(0xFF9AA0A6)),
          ],
        ),
      ),
    );
  }
}

class _DetailShopPlaceholder extends StatelessWidget {
  const _DetailShopPlaceholder();

  @override
  Widget build(BuildContext context) => const SizedBox.square(
    dimension: 56,
    child: ColoredBox(
      color: Colors.white,
      child: Icon(Icons.storefront_outlined, color: Color(0xFFE77910)),
    ),
  );
}

class _DetailTopicChip extends StatelessWidget {
  const _DetailTopicChip({required this.topic, required this.onTap});

  final MomentTopic topic;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: Ink(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF3E8),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '#${topic.name}',
        style: const TextStyle(
          fontSize: 12.5,
          fontWeight: FontWeight.w600,
          color: Color(0xFFD96C06),
        ),
      ),
    ),
  );
}

class _DetailReactionChip extends StatelessWidget {
  const _DetailReactionChip({
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
    borderRadius: BorderRadius.circular(10),
    child: Ink(
      height: 34,
      padding: const EdgeInsets.symmetric(horizontal: 11),
      decoration: BoxDecoration(
        color: selected ? const Color(0xFFFFE8D2) : const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(10),
        border: selected ? Border.all(color: const Color(0xFFF3B77D)) : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(emoji, style: const TextStyle(fontSize: 17)),
          const SizedBox(width: 6),
          Text(
            '$count',
            style: TextStyle(
              fontSize: 12.5,
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

class _DetailReactionPicker extends StatelessWidget {
  const _DetailReactionPicker({
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
    child: const _DetailActionContent(
      icon: Icons.add_reaction_outlined,
      label: '回应',
      active: false,
    ),
  );
}

class _DetailAction extends StatelessWidget {
  const _DetailAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.active = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool active;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: _DetailActionContent(icon: icon, label: label, active: active),
  );
}

class _DetailActionContent extends StatelessWidget {
  const _DetailActionContent({
    required this.icon,
    required this.label,
    required this.active,
  });

  final IconData icon;
  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) => Center(
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          icon,
          size: 20,
          color: active ? const Color(0xFFE64646) : const Color(0xFF646A70),
        ),
        const SizedBox(width: 5),
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 12,
              fontWeight: active ? FontWeight.w600 : FontWeight.w500,
              color: active ? const Color(0xFFE64646) : const Color(0xFF646A70),
            ),
          ),
        ),
      ],
    ),
  );
}

class _DetailActionDivider extends StatelessWidget {
  const _DetailActionDivider();

  @override
  Widget build(BuildContext context) => const SizedBox(
    height: 18,
    child: VerticalDivider(width: 1, color: Color(0xFFEEEEEE)),
  );
}

class _DetailImages extends StatelessWidget {
  const _DetailImages({required this.images});

  final List<String> images;

  @override
  Widget build(BuildContext context) {
    final shown = images.take(9).toList();
    if (shown.length == 1) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: AspectRatio(
          aspectRatio: 16 / 10,
          child: _DetailNetworkImage(url: shown.first, images: shown, index: 0),
        ),
      );
    }

    if (shown.length == 2) {
      return Row(
        children: [
          for (var index = 0; index < shown.length; index++) ...[
            if (index > 0) const SizedBox(width: 6),
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: AspectRatio(
                  aspectRatio: 1,
                  child: _DetailNetworkImage(
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
        mainAxisSpacing: 6,
        crossAxisSpacing: 6,
      ),
      itemBuilder: (_, index) => ClipRRect(
        borderRadius: BorderRadius.circular(9),
        child: _DetailNetworkImage(
          url: shown[index],
          images: shown,
          index: index,
        ),
      ),
    );
  }
}

class _DetailNetworkImage extends StatelessWidget {
  const _DetailNetworkImage({
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

class _DetailBadge extends StatelessWidget {
  const _DetailBadge({required this.text, required this.color, this.icon});

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

bool _shouldShowEdited(Moment moment) {
  if (moment.editedAt?.isNotEmpty != true || moment.status != 'PUBLISHED') {
    return false;
  }
  final edited = DateTime.tryParse(moment.editedAt!);
  final published = DateTime.tryParse(moment.publishedAt ?? moment.createdAt);
  if (edited == null || published == null) return true;
  return edited.isAfter(published.add(const Duration(seconds: 1)));
}
