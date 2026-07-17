import 'package:flutter/material.dart';

import 'moment_feed_page.dart';

/// “聊天 / 动态”顶部切换区域。
///
/// 动态列表上滑 56px 的过程中，顶部标题会连续压缩并渐隐；
/// 向下滑回顶部时会按滚动距离连续展开，不会突然跳变。
class ChatMomentSwitcher extends StatefulWidget {
  const ChatMomentSwitcher({
    super.key,
    required this.chatPage,
    this.initialIndex = 0,
    this.onTabChanged,
    this.onRelatedUnreadChanged,
    this.momentPageBuilder,
  });

  final Widget chatPage;
  final int initialIndex;
  final ValueChanged<int>? onTabChanged;
  final ValueChanged<int>? onRelatedUnreadChanged;

  /// 项目需要给动态页继续传其他参数时，可以通过这个 builder 自定义。
  /// 参数为页头压缩进度回调。
  final Widget Function(ValueChanged<double> onCollapse)? momentPageBuilder;

  @override
  State<ChatMomentSwitcher> createState() => _ChatMomentSwitcherState();
}

class _ChatMomentSwitcherState extends State<ChatMomentSwitcher> {
  late int _index;
  double _collapseProgress = 0;

  @override
  void initState() {
    super.initState();
    _index = widget.initialIndex.clamp(0, 1).toInt();
  }

  void _changeTab(int index) {
    if (_index == index) return;
    setState(() {
      _index = index;
      if (index == 0) _collapseProgress = 0;
    });
    widget.onTabChanged?.call(index);
  }

  void _updateCollapse(double value) {
    final progress = value.clamp(0.0, 1.0).toDouble();
    if ((_collapseProgress - progress).abs() < 0.002 || !mounted) return;
    setState(() {
      _collapseProgress = progress;
    });
  }

  @override
  Widget build(BuildContext context) {
    final momentPage =
        widget.momentPageBuilder?.call(_updateCollapse) ??
        MomentFeedPage(
          embedded: true,
          active: _index == 1,
          onHeaderCollapseChanged: _updateCollapse,
          onRelatedUnreadChanged: widget.onRelatedUnreadChanged,
        );

    return ColoredBox(
      color: const Color(0xFFF6F7F8),
      child: Column(
        children: [
          MomentCollapsibleHeader(
            progress: _index == 1 ? _collapseProgress : 0,
            expandedHeight: 60,
            child: _ChatMomentHeader(index: _index, onChanged: _changeTab),
          ),
          Expanded(
            child: IndexedStack(
              index: _index,
              children: [widget.chatPage, momentPage],
            ),
          ),
        ],
      ),
    );
  }
}

class _ChatMomentHeader extends StatelessWidget {
  const _ChatMomentHeader({required this.index, required this.onChanged});

  final int index;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    child: Container(
      height: 60,
      padding: const EdgeInsets.fromLTRB(18, 8, 18, 7),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: Color(0xFFEAECF0))),
      ),
      child: Row(
        children: [
          _HeaderTab(
            label: '聊天',
            selected: index == 0,
            onTap: () => onChanged(0),
          ),
          const SizedBox(width: 24),
          _HeaderTab(
            label: '动态',
            selected: index == 1,
            onTap: () => onChanged(1),
          ),
        ],
      ),
    ),
  );
}

class _HeaderTab extends StatelessWidget {
  const _HeaderTab({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 5),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedDefaultTextStyle(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            style: TextStyle(
              fontSize: 20,
              height: 1.15,
              fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
              color: selected
                  ? const Color(0xFF202124)
                  : const Color(0xFF8A8F98),
            ),
            child: Text(label),
          ),
          const SizedBox(height: 5),
          AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            width: selected ? 22 : 0,
            height: 3,
            decoration: BoxDecoration(
              color: const Color(0xFFF28C28),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ],
      ),
    ),
  );
}
