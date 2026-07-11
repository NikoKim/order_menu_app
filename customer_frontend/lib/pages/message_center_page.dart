import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';

class MessageCenterPage extends StatefulWidget {
  const MessageCenterPage({super.key});

  @override
  State<MessageCenterPage> createState() => _MessageCenterPageState();
}

class _MessageCenterPageState extends State<MessageCenterPage> {
  CustomerApi? _api;
  Future<NotificationList>? _notificationsFuture;
  StreamSubscription<CustomerWsMessage>? _wsSubscription;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_api != null) return;
    _api = CustomerApi(AppScope.of(context).api);
    _reload();
    _wsSubscription = CustomerWebSocketService.instance
        .streamForModule('NOTIFICATION')
        .listen((message) {
          if (message.action == 'CREATED' || message.action == 'READ_CHANGED') {
            _reload();
          }
        });
  }

  void _reload() {
    final api = _api;
    if (api == null || !mounted) return;
    setState(() => _notificationsFuture = api.notifications());
  }

  Future<void> _markAllRead() async {
    final api = _api;
    if (api == null) return;
    try {
      await api.markAllNotificationsRead();
      _reload();
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
    }
  }

  Future<void> _markRead(UserNotification notification) async {
    if (!notification.isUnread || _api == null) return;
    try {
      await _api!.markNotificationRead(notification.id);
      _reload();
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
    }
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final future = _notificationsFuture;
    return Scaffold(
      appBar: AppBar(
        title: const Text('消息通知'),
        actions: [
          FutureBuilder<NotificationList>(
            future: future,
            builder: (context, snapshot) {
              final canMarkAll = (snapshot.data?.unreadCount ?? 0) > 0;
              return TextButton(
                onPressed: canMarkAll ? _markAllRead : null,
                child: const Text('全部已读'),
              );
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        child: FutureBuilder<NotificationList>(
          future: future,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return ListView(
                children: [
                  const SizedBox(height: 180),
                  Center(
                    child: TextButton(
                      onPressed: _reload,
                      child: const Text('通知加载失败，点击重试'),
                    ),
                  ),
                ],
              );
            }
            final notifications = snapshot.data?.notifications ?? const [];
            if (notifications.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 180),
                  Center(child: Text('暂无消息通知')),
                ],
              );
            }
            return ListView.separated(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: notifications.length,
              separatorBuilder: (_, _) => const Divider(height: 1, indent: 72),
              itemBuilder: (context, index) {
                final notification = notifications[index];
                return ListTile(
                  onTap: () => _markRead(notification),
                  leading: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      CircleAvatar(
                        backgroundColor: const Color(0xFFFFF4E8),
                        child: Icon(
                          _notificationIcon(notification.type),
                          color: const Color(0xFFF28C28),
                        ),
                      ),
                      if (notification.isUnread)
                        const Positioned(
                          top: -1,
                          right: -1,
                          child: CircleAvatar(
                            radius: 5,
                            backgroundColor: Colors.red,
                          ),
                        ),
                    ],
                  ),
                  title: Text(
                    notification.title,
                    style: TextStyle(
                      fontWeight: notification.isUnread
                          ? FontWeight.w700
                          : FontWeight.w400,
                    ),
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 3),
                      Text(notification.content),
                      const SizedBox(height: 4),
                      Text(
                        _formatNotificationTime(notification.createdAt),
                        style: const TextStyle(fontSize: 12),
                      ),
                    ],
                  ),
                  isThreeLine: true,
                );
              },
            );
          },
        ),
      ),
    );
  }
}

IconData _notificationIcon(String type) => switch (type) {
  'ORDER_STATUS' => Icons.receipt_long_outlined,
  'FEEDBACK_REPLY' => Icons.rate_review_outlined,
  'FAMILY_APPROVED' || 'FAMILY_REJECTED' => Icons.home_outlined,
  _ => Icons.notifications_none,
};

String _formatNotificationTime(String value) => value.replaceFirst('T', ' ');
