import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

class OrderDetailPage extends StatefulWidget {
  const OrderDetailPage({super.key, required this.orderId, required this.api});

  final int orderId;
  final CustomerApi api;

  @override
  State<OrderDetailPage> createState() => _OrderDetailPageState();
}

class _OrderDetailPageState extends State<OrderDetailPage> {
  late Future<OrderDetail> _detailFuture;
  bool _canceling = false;

  @override
  void initState() {
    super.initState();
    _detailFuture = widget.api.orderDetail(widget.orderId);
  }

  void _reload() {
    setState(() {
      _detailFuture = widget.api.orderDetail(widget.orderId);
    });
  }

  Future<void> _cancel(OrderDetail order) async {
    setState(() => _canceling = true);
    try {
      await widget.api.cancelOrder(order.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(
            content: Text('订单已取消'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      _reload();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(error.toString()),
            behavior: SnackBarBehavior.floating,
          ),
        );
    } finally {
      if (mounted) setState(() => _canceling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      appBar: AppBar(
        title: const Text('订单详情'),
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
        child: FutureBuilder<OrderDetail>(
          future: _detailFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const _OrderDetailSkeleton();
            }
            if (snapshot.hasError) {
              return _StateMessage(
                icon: Icons.wifi_off_outlined,
                title: '订单详情加载失败',
                message: snapshot.error.toString(),
                actionText: '重试',
                onPressed: _reload,
              );
            }

            final order = snapshot.data!;
            final canCancel =
                order.status == 'SUBMITTED' ||
                    order.status == 'ACCEPTED' ||
                    order.status == 'PENDING' ||
                    order.status == 'CONFIRMED';

            return ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [
                _OrderStatusCard(order: order),
                const SizedBox(height: 12),
                _Panel(
                  title: order.shopName,
                  icon: Icons.storefront_outlined,
                  child: Column(
                    children: [
                      for (var index = 0; index < order.items.length; index++) ...[
                        _OrderItemRow(item: order.items[index]),
                        if (index != order.items.length - 1)
                          const Divider(height: 20, color: pageLineColor),
                      ],
                      const SizedBox(height: 4),
                      const Divider(color: pageLineColor),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          const Text(
                            '合计',
                            style: TextStyle(
                              color: pageTextColor,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          const Spacer(),
                          Text(
                            '¥${order.totalAmount.toStringAsFixed(2)}',
                            style: const TextStyle(
                              color: warmOrange,
                              fontSize: 20,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                _Panel(
                  title: '订单备注',
                  icon: Icons.sticky_note_2_outlined,
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF7F7F7),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      order.remark.isEmpty ? '无' : order.remark,
                      style: const TextStyle(
                        color: pageMutedColor,
                        fontSize: 14,
                        height: 1.45,
                      ),
                    ),
                  ),
                ),
                if (canCancel) ...[
                  const SizedBox(height: 12),
                  _WhiteCard(
                    child: OutlinedButton.icon(
                      onPressed: _canceling ? null : () => _cancel(order),
                      icon: _canceling
                          ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: warmOrange,
                        ),
                      )
                          : const Icon(Icons.cancel_outlined),
                      label: Text(_canceling ? '取消中...' : '取消订单'),
                    ),
                  ),
                ],
                const AppFooter(),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _OrderStatusCard extends StatelessWidget {
  const _OrderStatusCard({required this.order});

  final OrderDetail order;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
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
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: softOrange,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.receipt_long_outlined, color: warmOrange, size: 22),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _statusText(order.status),
                      style: const TextStyle(
                        color: pageTextColor,
                        fontSize: 20,
                        fontWeight: FontWeight.w900,
                        height: 1.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      '订单状态',
                      style: TextStyle(color: pageMutedColor, fontSize: 12),
                    ),
                  ],
                ),
              ),
              _StatusBadge(text: _statusText(order.status)),
            ],
          ),
          const SizedBox(height: 14),
          _InfoRow(
            icon: Icons.confirmation_number_outlined,
            label: '订单号',
            value: order.orderNo,
          ),
          const SizedBox(height: 8),
          _InfoRow(
            icon: Icons.schedule_rounded,
            label: '下单时间',
            value: _formatTime(order.createdAt),
          ),
        ],
      ),
    );
  }
}

class _OrderItemRow extends StatelessWidget {
  const _OrderItemRow({required this.item});

  final dynamic item;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Text(
            item.dishName,
            style: const TextStyle(
              color: pageTextColor,
              fontSize: 15,
              fontWeight: FontWeight.w600,
              height: 1.35,
            ),
          ),
        ),
        const SizedBox(width: 12),
        Text(
          'x${item.quantity}',
          style: const TextStyle(color: pageMutedColor, fontSize: 13),
        ),
        const SizedBox(width: 18),
        Text(
          '¥${item.subtotalAmount.toStringAsFixed(2)}',
          style: const TextStyle(
            color: pageTextColor,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: warmOrange, size: 15),
        const SizedBox(width: 6),
        Text(
          '$label：',
          style: const TextStyle(color: pageMutedColor, fontSize: 13),
        ),
        Expanded(
          child: Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: pageTextColor, fontSize: 13),
          ),
        ),
      ],
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({required this.title, required this.icon, required this.child});

  final String title;
  final IconData icon;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return _WhiteCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: warmOrange, size: 18),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: pageTextColor,
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          child,
        ],
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

class _OrderDetailSkeleton extends StatelessWidget {
  const _OrderDetailSkeleton();

  @override
  Widget build(BuildContext context) {
    const skeletonColor = Color(0xFFFFF4E8);
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        _SkeletonCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: skeletonColor,
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(child: Container(height: 22, color: skeletonColor)),
                  const SizedBox(width: 16),
                  Container(
                    width: 60,
                    height: 26,
                    decoration: BoxDecoration(
                      color: skeletonColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Container(height: 14, width: double.infinity, color: skeletonColor),
              const SizedBox(height: 10),
              Container(height: 14, width: 180, color: skeletonColor),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _SkeletonCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(height: 20, width: 160, color: skeletonColor),
              const SizedBox(height: 18),
              for (var i = 0; i < 3; i++) ...[
                Row(
                  children: [
                    Expanded(child: Container(height: 16, color: skeletonColor)),
                    const SizedBox(width: 36),
                    Container(height: 16, width: 60, color: skeletonColor),
                  ],
                ),
                if (i != 2) const SizedBox(height: 14),
              ],
              const SizedBox(height: 18),
              Row(
                children: [
                  Container(height: 18, width: 56, color: skeletonColor),
                  const Spacer(),
                  Container(height: 22, width: 86, color: skeletonColor),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _SkeletonCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(height: 20, width: 100, color: skeletonColor),
              const SizedBox(height: 14),
              Container(
                height: 52,
                width: double.infinity,
                decoration: BoxDecoration(
                  color: skeletonColor,
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ],
          ),
        ),
        const AppFooter(),
      ],
    );
  }
}

class _SkeletonCard extends StatelessWidget {
  const _SkeletonCard({required this.child});

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
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        _WhiteCard(
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
        const AppFooter(),
      ],
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
  if (value.isEmpty) return '';
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
