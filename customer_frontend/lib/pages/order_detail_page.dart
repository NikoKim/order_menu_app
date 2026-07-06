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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('订单已取消')));
      _reload();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) setState(() => _canceling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('订单详情')),
      body: FutureBuilder<OrderDetail>(
        future: _detailFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.wifi_off_outlined, color: warmOrange),
                    const SizedBox(height: 12),
                    Text(
                      snapshot.error.toString(),
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: pageMutedColor),
                    ),
                    const SizedBox(height: 16),
                    FilledButton(onPressed: _reload, child: const Text('重试')),
                  ],
                ),
              ),
            );
          }
          final order = snapshot.data!;
          final canCancel =
              order.status == 'SUBMITTED' ||
              order.status == 'ACCEPTED' ||
              order.status == 'PENDING' ||
              order.status == 'CONFIRMED';
          return ListView(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
            children: [
              _Panel(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          _statusText(order.status),
                          style: const TextStyle(
                            color: warmOrange,
                            fontSize: 22,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const Spacer(),
                        const Icon(
                          Icons.receipt_long_outlined,
                          color: warmOrange,
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text(
                      '订单号：${order.orderNo}',
                      style: const TextStyle(color: pageMutedColor),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      '下单时间：${order.createdAt.replaceFirst('T', ' ')}',
                      style: const TextStyle(color: pageMutedColor),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              _Panel(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      order.shopName,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 12),
                    for (final item in order.items)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        child: Row(
                          children: [
                            Expanded(child: Text(item.dishName)),
                            Text(
                              'x${item.quantity}',
                              style: const TextStyle(color: pageMutedColor),
                            ),
                            const SizedBox(width: 18),
                            Text('¥${item.subtotalAmount.toStringAsFixed(2)}'),
                          ],
                        ),
                      ),
                    const Divider(color: pageLineColor),
                    Row(
                      children: [
                        const Text(
                          '合计',
                          style: TextStyle(fontWeight: FontWeight.w800),
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
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '备注',
                      style: TextStyle(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(width: 18),
                    Expanded(
                      child: Text(
                        order.remark.isEmpty ? '无' : order.remark,
                        style: const TextStyle(color: pageMutedColor),
                      ),
                    ),
                  ],
                ),
              ),
              if (canCancel) ...[
                const SizedBox(height: 18),
                OutlinedButton.icon(
                  onPressed: _canceling ? null : () => _cancel(order),
                  icon: const Icon(Icons.cancel_outlined),
                  label: Text(_canceling ? '取消中...' : '取消订单'),
                ),
              ],
              const AppFooter(),
            ],
          );
        },
      ),
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: pageLineColor),
      ),
      child: child,
    );
  }
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
