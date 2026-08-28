import 'dart:async';
import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'api_client.dart';

abstract interface class PushTokenProvider {
  Future<String?> getToken();
  Stream<String> get onTokenRefresh;
}

class NoopPushTokenProvider implements PushTokenProvider {
  const NoopPushTokenProvider();
  @override
  Future<String?> getToken() async => null;
  @override
  Stream<String> get onTokenRefresh => const Stream.empty();
}

class FirebasePushTokenProvider implements PushTokenProvider {
  const FirebasePushTokenProvider({
    required this.apiKey,
    required this.appId,
    required this.messagingSenderId,
    required this.projectId,
    this.storageBucket,
    this.iosBundleId,
  });

  factory FirebasePushTokenProvider.fromEnvironment() =>
      const FirebasePushTokenProvider(
        apiKey: String.fromEnvironment('CUSTOMER_FIREBASE_API_KEY'),
        appId: String.fromEnvironment('CUSTOMER_FIREBASE_APP_ID'),
        messagingSenderId: String.fromEnvironment(
          'CUSTOMER_FIREBASE_MESSAGING_SENDER_ID',
        ),
        projectId: String.fromEnvironment('CUSTOMER_FIREBASE_PROJECT_ID'),
        storageBucket: String.fromEnvironment(
          'CUSTOMER_FIREBASE_STORAGE_BUCKET',
        ),
        iosBundleId: String.fromEnvironment('CUSTOMER_FIREBASE_IOS_BUNDLE_ID'),
      );

  final String apiKey;
  final String appId;
  final String messagingSenderId;
  final String projectId;
  final String? storageBucket;
  final String? iosBundleId;
  static bool _navigationBound = false;
  bool get configured =>
      apiKey.isNotEmpty &&
      appId.isNotEmpty &&
      messagingSenderId.isNotEmpty &&
      projectId.isNotEmpty;

  Future<FirebaseMessaging?> _messaging() async {
    if (!configured) return null;
    if (Firebase.apps.isEmpty) {
      await Firebase.initializeApp(
        options: FirebaseOptions(
          apiKey: apiKey,
          appId: appId,
          messagingSenderId: messagingSenderId,
          projectId: projectId,
          storageBucket: storageBucket?.isEmpty == true ? null : storageBucket,
          iosBundleId: iosBundleId?.isEmpty == true ? null : iosBundleId,
        ),
      );
    }
    final messaging = FirebaseMessaging.instance;
    await messaging.requestPermission(alert: true, badge: true, sound: true);
    if (!_navigationBound) {
      _navigationBound = true;
      FirebaseMessaging.onMessageOpenedApp.listen(
        (message) =>
            PushDeviceRegistration.instance.emitNavigation(message.data),
      );
      final initial = await messaging.getInitialMessage();
      if (initial != null) {
        scheduleMicrotask(
          () => PushDeviceRegistration.instance.emitNavigation(initial.data),
        );
      }
    }
    return messaging;
  }

  @override
  Future<String?> getToken() async => (await _messaging())?.getToken();

  @override
  Stream<String> get onTokenRefresh async* {
    final messaging = await _messaging();
    if (messaging != null) yield* messaging.onTokenRefresh;
  }
}

class PushDeviceRegistration {
  PushDeviceRegistration({this.provider = const NoopPushTokenProvider()});

  static final instance = PushDeviceRegistration(
    provider: FirebasePushTokenProvider.fromEnvironment(),
  );

  PushTokenProvider provider;
  String? _registeredToken;
  StreamSubscription<String>? _refreshSubscription;
  final _navigation = StreamController<Map<String, dynamic>>.broadcast();
  Map<String, dynamic>? _pendingNavigation;

  Stream<Map<String, dynamic>> get navigation => _navigation.stream;

  void emitNavigation(Map<String, dynamic> payload) {
    if (payload.isEmpty) return;
    if (!_navigation.hasListener) {
      _pendingNavigation = payload;
      return;
    }
    _navigation.add(payload);
  }

  Map<String, dynamic>? takePendingNavigation() {
    final value = _pendingNavigation;
    _pendingNavigation = null;
    return value;
  }

  void configure(PushTokenProvider provider) {
    _refreshSubscription?.cancel();
    _refreshSubscription = null;
    _registeredToken = null;
    this.provider = provider;
  }

  Future<void> sync(CustomerApi api) async {
    final token = await provider.getToken();
    if (token != null && token.isNotEmpty) await _register(api, token);
    _refreshSubscription ??= provider.onTokenRefresh.listen(
      (next) => _register(api, next).catchError((_) {}),
      onError: (_) {},
    );
  }

  Future<void> _register(CustomerApi api, String token) async {
    if (token.isEmpty || token == _registeredToken) return;
    final previous = _registeredToken;
    if (previous != null) {
      try {
        await api.unregisterPushDevice(previous);
      } catch (_) {}
    }
    await api.registerPushDevice(token: token, platform: _platform);
    _registeredToken = token;
  }

  String get _platform => Platform.isAndroid
      ? 'ANDROID'
      : Platform.isIOS
      ? 'IOS'
      : 'OTHER';

  Future<void> unregister(CustomerApi api) async {
    final token = _registeredToken;
    await _refreshSubscription?.cancel();
    _refreshSubscription = null;
    _registeredToken = null;
    if (token == null) return;
    await api.unregisterPushDevice(token);
  }
}
