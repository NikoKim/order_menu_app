import 'dart:io';
import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'shop_api_service.dart';

typedef PushTokenProvider = Future<String?> Function();

class PushTokenCoordinator {
  PushTokenCoordinator({
    required this.tokenProvider,
    required this.register,
    required this.unregister,
    required this.platformProvider,
  });

  final PushTokenProvider tokenProvider;
  final Future<void> Function(String token, String platform) register;
  final Future<void> Function(String token) unregister;
  final String Function() platformProvider;
  String? _registeredToken;
  StreamSubscription<String>? _refreshSubscription;

  Future<void> sync() async {
    final token = (await tokenProvider())?.trim();
    if (token == null || token.isEmpty || token == _registeredToken) return;
    final previous = _registeredToken;
    if (previous != null) {
      try {
        await unregister(previous);
      } catch (_) {}
    }
    await register(token, platformProvider());
    _registeredToken = token;
  }

  Future<void> start({Stream<String>? tokenRefreshes}) async {
    await sync();
    await _refreshSubscription?.cancel();
    if (tokenRefreshes != null) {
      _refreshSubscription = tokenRefreshes.listen((token) async {
        final normalized = token.trim();
        if (normalized.isEmpty || normalized == _registeredToken) return;
        try {
          final previous = _registeredToken;
          if (previous != null) await unregister(previous);
          await register(normalized, platformProvider());
          _registeredToken = normalized;
        } catch (_) {}
      });
    }
  }

  Future<void> clear() async {
    await _refreshSubscription?.cancel();
    _refreshSubscription = null;
    final token = _registeredToken ?? (await tokenProvider())?.trim();
    _registeredToken = null;
    if (token == null || token.isEmpty) return;
    await unregister(token);
  }
}

class FirebasePushTokenProvider {
  FirebaseMessaging? _messaging;

  static const _apiKey = String.fromEnvironment('SHOP_FIREBASE_API_KEY');
  static const _appId = String.fromEnvironment('SHOP_FIREBASE_APP_ID');
  static const _senderId = String.fromEnvironment(
    'SHOP_FIREBASE_MESSAGING_SENDER_ID',
  );
  static const _projectId = String.fromEnvironment('SHOP_FIREBASE_PROJECT_ID');
  static const _storageBucket = String.fromEnvironment(
    'SHOP_FIREBASE_STORAGE_BUCKET',
  );
  static const _iosBundleId = String.fromEnvironment(
    'SHOP_FIREBASE_IOS_BUNDLE_ID',
  );

  bool get configured =>
      _apiKey.isNotEmpty &&
      _appId.isNotEmpty &&
      _senderId.isNotEmpty &&
      _projectId.isNotEmpty;

  Future<String?> initializeAndGetToken() async {
    const fallback = String.fromEnvironment('SHOP_PUSH_TOKEN');
    if (!configured) return fallback.isEmpty ? null : fallback;
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp(
          options: FirebaseOptions(
            apiKey: _apiKey,
            appId: _appId,
            messagingSenderId: _senderId,
            projectId: _projectId,
            storageBucket: _storageBucket.isEmpty ? null : _storageBucket,
            iosBundleId: _iosBundleId.isEmpty ? null : _iosBundleId,
          ),
        );
      }
      final messaging = FirebaseMessaging.instance;
      _messaging = messaging;
      await messaging.requestPermission(alert: true, badge: true, sound: true);
      return await messaging.getToken();
    } catch (_) {
      return null;
    }
  }

  Stream<String> get onTokenRefresh =>
      _messaging?.onTokenRefresh ?? const Stream<String>.empty();
}

String _platform() {
  if (Platform.isAndroid) return 'ANDROID';
  if (Platform.isIOS) return 'IOS';
  return Platform.operatingSystem.toUpperCase();
}

final firebasePushTokens = FirebasePushTokenProvider();

final shopPushTokens = PushTokenCoordinator(
  tokenProvider: firebasePushTokens.initializeAndGetToken,
  register: (token, platform) =>
      shopApi.registerPushDevice(token: token, platform: platform),
  unregister: shopApi.unregisterPushDevice,
  platformProvider: _platform,
);

Future<void> startShopPushTokens() async {
  await shopPushTokens.sync();
  await shopPushTokens.start(tokenRefreshes: firebasePushTokens.onTokenRefresh);
}
