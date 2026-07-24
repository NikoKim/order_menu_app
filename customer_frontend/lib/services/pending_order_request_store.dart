import 'dart:convert';
import 'dart:math';

import 'package:shared_preferences/shared_preferences.dart';

import '../models.dart';

class PendingOrderRequestStore {
  PendingOrderRequestStore({
    this.reuseWindow = const Duration(minutes: 30),
    DateTime Function()? clock,
    String Function()? requestIdFactory,
  }) : _clock = clock ?? DateTime.now,
       _requestIdFactory = requestIdFactory ?? _newUuidV4;

  static const _preferenceKey = 'pending_order_request_v1';

  final Duration reuseWindow;
  final DateTime Function() _clock;
  final String Function() _requestIdFactory;

  Future<String> resolve(String payloadFingerprint) async {
    final preferences = await SharedPreferences.getInstance();
    final now = _clock();
    final saved = _decode(preferences.getString(_preferenceKey));
    if (saved != null &&
        saved.payloadFingerprint == payloadFingerprint &&
        !saved.createdAt.isAfter(now) &&
        now.difference(saved.createdAt) <= reuseWindow) {
      return saved.requestId;
    }

    final requestId = _requestIdFactory();
    await preferences.setString(
      _preferenceKey,
      jsonEncode({
        'requestId': requestId,
        'payloadFingerprint': payloadFingerprint,
        'createdAt': now.toUtc().toIso8601String(),
      }),
    );
    return requestId;
  }

  Future<void> clear(String requestId) async {
    final preferences = await SharedPreferences.getInstance();
    final saved = _decode(preferences.getString(_preferenceKey));
    if (saved?.requestId == requestId) {
      await preferences.remove(_preferenceKey);
    }
  }

  static String fingerprint({
    required int shopId,
    required List<CartItem> items,
    required String remark,
  }) {
    final canonicalItems =
        items.map((item) {
          final optionIds = [...item.selectedOptionIds]..sort();
          return <String, Object>{
            'dishId': item.dish.id,
            'quantity': item.quantity,
            'selectedOptionIds': optionIds,
          };
        }).toList()..sort((left, right) {
          final dishComparison = (left['dishId'] as int).compareTo(
            right['dishId'] as int,
          );
          if (dishComparison != 0) return dishComparison;
          return jsonEncode(
            left['selectedOptionIds'],
          ).compareTo(jsonEncode(right['selectedOptionIds']));
        });
    final canonicalPayload = jsonEncode({
      'shopId': shopId,
      'remark': remark,
      'items': canonicalItems,
    });
    // Store an exact, compact representation rather than a collision-prone hash.
    return base64UrlEncode(utf8.encode(canonicalPayload));
  }

  _PendingOrderRequest? _decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      final json = jsonDecode(raw) as Map<String, dynamic>;
      final requestId = json['requestId']?.toString() ?? '';
      final payloadFingerprint = json['payloadFingerprint']?.toString() ?? '';
      final createdAt = DateTime.tryParse(json['createdAt']?.toString() ?? '');
      if (requestId.isEmpty ||
          payloadFingerprint.isEmpty ||
          createdAt == null) {
        return null;
      }
      return _PendingOrderRequest(
        requestId: requestId,
        payloadFingerprint: payloadFingerprint,
        createdAt: createdAt,
      );
    } catch (_) {
      return null;
    }
  }
}

class _PendingOrderRequest {
  const _PendingOrderRequest({
    required this.requestId,
    required this.payloadFingerprint,
    required this.createdAt,
  });

  final String requestId;
  final String payloadFingerprint;
  final DateTime createdAt;
}

String _newUuidV4() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  String hex(int value) => value.toRadixString(16).padLeft(2, '0');
  final value = bytes.map(hex).join();
  return '${value.substring(0, 8)}-${value.substring(8, 12)}-'
      '${value.substring(12, 16)}-${value.substring(16, 20)}-'
      '${value.substring(20)}';
}
