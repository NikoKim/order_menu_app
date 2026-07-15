import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/services/push_token_service.dart';

void main() {
  test('missing push token safely skips registration', () async {
    var registrations = 0;
    final coordinator = PushTokenCoordinator(
      tokenProvider: () async => null,
      register: (_, _) async => registrations++,
      unregister: (_) async {},
      platformProvider: () => 'android',
    );

    await coordinator.sync();

    expect(registrations, 0);
  });

  test('token refresh registers the rotated firebase token', () async {
    final refreshes = StreamController<String>();
    final registered = <String>[];
    final unregistered = <String>[];
    final coordinator = PushTokenCoordinator(
      tokenProvider: () async => 'first-token',
      register: (token, _) async => registered.add(token),
      unregister: (token) async => unregistered.add(token),
      platformProvider: () => 'ANDROID',
    );

    await coordinator.start(tokenRefreshes: refreshes.stream);
    refreshes.add('next-token');
    await Future<void>.delayed(Duration.zero);

    expect(registered, ['first-token', 'next-token']);
    expect(unregistered, ['first-token']);
    await coordinator.clear();
    expect(unregistered, ['first-token', 'next-token']);
    await refreshes.close();
  });

  test('sync unregisters a token rotated while refresh was offline', () async {
    var token = 'first-token';
    final registered = <String>[];
    final unregistered = <String>[];
    final coordinator = PushTokenCoordinator(
      tokenProvider: () async => token,
      register: (value, _) async => registered.add(value),
      unregister: (value) async => unregistered.add(value),
      platformProvider: () => 'ANDROID',
    );

    await coordinator.sync();
    token = 'offline-rotated-token';
    await coordinator.sync();

    expect(registered, ['first-token', 'offline-rotated-token']);
    expect(unregistered, ['first-token']);
  });
}
