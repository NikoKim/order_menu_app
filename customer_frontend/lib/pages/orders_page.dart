import 'dart:async';

import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
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
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _ordersFuture = widget.api.orders();
    _refreshTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      if (mounted) {
        _reload();
      }
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
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
      appBar: AppBar(title: const Text('我的订单')),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        child: FutureBuilder<PageResult<OrderSummary>>(
          future: _ordersFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const _OrdersSkeleton();
            }
            if (snapshot.hasError) {
              return _StateMessage(
                title: '订单加载失败',
                message: snapshot.error.toString(),
                onPressed: _reload,
              );
            }
            final orders = snapshot.data?.records ?? const [];
            if (orders.isEmpty) {
              return const _EmptyOrders();
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
              itemCount: orders.length + 1,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                if (index == orders.length) return const AppFooter();
                final order = orders[index];
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
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      itemCount: 4,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (_, _) => Container(
        height: 118,
        decoration: BoxDecoration(
          color: softOrange,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: pageLineColor),
        ),
      ),
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
      borderRadius: BorderRadius.circular(8),
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
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: pageLineColor),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    order.shopName,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                _StatusBadge(text: _statusText(order.status)),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              _formatTime(order.createdAt),
              style: const TextStyle(color: pageMutedColor),
            ),
            const SizedBox(height: 12),
            Text(
              '订单号：${order.orderNo}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: pageMutedColor),
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
        style: const TextStyle(color: warmOrange, fontWeight: FontWeight.w700),
      ),
    );
  }
}

class _EmptyOrders extends StatelessWidget {
  const _EmptyOrders();

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: const [
        SizedBox(height: 180),
        Icon(Icons.receipt_long_outlined, color: warmOrange, size: 42),
        SizedBox(height: 12),
        Center(
          child: Text('暂无订单', style: TextStyle(fontWeight: FontWeight.w800)),
        ),
      ],
    );
  }
}

class _StateMessage extends StatelessWidget {
  const _StateMessage({
    required this.title,
    required this.message,
    required this.onPressed,
  });

  final String title;
  final String message;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off_outlined, color: warmOrange, size: 42),
            const SizedBox(height: 12),
            Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: pageMutedColor),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: onPressed, child: const Text('重试')),
          ],
        ),
      ),
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
