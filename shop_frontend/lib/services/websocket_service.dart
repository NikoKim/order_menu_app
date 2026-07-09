import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'api_config.dart';
import 'auth_session.dart';

/// 标准化的 WebSocket 消息体
class WsMessage {
  final String module;
  final String action;
  final Map<String, dynamic> data;

  WsMessage({required this.module, required this.action, required this.data});

  factory WsMessage.fromJson(Map<String, dynamic> json) {
    return WsMessage(
      module: json['module'] ?? 'UNKNOWN',
      action: json['action'] ?? 'UNKNOWN',
      data: json['data'] ?? <String, dynamic>{},
    );
  }
}

class ShopWebSocketService {
  ShopWebSocketService._();
  static final ShopWebSocketService instance = ShopWebSocketService._();

  WebSocketChannel? _channel;
  bool _isConnected = false;

  // 全局广播流控制器，只流转标准化后的 WsMessage
  final _messageController = StreamController<WsMessage>.broadcast();

  // 提供一个基础流给所有页面
  Stream<WsMessage> get messageStream => _messageController.stream;

  // 高级用法：按模块过滤的流，方便具体页面按需监听
  Stream<WsMessage> streamForModule(String module) {
    return _messageController.stream.where((msg) => msg.module == module);
  }

  Future<void> connect() async {
    if (_isConnected) return;
    final token = await AuthSession.loadToken();
    if (token == null || token.isEmpty) return;

    final wsBaseUrl = ApiConfig.baseUrl.replaceFirst('http', 'ws');
    final uri = Uri.parse('$wsBaseUrl/ws/shop?token=$token');

    try {
      _channel = WebSocketChannel.connect(uri);
      _isConnected = true;
      debugPrint('WebSocket 连接成功');

      _channel!.stream.listen(
            (data) {
          try {
            final decoded = jsonDecode(data);
            final msg = WsMessage.fromJson(decoded);
            _messageController.add(msg); // 投递结构化消息
          } catch (e) {
            debugPrint('WebSocket 消息解析失败: $e');
          }
        },
        onDone: _handleDisconnect,
        onError: (error) => _handleDisconnect(),
      );
    } catch (e) {
      _handleDisconnect();
    }
  }

  void _handleDisconnect() {
    _isConnected = false;
    _channel?.sink.close();
    Future.delayed(const Duration(seconds: 5), connect); // 断线重连
  }

  void disconnect() {
    _channel?.sink.close();
    _isConnected = false;
  }
}