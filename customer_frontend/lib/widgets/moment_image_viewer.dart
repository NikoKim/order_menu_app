import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';

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
  if (images.isEmpty) return;
  final requestedImage = images[initialIndex.clamp(0, images.length - 1)];
  final availableImages = images
      .where((image) => image.trim().isNotEmpty)
      .toList();
  if (availableImages.isEmpty) return;
  final requestedIndex = availableImages.indexOf(requestedImage);
  final selectedIndex = requestedIndex < 0 ? 0 : requestedIndex;

  await showGeneralDialog<void>(
    context: context,
    barrierDismissible: true,
    barrierLabel: '关闭图片预览',
    barrierColor: const Color(0xCC000000),
    transitionDuration: const Duration(milliseconds: 160),
    pageBuilder: (dialogContext, _, _) => _MomentImageGallery(
      images: availableImages,
      initialIndex: selectedIndex,
      resolveUrl: resolveUrl,
      headers: headers,
    ),
    transitionBuilder: (_, animation, _, child) => FadeTransition(
      opacity: CurvedAnimation(parent: animation, curve: Curves.easeOut),
      child: child,
    ),
  );
}

class _MomentImageGallery extends StatefulWidget {
  const _MomentImageGallery({
    required this.images,
    required this.initialIndex,
    required this.resolveUrl,
    this.headers,
  });

  final List<String> images;
  final int initialIndex;
  final MomentImageUrlResolver resolveUrl;
  final Map<String, String>? headers;

  @override
  State<_MomentImageGallery> createState() => _MomentImageGalleryState();
}

class _MomentImageGalleryState extends State<_MomentImageGallery> {
  late final PageController _pageController;
  late int _currentIndex;
  Timer? _singleTapTimer;
  Offset? _pointerDown;
  int _pointerCount = 0;
  bool _pointerMoved = false;
  bool _completingDoubleTap = false;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
    _pageController = PageController(initialPage: widget.initialIndex);
  }

  @override
  void dispose() {
    _singleTapTimer?.cancel();
    _pageController.dispose();
    super.dispose();
  }

  void _onPointerDown(PointerDownEvent event) {
    _pointerCount += 1;
    if (_pointerCount > 1) {
      _pointerMoved = true;
      _singleTapTimer?.cancel();
      _singleTapTimer = null;
      return;
    }
    _pointerDown = event.position;
    _pointerMoved = false;
    _completingDoubleTap = _singleTapTimer != null;
    _singleTapTimer?.cancel();
    _singleTapTimer = null;
  }

  void _onPointerMove(PointerMoveEvent event) {
    final origin = _pointerDown;
    if (origin != null && (event.position - origin).distance > kTouchSlop) {
      _pointerMoved = true;
    }
  }

  void _onPointerUp(PointerUpEvent event) {
    if (_pointerCount > 0) _pointerCount -= 1;
    if (_pointerCount > 0) return;
    final wasTap = !_pointerMoved && _pointerDown != null;
    _pointerDown = null;
    _pointerMoved = false;
    if (!wasTap || _completingDoubleTap) {
      _completingDoubleTap = false;
      return;
    }
    _singleTapTimer = Timer(kDoubleTapTimeout, () {
      _singleTapTimer = null;
      if (mounted && ModalRoute.of(context)?.isCurrent == true) {
        Navigator.of(context).pop();
      }
    });
  }

  void _onPointerCancel(PointerCancelEvent event) {
    _pointerCount = 0;
    _pointerDown = null;
    _pointerMoved = false;
    _completingDoubleTap = false;
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      key: const ValueKey('moment-image-viewer'),
      type: MaterialType.transparency,
      child: Stack(
        children: [
          Positioned.fill(
            child: Listener(
              behavior: HitTestBehavior.opaque,
              onPointerDown: _onPointerDown,
              onPointerMove: _onPointerMove,
              onPointerUp: _onPointerUp,
              onPointerCancel: _onPointerCancel,
              child: PhotoViewGallery.builder(
                key: const ValueKey('moment-image-gallery'),
                pageController: _pageController,
                itemCount: widget.images.length,
                scrollPhysics: const BouncingScrollPhysics(),
                backgroundDecoration: const BoxDecoration(
                  color: Colors.transparent,
                ),
                onPageChanged: (index) => setState(() => _currentIndex = index),
                loadingBuilder: (_, _) => const Center(
                  child: SizedBox(
                    width: 280,
                    height: 220,
                    child: SoftImageLoadingPlaceholder(),
                  ),
                ),
                builder: (_, index) => PhotoViewGalleryPageOptions(
                  key: ValueKey('moment-image-zoom-$index'),
                  imageProvider: NetworkImage(
                    widget.resolveUrl(widget.images[index]),
                    headers: widget.headers,
                  ),
                  initialScale: PhotoViewComputedScale.contained,
                  minScale: PhotoViewComputedScale.contained,
                  maxScale: PhotoViewComputedScale.covered * 4,
                  gestureDetectorBehavior: HitTestBehavior.opaque,
                  errorBuilder: (_, _, _) => const Center(
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
          if (widget.images.length > 1)
            Positioned(
              left: 0,
              right: 0,
              bottom: MediaQuery.paddingOf(context).bottom + 22,
              child: Center(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: const Color(0x99000000),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 9,
                      vertical: 5,
                    ),
                    child: Text(
                      '${_currentIndex + 1}/${widget.images.length}',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
