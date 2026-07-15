import 'package:flutter/material.dart';

class ChatAction {
  const ChatAction(this.value, this.label, this.icon);
  final String value;
  final String label;
  final IconData icon;
}

class ChatActionOverlay extends StatefulWidget {
  const ChatActionOverlay({
    super.key,
    required this.actions,
    required this.onSelected,
    required this.child,
  });

  final List<ChatAction> actions;
  final ValueChanged<String> onSelected;
  final Widget child;

  @override
  State<ChatActionOverlay> createState() => _ChatActionOverlayState();
}

class _ChatActionOverlayState extends State<ChatActionOverlay>
    with SingleTickerProviderStateMixin {
  final LayerLink _link = LayerLink();
  late final AnimationController _controller;
  late final Animation<double> _scale;
  OverlayEntry? _entry;
  bool _pressed = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 260),
    );
    _scale = TweenSequence<double>([
      TweenSequenceItem(tween: Tween(begin: .92, end: 1.045), weight: 45),
      TweenSequenceItem(tween: Tween(begin: 1.045, end: 1), weight: 55),
    ]).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));
  }

  void _show() {
    if (_entry != null || widget.actions.isEmpty) return;
    setState(() => _pressed = false);
    _controller.forward(from: 0);
    _entry = OverlayEntry(
      builder: (context) => Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: _remove,
              child: const ColoredBox(color: Colors.transparent),
            ),
          ),
          CompositedTransformFollower(
            link: _link,
            targetAnchor: Alignment.topCenter,
            followerAnchor: Alignment.bottomCenter,
            offset: const Offset(0, -8),
            showWhenUnlinked: false,
            child: Material(
              color: Colors.transparent,
              child: Align(
                alignment: Alignment.topCenter,
                widthFactor: 1,
                heightFactor: 1,
                child: TweenAnimationBuilder<double>(
                  tween: Tween(begin: .82, end: 1),
                  duration: const Duration(milliseconds: 180),
                  curve: Curves.easeOutBack,
                  builder: (context, value, child) => Opacity(
                    opacity: value.clamp(0.0, 1.0).toDouble(),
                    child: Transform.scale(scale: value, child: child),
                  ),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFF303236),
                      borderRadius: BorderRadius.circular(8),
                      boxShadow: const [
                        BoxShadow(
                          color: Color(0x33000000),
                          blurRadius: 12,
                          offset: Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (final action in widget.actions)
                          InkWell(
                            borderRadius: BorderRadius.circular(6),
                            onTap: () {
                              _remove();
                              widget.onSelected(action.value);
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 7,
                              ),
                              child: Row(
                                children: [
                                  Icon(
                                    action.icon,
                                    size: 16,
                                    color: Colors.white,
                                  ),
                                  const SizedBox(width: 4),
                                  Text(
                                    action.label,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
    Overlay.of(context).insert(_entry!);
  }

  void _remove() {
    _entry?.remove();
    _entry = null;
  }

  @override
  void dispose() {
    _remove();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => CompositedTransformTarget(
    link: _link,
    child: GestureDetector(
      onLongPressStart: (_) => setState(() => _pressed = true),
      onLongPressCancel: () => setState(() => _pressed = false),
      onLongPress: _show,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) => AnimatedScale(
          scale: _pressed ? .92 : (_controller.isAnimating ? _scale.value : 1),
          duration: const Duration(milliseconds: 120),
          curve: Curves.easeOut,
          child: child,
        ),
        child: widget.child,
      ),
    ),
  );
}
