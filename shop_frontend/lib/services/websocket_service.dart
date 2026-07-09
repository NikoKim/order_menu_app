import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import 'api_config.dart';
import 'auth_session.dart';

class WsMessage {
  final String module;
  final String action;
  final Map<String, dynamic> data;
  final int? timestamp;

  const WsMessage({
    required this.module,
    required this.action,
    required this.data,
    this.timestamp,
  });

  factory WsMessage.fromJson(Map<String, dynamic> json) {
    final rawData = json['data'];

    return WsMessage(
      module: json['module']?.toString() ?? 'UNKNOWN',
      action: json['action']?.toString() ?? 'UNKNOWN',
      data: rawData is Map<String, dynamic>
          ? rawData
          : rawData is Map
          ? rawData.map((key, value) => MapEntry(key.toString(), value))
          : <String, dynamic>{},
      timestamp: int.tryParse('${json['timestamp'] ?? ''}'),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'module': module,
      'action': action,
      'data': data,
      if (timestamp != null) 'timestamp': timestamp,
    };
  }
}

class ShopWebSocketService {
  ShopWebSocketService._();

  static final ShopWebSocketService instance = ShopWebSocketService._();

  WebSocketChannel? _channel;
  StreamSubscription? _subscription;
  Timer? _reconnectTimer;
  Timer? _heartbeatTimer;

  bool _connected = false;
  bool _connecting = false;
  bool _manualClosed = false;
  int _retryCount = 0;

  final _messageController = StreamController<WsMessage>.broadcast();

  Stream<WsMessage> get messageStream => _messageController.stream;

  Stream<WsMessage> streamForModule(String module) {
    return messageStream.where((msg) => msg.module == module);
  }

  bool get isConnected => _connected;

  Future<void> connect({bool force = false}) async {
    if (_connecting) return;
    if (_connected && !force) return;

    _manualClosed = false;

    final token = await AuthSession.loadToken();
    if (token == null || token.isEmpty) {
      debugPrint('Shop WS 未连接：没有 token');
      return;
    }

    if (force) {
      await _closeCurrent();
    }

    _connecting = true;

    final uri = Uri.parse(
      '${ApiConfig.wsBaseUrl}/shop?token=${Uri.encodeQueryComponent(token)}',
    );

    try {
      _channel = WebSocketChannel.connect(uri);

      _subscription = _channel!.stream.listen(
        _onData,
        onDone: _onDisconnected,
        onError: (error) {
          debugPrint('Shop WS error: $error');
          _onDisconnected();
        },
        cancelOnError: true,
      );

      _connected = true;
      _connecting = false;
      _retryCount = 0;

      _startHeartbeat();

      debugPrint('Shop WS 已连接：$uri');
    } catch (e) {
      debugPrint('Shop WS 连接失败: $e');
      _connecting = false;
      _connected = false;
      _scheduleReconnect();
    }
  }

  void _onData(dynamic raw) {
    try {
      final decoded = jsonDecode(raw.toString());

      if (decoded is! Map) {
        return;
      }

      final msg = WsMessage.fromJson(
        decoded.map((key, value) => MapEntry(key.toString(), value)),
      );

      if (msg.module == 'SYSTEM' && msg.action == 'PONG') {
        return;
      }

      if (msg.module == 'SYSTEM' && msg.action == 'AUTH_EXPIRED') {
        disconnect();
        return;
      }

      _messageController.add(msg);
    } catch (e) {
      debugPrint('Shop WS 消息解析失败: $e, raw=$raw');
    }
  }

  void send(String module, String action, [Map<String, dynamic>? data]) {
    if (!_connected || _channel == null) return;

    final msg = WsMessage(
      module: module,
      action: action,
      data: data ?? <String, dynamic>{},
    );

    _channel!.sink.add(jsonEncode(msg.toJson()));
  }

  void _startHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(const Duration(seconds: 25), (_) {
      send('SYSTEM', 'PING');
    });
  }

  void _onDisconnected() {
    if (_manualClosed) return;

    _connected = false;
    _connecting = false;
    _heartbeatTimer?.cancel();

    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (_manualClosed) return;

    _reconnectTimer?.cancel();

    final seconds = _retryCount < 6 ? 3 + _retryCount * 2 : 15;
    _retryCount++;

    _reconnectTimer = Timer(Duration(seconds: seconds), () {
      connect();
    });
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
    _closeCurrent();
  }

  void dispose() {
    disconnect();
    _messageController.close();
  }
}