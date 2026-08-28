import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../widgets/app_notice.dart';
import '../utils/moment_time_formatter.dart';
import 'chat_detail_page.dart';
import 'moment_detail_page.dart';
import 'order_detail_page.dart';
import 'my_feedback_page.dart';
import 'my_families_page.dart';

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
    setState(() {
      _notificationsFuture = api.notifications();
    });
  }

  Future<void> _markAllRead() async {
    final api = _api;
    if (api == null) return;
    try {
      await api.markAllNotificationsRead();
      _reload();
    } on ApiException catch (error) {
      if (mounted) {
        AppNotice.show(context, error.message);
      }
    }
  }

  int? _payloadId(UserNotification notification, String key) {
    final value = notification.payloadData[key];
    return value is num ? value.toInt() : int.tryParse('$value');
  }

  int? _conversationId(UserNotification notification) =>
      _payloadId(notification, 'conversationId');

  Future<void> _handleNotificationTap(UserNotification notification) async {
    final api = _api;
    if (api == null) return;
    if (notification.isUnread) {
      try {
        await api.markNotificationRead(notification.id);
      } on ApiException catch (error) {
        if (mounted) AppNotice.show(context, error.message);
        return;
      }
    }
    if (!mounted) return;
    _reload();
    final momentId = _payloadId(notification, 'momentId');
    if (momentId != null || notification.type.startsWith('MOMENT_')) {
      if (momentId == null) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => MomentDetailPage(
            momentId: momentId,
            highlightCommentId: _payloadId(notification, 'commentId'),
          ),
        ),
      );
      return;
    }
    final conversationId = _conversationId(notification);
    if (conversationId != null && notification.type != 'FAMILY_GROUP_INVITE') {
      try {
        final conversations = await api.conversations();
        final conversation = conversations
            .where((item) => item.id == conversationId)
            .firstOrNull;
        if (conversation != null && mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ChatDetailPage(conversation: conversation),
            ),
          );
        }
      } on ApiException catch (error) {
        if (mounted) AppNotice.show(context, error.message);
      }
      return;
    }
    final orderId = _payloadId(notification, 'orderId');
    if (orderId != null) {
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => OrderDetailPage(orderId: orderId, api: api),
        ),
      );
      return;
    }
    if (notification.type.startsWith('FEEDBACK_')) {
      await Navigator.of(
        context,
      ).push(MaterialPageRoute(builder: (_) => MyFeedbackPage(api: api)));
      return;
    }
    if (notification.type.startsWith('FAMILY_')) {
      await Navigator.of(
        context,
      ).push(MaterialPageRoute(builder: (_) => MyFamiliesPage(api: api)));
    }
  }

  Future<void> _handleInvitation(
    UserNotification notification,
    bool accept,
  ) async {
    final id = _conversationId(notification);
    if (id == null || _api == null) return;
    try {
      if (accept) {
        await _api!.acceptFamilyGroupInvitation(id);
      } else {
        await _api!.declineFamilyGroupInvitation(id);
      }
      await _api!.markNotificationRead(notification.id);
      if (mounted) AppNotice.show(context, accept ? '已加入家庭群聊' : '已拒绝群聊邀请');
      _reload();
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
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
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF5F5F5),
        surfaceTintColor: Colors.transparent,
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
              padding: const EdgeInsets.all(16),
              itemCount: notifications.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final notification = notifications[index];
                return Material(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    onTap: () => _handleNotificationTap(notification),
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
                        Text(
                          notification.content,
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (notification.type == 'FAMILY_GROUP_INVITE' &&
                            notification.isUnread)
                          Row(
                            children: [
                              TextButton(
                                onPressed: () =>
                                    _handleInvitation(notification, false),
                                child: const Text('暂不加入'),
                              ),
                              FilledButton(
                                onPressed: () =>
                                    _handleInvitation(notification, true),
                                child: const Text('加入群聊'),
                              ),
                            ],
                          ),
                        const SizedBox(height: 4),
                        Text(
                          _formatNotificationTime(notification.createdAt),
                          style: const TextStyle(fontSize: 12),
                        ),
                      ],
                    ),
                    isThreeLine: true,
                  ),
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
  'MOMENT_LIKED' => Icons.favorite_outline_rounded,
  'MOMENT_COMMENTED' || 'MOMENT_COMMENT_REPLIED' => Icons.mode_comment_outlined,
  'MOMENT_REACTED' => Icons.emoji_emotions_outlined,
  'MOMENT_MENTIONED' ||
  'MOMENT_COMMENT_MENTIONED' => Icons.alternate_email_rounded,
  'MOMENT_QUOTED' || 'MOMENT_COMMENT_QUOTED' => Icons.format_quote_rounded,
  'CHAT_MESSAGE' => Icons.chat_bubble_outline_rounded,
  _ => Icons.notifications_none,
};

String _formatNotificationTime(String value) => formatMomentTime(value);
