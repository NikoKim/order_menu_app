import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import 'api_client.dart';

class CustomerWsMessage {
  const CustomerWsMessage({
    required this.module,
    required this.action,
    required this.data,
    this.timestamp,
  });

  factory CustomerWsMessage.fromJson(Map<String, dynamic> json) {
    final rawData = json['data'];
    return CustomerWsMessage(
      module: json['module']?.toString() ?? 'UNKNOWN',
      action: json['action']?.toString() ?? 'UNKNOWN',
      data: rawData is Map<String, dynamic>
          ? rawData
          : rawData is Map
          ? rawData.map((key, value) => MapEntry(key.toString(), value))
          : const <String, dynamic>{},
      timestamp: int.tryParse('${json['timestamp'] ?? ''}'),
    );
  }

  final String module;
  final String action;
  final Map<String, dynamic> data;
  final int? timestamp;

  Map<String, dynamic> toJson() => {
    'module': module,
    'action': action,
    'data': data,
    if (timestamp != null) 'timestamp': timestamp,
  };
}

class CustomerWebSocketService {
  CustomerWebSocketService._();

  static final CustomerWebSocketService instance = CustomerWebSocketService._();

  final _messageController = StreamController<CustomerWsMessage>.broadcast();
  WebSocketChannel? _channel;
  StreamSubscription? _subscription;
  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;
  String? _token;
  bool _connected = false;
  bool _connecting = false;
  bool _manualClosed = false;
  int _retryCount = 0;

  Stream<CustomerWsMessage> get messageStream => _messageController.stream;

  bool get isConnected => _connected;

  Stream<CustomerWsMessage> streamForModule(String module) =>
      messageStream.where((message) => message.module == module);

  Future<void> connect(String token, {bool force = false}) async {
    if (token.isEmpty || _connecting || (_connected && !force)) return;

    _token = token;
    _manualClosed = false;
    if (force) await _closeCurrent();
    _connecting = true;

    try {
      _channel = WebSocketChannel.connect(_uriForToken(token));
      _subscription = _channel!.stream.listen(
        _onData,
        onDone: _onDisconnected,
        onError: (Object error) {
          debugPrint('Customer WebSocket error: $error');
          _onDisconnected();
        },
        cancelOnError: true,
      );
      _connected = true;
      _connecting = false;
      _retryCount = 0;
      _startHeartbeat();
    } catch (error) {
      debugPrint('Customer WebSocket connection failed: $error');
      _connected = false;
      _connecting = false;
      _scheduleReconnect();
    }
  }

  void _onData(dynamic raw) {
    try {
      final decoded = jsonDecode(raw.toString());
      if (decoded is! Map) return;
      final message = CustomerWsMessage.fromJson(
        decoded.map((key, value) => MapEntry(key.toString(), value)),
      );
      if (message.module == 'SYSTEM' && message.action == 'PONG') return;
      if (message.module == 'SYSTEM' && message.action == 'AUTH_EXPIRED') {
        disconnect();
        return;
      }
      _messageController.add(message);
    } catch (error) {
      debugPrint('Customer WebSocket message parse failed: $error');
    }
  }

  void _startHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(const Duration(seconds: 25), (_) {
      send('SYSTEM', 'PING');
    });
  }

  void send(String module, String action, [Map<String, dynamic>? data]) {
    if (!_connected || _channel == null) return;
    _channel!.sink.add(
      jsonEncode(
        CustomerWsMessage(
          module: module,
          action: action,
          data: data ?? const <String, dynamic>{},
        ).toJson(),
      ),
    );
  }

  void _onDisconnected() {
    if (_manualClosed) return;
    _connected = false;
    _connecting = false;
    _heartbeatTimer?.cancel();
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (_manualClosed || _token == null || _token!.isEmpty) return;
    _reconnectTimer?.cancel();
    final seconds = _retryCount < 6 ? 3 + _retryCount * 2 : 15;
    _retryCount++;
    _reconnectTimer = Timer(Duration(seconds: seconds), () => connect(_token!));
  }

  Future<void> _closeCurrent() async {
    _heartbeatTimer?.cancel();
    _reconnectTimer?.cancel();
    await _subscription?.cancel();
    _subscription = null;
    try {
      await _channel?.sink.close();
    } catch (_) {}
    _channel = null;
    _connected = false;
    _connecting = false;
  }

  void disconnect() {
    _manualClosed = true;
    _token = null;
    _closeCurrent();
  }

  Uri _uriForToken(String token) {
    final apiUri = Uri.parse(ApiClient.defaultBaseUrl);
    return apiUri.replace(
      scheme: apiUri.scheme == 'https' ? 'wss' : 'ws',
      path: '/ws/customer',
      queryParameters: {'token': token},
    );
  }
}
