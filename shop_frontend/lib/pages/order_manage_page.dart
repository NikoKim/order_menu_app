import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';
import 'shop_order_detail_page.dart';

class OrderManagePage extends StatefulWidget {
  const OrderManagePage({super.key});

  @override
  State<OrderManagePage> createState() => _OrderManagePageState();
}

class _OrderManagePageState extends State<OrderManagePage> {
  static const _filters = <String?>[
    null,
    'SUBMITTED',
    'ACCEPTED',
    'COMPLETED',
    'CANCELLED',
  ];

  List<OrderSummary> _orders = const [];
  Timer? _refreshTimer;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
    _refreshTimer = Timer.periodic(const Duration(seconds: 6), (_) {
      if (mounted) {
        _load(silent: true);
      }
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final result = await shopApi.fetchOrders(pageSize: 100);
      if (mounted) {
        setState(() {
          _orders = result.records;
          _error = null;
        });
      }
    } on ApiException catch (error) {
      if (mounted && !silent) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted && !silent) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: _filters.length,
      child: Scaffold(
        backgroundColor: Colors.white,
        appBar: AppBar(
          title: const Text('订单管理'),
          backgroundColor: Colors.white,
          foregroundColor: textColor,
          elevation: 0,
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(52),
            child: Align(
              alignment: Alignment.centerLeft,
              child: TabBar(
                isScrollable: true,
                indicatorColor: warmOrange,
                labelColor: warmOrange,
                unselectedLabelColor: mutedColor,
                indicatorSize: TabBarIndicatorSize.label,
                tabs: [
                  for (final filter in _filters)
                    Tab(child: Text(_statusText(filter))),
                ],
              ),
            ),
          ),
        ),
        body: RefreshIndicator(
          color: warmOrange,
          onRefresh: _load,
          child: _buildBody(),
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return ListView(
        children: [
          const SizedBox(height: 220),
          const Center(child: CircularProgressIndicator(color: warmOrange)),
        ],
      );
    }
    if (_error != null) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [_ErrorPanel(message: _error!, onRetry: _load)],
      );
    }

    return TabBarView(
      children: [
        for (final filter in _filters)
          _OrderList(
            orders: filter == null
                ? _orders
                : _orders.where((order) => order.status == filter).toList(),
            onAction: _handleAction,
            onReject: _rejectOrder,
            onOpen: _openDetail,
          ),
      ],
    );
  }

  Future<void> _openDetail(OrderSummary order) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => ShopOrderDetailPage(orderId: order.id)),
    );
    if (changed == true) {
      await _load();
    }
  }

  Future<void> _handleAction(OrderSummary order) async {
    try {
      if (order.status == 'SUBMITTED') {
        await shopApi.acceptOrder(order.id);
        _showSnackBar('已接单');
      } else if (order.status == 'ACCEPTED') {
        await shopApi.completeOrder(order.id);
        _showSnackBar('订单已完成');
      }
      await _load();
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    }
  }

  Future<void> _rejectOrder(OrderSummary order) async {
    try {
      await shopApi.rejectOrder(order.id);
      _showSnackBar('已不接单，库存已退回');
      await _load();
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
      );
  }
}

class _OrderList extends StatelessWidget {
  const _OrderList({
    required this.orders,
    required this.onAction,
    required this.onReject,
    required this.onOpen,
  });

  final List<OrderSummary> orders;
  final ValueChanged<OrderSummary> onAction;
  final ValueChanged<OrderSummary> onReject;
  final ValueChanged<OrderSummary> onOpen;

  @override
  Widget build(BuildContext context) {
    if (orders.isEmpty) {
      return const Center(
        child: Text('暂无订单', style: TextStyle(color: mutedColor)),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: orders.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        return _OrderCard(
          order: orders[index],
          onAction: () => onAction(orders[index]),
          onReject: () => onReject(orders[index]),
          onOpen: () => onOpen(orders[index]),
        );
      },
    );
  }
}

class _OrderCard extends StatelessWidget {
  const _OrderCard({
    required this.order,
    required this.onAction,
    required this.onReject,
    required this.onOpen,
  });

  final OrderSummary order;
  final VoidCallback onAction;
  final VoidCallback onReject;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    final actionLabel = _actionLabelFor(order.status);

    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: onOpen,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: lineColor),
          borderRadius: BorderRadius.circular(8),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    '订单号 ${order.orderNo}',
                    style: const TextStyle(
                      color: textColor,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                _StatusBadge(status: order.status),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              _formatTime(order.createdAt),
              style: const TextStyle(color: mutedColor, fontSize: 13),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Text(
                  '¥${order.totalAmount.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: warmOrange,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const Spacer(),
                if (order.status == 'SUBMITTED') ...[
                  OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.redAccent,
                      side: const BorderSide(color: Colors.redAccent),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    onPressed: onReject,
                    child: const Text('不接单'),
                  ),
                  const SizedBox(width: 8),
                ],
                if (actionLabel != null)
                  OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: warmOrange,
                      side: const BorderSide(color: warmOrange),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    onPressed: onAction,
                    child: Text(actionLabel),
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
  const _StatusBadge({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: softOrange,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        _statusText(status),
        style: const TextStyle(
          color: warmOrange,
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: softOrange,
      borderRadius: BorderRadius.circular(8),
      child: ListTile(
        leading: const Icon(Icons.info_outline, color: warmOrange),
        title: Text(message),
        trailing: TextButton(onPressed: onRetry, child: const Text('重试')),
      ),
    );
  }
}

String? _actionLabelFor(String status) {
  if (status == 'SUBMITTED') {
    return '接单';
  }
  if (status == 'ACCEPTED') {
    return '完成';
  }
  return null;
}

String _statusText(String? status) {
  switch (status) {
    case null:
      return '全部';
    case 'SUBMITTED':
      return '待接单';
    case 'ACCEPTED':
      return '制作中';
    case 'COMPLETED':
      return '已完成';
    case 'CANCELLED':
      return '已取消';
    default:
      return status;
  }
}

String _formatTime(String value) {
  return value.replaceFirst('T', ' ');
}
