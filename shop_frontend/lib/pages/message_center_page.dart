import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../widgets/app_notice.dart';

class MessageCenterPage extends StatefulWidget {
  const MessageCenterPage({super.key});

  @override
  State<MessageCenterPage> createState() => _MessageCenterPageState();
}

class _MessageCenterPageState extends State<MessageCenterPage>
    with WidgetsBindingObserver {
  List<UserNotification>? _items;
  String? _error;
  StreamSubscription? _subscription;
  StreamSubscription<bool>? _connectionSubscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _reload();
    _subscription = ShopWebSocketService.instance
        .streamForModule('NOTIFICATION')
        .listen((_) => _reload());
    _connectionSubscription = ShopWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) => _reload());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _reload();
  }

  Future<void> _reload() async {
    try {
      final items = await shopApi.fetchMessageNotifications();
      if (mounted) {
        setState(() {
          _items = items;
          _error = null;
        });
      }
    } on ApiException catch (error) {
      if (mounted && _items == null) setState(() => _error = error.message);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _subscription?.cancel();
    _connectionSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F6F8),
    appBar: AppBar(
      title: const Text('消息通知'),
      actions: [
        TextButton(
          onPressed: () async {
            await shopApi.markAllMessageNotificationsRead();
            if (mounted) AppNotice.show(context, '已全部标为已读');
            await _reload();
          },
          child: const Text('全部已读'),
        ),
      ],
    ),
    body: _items == null
        ? _error == null
              ? const Center(child: CircularProgressIndicator())
              : Center(
                  child: TextButton(
                    onPressed: _reload,
                    child: const Text('通知加载失败，点击重试'),
                  ),
                )
        : RefreshIndicator(
            onRefresh: _reload,
            child: _items!.isEmpty
                ? ListView(
                    children: const [
                      SizedBox(height: 180),
                      Center(child: Text('暂无通知')),
                    ],
                  )
                : ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: _items!.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (_, index) {
                      final item = _items![index];
                      return Container(
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: ListTile(
                          onTap: item.read
                              ? null
                              : () async {
                                  await shopApi.markMessageNotificationRead(
                                    item.id,
                                  );
                                  await _reload();
                                },
                          leading: Icon(
                            item.read
                                ? Icons.notifications_none
                                : Icons.notifications,
                            color: item.read
                                ? Colors.grey
                                : const Color(0xFFF28C28),
                          ),
                          title: Text(item.title),
                          subtitle: Text(item.content),
                          trailing: Text(
                            item.createdAt,
                            style: const TextStyle(fontSize: 11),
                          ),
                        ),
                      );
                    },
                  ),
          ),
  );
}
