import 'package:flutter/material.dart';

typedef MomentImageUrlResolver = String Function(String url);

class SoftImageLoadingPlaceholder extends StatefulWidget {
  const SoftImageLoadingPlaceholder({
    super.key,
    this.width,
    this.height,
    this.borderRadius = 0,
  });

  final double? width;
  final double? height;
  final double borderRadius;

  @override
  State<SoftImageLoadingPlaceholder> createState() =>
      _SoftImageLoadingPlaceholderState();
}

class _SoftImageLoadingPlaceholderState
    extends State<SoftImageLoadingPlaceholder>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Color?> _color;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..repeat(reverse: true);
    _color = ColorTween(
      begin: const Color(0xFFE9EAEC),
      end: const Color(0xFFF4F5F6),
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _color,
    builder: (_, _) => Container(
      width: widget.width,
      height: widget.height,
      decoration: BoxDecoration(
        color: _color.value,
        borderRadius: BorderRadius.circular(widget.borderRadius),
      ),
    ),
  );
}

class MomentImagePreview extends StatelessWidget {
  const MomentImagePreview({
    super.key,
    required this.images,
    required this.index,
    required this.resolveUrl,
    required this.child,
    this.headers,
  });

  final List<String> images;
  final int index;
  final MomentImageUrlResolver resolveUrl;
  final Map<String, String>? headers;
  final Widget child;

  @override
  Widget build(BuildContext context) => GestureDetector(
    behavior: HitTestBehavior.opaque,
    onTap: () => openMomentImageViewer(
      context,
      images: images,
      initialIndex: index,
      resolveUrl: resolveUrl,
      headers: headers,
    ),
    child: child,
  );
}

Future<void> openMomentImageViewer(
  BuildContext context, {
  required List<String> images,
  required int initialIndex,
  required MomentImageUrlResolver resolveUrl,
  Map<String, String>? headers,
}) async {
  final availableImages = images
      .where((image) => image.trim().isNotEmpty)
      .toList();
  if (availableImages.isEmpty) return;
  final selectedIndex = initialIndex.clamp(0, availableImages.length - 1);

  await showGeneralDialog<void>(
    context: context,
    barrierDismissible: true,
    barrierLabel: '关闭图片预览',
    barrierColor: const Color(0xCC000000),
    transitionDuration: const Duration(milliseconds: 160),
    pageBuilder: (dialogContext, _, _) => Material(
      key: const ValueKey('moment-image-viewer'),
      type: MaterialType.transparency,
      child: Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              key: const ValueKey('moment-image-viewer-backdrop'),
              behavior: HitTestBehavior.opaque,
              onTap: () => Navigator.of(dialogContext).pop(),
            ),
          ),
          SafeArea(
            child: Center(
              child: _ZoomableNetworkImage(
                url: resolveUrl(availableImages[selectedIndex]),
                headers: headers,
              ),
            ),
          ),
        ],
      ),
    ),
    transitionBuilder: (_, animation, _, child) => FadeTransition(
      opacity: CurvedAnimation(parent: animation, curve: Curves.easeOut),
      child: child,
    ),
  );
}

class _ZoomableNetworkImage extends StatefulWidget {
  const _ZoomableNetworkImage({required this.url, this.headers});

  final String url;
  final Map<String, String>? headers;

  @override
  State<_ZoomableNetworkImage> createState() => _ZoomableNetworkImageState();
}

class _ZoomableNetworkImageState extends State<_ZoomableNetworkImage> {
  double _scale = 1;
  double _scaleAtStart = 1;
  Offset _offset = Offset.zero;
  Offset _offsetAtStart = Offset.zero;
  Offset _focalPointAtStart = Offset.zero;

  void _handleScaleStart(ScaleStartDetails details) {
    _scaleAtStart = _scale;
    _offsetAtStart = _offset;
    _focalPointAtStart = details.localFocalPoint;
  }

  void _handleScaleUpdate(ScaleUpdateDetails details) {
    final nextScale = (_scaleAtStart * details.scale).clamp(1.0, 5.0);
    setState(() {
      _scale = nextScale;
      _offset = nextScale == 1
          ? Offset.zero
          : _offsetAtStart + (details.localFocalPoint - _focalPointAtStart);
    });
  }

  void _handleDoubleTap() {
    setState(() {
      if (_scale > 1.01) {
        _scale = 1;
        _offset = Offset.zero;
      } else {
        _scale = 2.5;
      }
    });
  }

  Matrix4 get _imageTransform => Matrix4.identity()
    ..setEntry(0, 0, _scale)
    ..setEntry(1, 1, _scale)
    ..setEntry(0, 3, _offset.dx)
    ..setEntry(1, 3, _offset.dy);

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    return GestureDetector(
      key: const ValueKey('moment-image-zoom'),
      behavior: HitTestBehavior.opaque,
      onTap: () {},
      onDoubleTap: _handleDoubleTap,
      onScaleStart: _handleScaleStart,
      onScaleUpdate: _handleScaleUpdate,
      child: Transform(
        alignment: Alignment.center,
        transform: _imageTransform,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: size.width,
            maxHeight: size.height * .9,
          ),
          child: Image.network(
            widget.url,
            headers: widget.headers,
            fit: BoxFit.contain,
            loadingBuilder: (_, child, progress) => progress == null
                ? child
                : const SizedBox(
                    width: 280,
                    height: 220,
                    child: SoftImageLoadingPlaceholder(),
                  ),
            errorBuilder: (_, _, _) => const SizedBox(
              width: 160,
              height: 120,
              child: Center(
                child: Icon(
                  Icons.broken_image_outlined,
                  size: 44,
                  color: Colors.white54,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
