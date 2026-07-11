import 'dart:async';

import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../services/customer_websocket_service.dart';
import '../widgets/app_footer.dart';
import 'order_detail_page.dart';

class OrdersPage extends StatefulWidget {
  const OrdersPage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<OrdersPage> createState() => _OrdersPageState();
}

class _OrdersPageState extends State<OrdersPage> {
  late Future<PageResult<OrderSummary>> _ordersFuture;
  StreamSubscription<CustomerWsMessage>? _webSocketSubscription;

  @override
  void initState() {
    super.initState();
    _ordersFuture = widget.api.orders();
    _webSocketSubscription = CustomerWebSocketService.instance
        .streamForModule('ORDER')
        .listen((message) {
          if (mounted &&
              const {
                'CREATED',
                'CANCELLED',
                'ACCEPTED',
                'REJECTED',
                'COMPLETED',
              }.contains(message.action)) {
            _reload();
          }
        });
  }

  @override
  void dispose() {
    _webSocketSubscription?.cancel();
    super.dispose();
  }

  void _reload() {
    setState(() {
      _ordersFuture = widget.api.orders();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      appBar: AppBar(
        title: const Text('我的订单'),
        backgroundColor: const Color(0xFFF7F7F7),
        foregroundColor: pageTextColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: warmOrange,
        backgroundColor: Colors.white,
        child: FutureBuilder<PageResult<OrderSummary>>(
          future: _ordersFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const _OrdersSkeleton();
            }
            if (snapshot.hasError) {
              return _StateMessage(
                icon: Icons.wifi_off_outlined,
                title: '订单加载失败',
                message: snapshot.error.toString(),
                actionText: '重试',
                onPressed: _reload,
              );
            }
            final orders = snapshot.data?.records ?? const [];
            if (orders.isEmpty) {
              return _EmptyOrders(onRefresh: _reload);
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              itemCount: orders.length + 2,
              separatorBuilder: (_, index) => index == 0
                  ? const SizedBox(height: 12)
                  : const SizedBox(height: 12),
              itemBuilder: (context, index) {
                if (index == 0) {
                  return const _PageHeaderCard(
                    icon: Icons.receipt_long_outlined,
                    title: '订单进度',
                    message: '订单会自动刷新，也可以下拉手动刷新查看最新状态。',
                  );
                }
                if (index == orders.length + 1) return const AppFooter();
                final order = orders[index - 1];
                return _OrderCard(order: order, api: widget.api);
              },
            );
          },
        ),
      ),
    );
  }
}

class _OrdersSkeleton extends StatelessWidget {
  const _OrdersSkeleton();

  @override
  Widget build(BuildContext context) {
    const skeletonColor = Color(0xFFFFF4E8);
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      itemCount: 5,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        if (index == 0) {
          return const _PageHeaderCard(
            icon: Icons.receipt_long_outlined,
            title: '订单进度',
            message: '订单会自动刷新，也可以下拉手动刷新查看最新状态。',
          );
        }
        return Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Container(height: 20, color: skeletonColor)),
                  const SizedBox(width: 32),
                  Container(
                    height: 24,
                    width: 60,
                    decoration: BoxDecoration(
                      color: skeletonColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Container(height: 14, width: 150, color: skeletonColor),
              const SizedBox(height: 12),
              Container(height: 14, width: 200, color: skeletonColor),
              const SizedBox(height: 14),
              Row(
                children: [
                  Container(height: 14, width: 60, color: skeletonColor),
                  const Spacer(),
                  Container(height: 20, width: 80, color: skeletonColor),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}

class _OrderCard extends StatelessWidget {
  const _OrderCard({required this.order, required this.api});

  final OrderSummary order;
  final CustomerApi api;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => OrderDetailPage(orderId: order.id, api: api),
          ),
        );
      },
      child: Ink(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    order.shopName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: pageTextColor,
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                _StatusBadge(text: _statusText(order.status)),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                const Icon(
                  Icons.access_time_rounded,
                  color: warmOrange,
                  size: 14,
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    _formatTime(order.createdAt),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: pageMutedColor, fontSize: 12),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFFF7F7F7),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Text(
                    '订单号',
                    style: TextStyle(color: pageMutedColor, fontSize: 12),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      order.orderNo,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.right,
                      style: const TextStyle(
                        color: pageTextColor,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                const Text('订单金额', style: TextStyle(color: pageMutedColor)),
                const Spacer(),
                Text(
                  '¥${order.totalAmount.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: warmOrange,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: softOrange,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: warmOrange,
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _EmptyOrders extends StatelessWidget {
  const _EmptyOrders({required this.onRefresh});

  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        const _PageHeaderCard(
          icon: Icons.receipt_long_outlined,
          title: '订单进度',
          message: '订单会自动刷新，也可以下拉手动刷新查看最新状态。',
        ),
        const SizedBox(height: 12),
        _StateMessage(
          icon: Icons.receipt_long_outlined,
          title: '暂无订单',
          message: '下单后可以在这里查看处理进度和订单详情。',
          actionText: '刷新',
          onPressed: onRefresh,
        ),
        const AppFooter(),
      ],
    );
  }
}

class _StateMessage extends StatelessWidget {
  const _StateMessage({
    required this.icon,
    required this.title,
    required this.message,
    required this.actionText,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionText;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 36),
      child: _WhiteCard(
        child: Column(
          children: [
            Icon(icon, color: Colors.grey[400], size: 56),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: pageTextColor,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: pageMutedColor, fontSize: 13),
            ),
            const SizedBox(height: 20),
            FilledButton.tonal(onPressed: onPressed, child: Text(actionText)),
          ],
        ),
      ),
    );
  }
}

class _PageHeaderCard extends StatelessWidget {
  const _PageHeaderCard({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return _WhiteCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: softOrange,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: warmOrange, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: pageTextColor,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  message,
                  style: const TextStyle(
                    color: pageMutedColor,
                    fontSize: 13,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _WhiteCard extends StatelessWidget {
  const _WhiteCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: child,
    );
  }
}

String _formatTime(String value) {
  return value.replaceFirst('T', ' ');
}

String _statusText(String status) {
  return switch (status) {
    'SUBMITTED' => '待处理',
    'ACCEPTED' => '已接单',
    'PENDING' => '待处理',
    'CONFIRMED' => '已接单',
    'COMPLETED' => '已完成',
    'CANCELED' || 'CANCELLED' => '已取消',
    _ => status.isEmpty ? '处理中' : status,
  };
}
