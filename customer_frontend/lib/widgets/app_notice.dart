import 'dart:async';

import 'package:flutter/material.dart';

class AppNotice {
  static OverlayEntry? _entry;
  static Timer? _timer;

  static void show(BuildContext context, String message) {
    _timer?.cancel();
    if (_entry?.mounted == true) _entry?.remove();
    final overlay = Overlay.of(context, rootOverlay: true);
    _entry = OverlayEntry(
      builder: (_) => Positioned.fill(
        child: IgnorePointer(
          child: Center(
            child: Material(
              color: Colors.transparent,
              child: Container(
                constraints: const BoxConstraints(maxWidth: 300),
                margin: const EdgeInsets.symmetric(horizontal: 32),
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x33000000),
                      blurRadius: 20,
                      offset: Offset(0, 8),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.info_outline, color: Color(0xFFFF8A34)),
                    const SizedBox(height: 10),
                    Text(
                      message,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Color(0xFF252525)),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    overlay.insert(_entry!);
    _timer = Timer(const Duration(seconds: 2), () {
      if (_entry?.mounted == true) _entry?.remove();
      _entry = null;
    });
  }
}
