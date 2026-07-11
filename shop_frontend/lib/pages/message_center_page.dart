import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';

class MessageCenterPage extends StatefulWidget {
  const MessageCenterPage({super.key});
  @override
  State<MessageCenterPage> createState() => _MessageCenterPageState();
}

class _MessageCenterPageState extends State<MessageCenterPage> {
  late Future<List<UserNotification>> _future;
  StreamSubscription? _subscription;
  @override
  void initState() {
    super.initState();
    _future = shopApi.fetchMessageNotifications();
    _subscription = ShopWebSocketService.instance.streamForModule('NOTIFICATION').listen((_) => _reload());
  }
  void _reload() { if (mounted) setState(() => _future = shopApi.fetchMessageNotifications()); }
  @override
  void dispose() { _subscription?.cancel(); super.dispose(); }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('消息通知'), actions: [IconButton(tooltip: '全部已读', icon: const Icon(Icons.done_all), onPressed: () async { await shopApi.markAllMessageNotificationsRead(); _reload(); })]),
    body: FutureBuilder<List<UserNotification>>(future: _future, builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
      if (snapshot.hasError) return Center(child: TextButton(onPressed: _reload, child: const Text('通知加载失败，点击重试')));
      final items = snapshot.data ?? const <UserNotification>[];
      if (items.isEmpty) return const Center(child: Text('暂无通知'));
      return RefreshIndicator(onRefresh: () async => _reload(), child: ListView.separated(itemCount: items.length, separatorBuilder: (_, _) => const Divider(height: 1), itemBuilder: (_, index) {
        final item = items[index];
        return ListTile(leading: Icon(item.read ? Icons.notifications_none : Icons.notifications, color: item.read ? Colors.grey : const Color(0xFFF28C28)), title: Text(item.title), subtitle: Text(item.content), trailing: Text(item.createdAt, style: const TextStyle(fontSize: 11)));
      }));
    }),
  );
}
