import 'package:flutter/material.dart';

// 统一的淡灰色背景
const pageBgColor = Color(0xFFF5F5F5);

Future<bool> confirmDestructiveAction(
  BuildContext context, {
  required String title,
  required String message,
  String confirmLabel = '删除',
}) async =>
    await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            child: Text(confirmLabel),
          ),
        ],
      ),
    ) ??
    false;

/// 骨架屏及图片加载的交替渐变动画组件
class ShimmerBox extends StatefulWidget {
  const ShimmerBox({super.key, this.width, this.height, this.borderRadius = 8});
  final double? width;
  final double? height;
  final double borderRadius;

  @override
  State<ShimmerBox> createState() => _ShimmerBoxState();
}

class _ShimmerBoxState extends State<ShimmerBox>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<Color?> _colorAnim;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..repeat(reverse: true);
    // 淡灰色和淡淡灰色交替过渡
    _colorAnim = ColorTween(
      begin: const Color(0xFFEEEEEE),
      end: const Color(0xFFF5F5F5),
    ).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _colorAnim,
      builder: (context, child) => Container(
        width: widget.width,
        height: widget.height,
        decoration: BoxDecoration(
          color: _colorAnim.value,
          borderRadius: BorderRadius.circular(widget.borderRadius),
        ),
      ),
    );
  }
}

/// 统一的图片加载组件 (带交替过渡动画)
class ShimmerImage extends StatelessWidget {
  const ShimmerImage({
    super.key,
    required this.url,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.borderRadius = 0,
  });
  final String url;
  final double? width;
  final double? height;
  final BoxFit fit;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: Image.network(
        url,
        width: width,
        height: height,
        fit: fit,
        loadingBuilder: (context, child, progress) {
          if (progress == null) return child;
          return ShimmerBox(width: width, height: height, borderRadius: 0);
        },
        errorBuilder: (_, __, ___) => Container(
          width: width,
          height: height,
          color: const Color(0xFFF5F5F5),
          child: const Icon(Icons.broken_image_outlined, color: Colors.grey),
        ),
      ),
    );
  }
}

/// A lightweight placeholder matching the shape of a moment card.
class MomentCardSkeleton extends StatelessWidget {
  const MomentCardSkeleton({super.key, this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(8),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            ShimmerBox(width: 40, height: 40, borderRadius: 20),
            SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShimmerBox(width: 96, height: 14, borderRadius: 4),
                SizedBox(height: 7),
                ShimmerBox(width: 58, height: 10, borderRadius: 4),
              ],
            ),
          ],
        ),
        const SizedBox(height: 16),
        const ShimmerBox(width: 180, height: 18, borderRadius: 4),
        const SizedBox(height: 10),
        const ShimmerBox(height: 13, borderRadius: 4),
        const SizedBox(height: 7),
        const FractionallySizedBox(
          widthFactor: .72,
          child: ShimmerBox(height: 13, borderRadius: 4),
        ),
        if (!compact) ...[
          const SizedBox(height: 14),
          const ShimmerBox(height: 104, borderRadius: 6),
        ],
        const SizedBox(height: 14),
        const Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            ShimmerBox(width: 42, height: 14, borderRadius: 4),
            ShimmerBox(width: 42, height: 14, borderRadius: 4),
            ShimmerBox(width: 42, height: 14, borderRadius: 4),
          ],
        ),
      ],
    ),
  );
}

class MomentFeedSkeleton extends StatelessWidget {
  const MomentFeedSkeleton({super.key, this.itemCount = 3});

  final int itemCount;

  @override
  Widget build(BuildContext context) => ListView.separated(
    physics: const NeverScrollableScrollPhysics(),
    padding: const EdgeInsets.all(12),
    itemCount: itemCount,
    separatorBuilder: (_, _) => const SizedBox(height: 10),
    itemBuilder: (_, index) => MomentCardSkeleton(compact: index > 0),
  );
}

class ChatListSkeleton extends StatelessWidget {
  const ChatListSkeleton({super.key, this.itemCount = 7});

  final int itemCount;

  @override
  Widget build(BuildContext context) => ListView.separated(
    physics: const NeverScrollableScrollPhysics(),
    padding: const EdgeInsets.all(12),
    itemCount: itemCount,
    separatorBuilder: (_, _) => const SizedBox(height: 8),
    itemBuilder: (_, _) => const DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.all(Radius.circular(8)),
      ),
      child: Padding(
        padding: EdgeInsets.all(14),
        child: Row(
          children: [
            ShimmerBox(width: 48, height: 48, borderRadius: 24),
            SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ShimmerBox(width: 104, height: 14, borderRadius: 4),
                  SizedBox(height: 10),
                  ShimmerBox(height: 12, borderRadius: 4),
                ],
              ),
            ),
            SizedBox(width: 20),
            ShimmerBox(width: 30, height: 10, borderRadius: 4),
          ],
        ),
      ),
    ),
  );
}

class CommentSkeleton extends StatelessWidget {
  const CommentSkeleton({super.key});

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: 10),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ShimmerBox(width: 34, height: 34, borderRadius: 17),
        SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ShimmerBox(width: 88, height: 13, borderRadius: 4),
              SizedBox(height: 8),
              ShimmerBox(height: 12, borderRadius: 4),
              SizedBox(height: 6),
              FractionallySizedBox(
                widthFactor: .58,
                child: ShimmerBox(height: 12, borderRadius: 4),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}
