import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import 'shop_order_detail_page.dart';

const _warmOrange = Color(0xFFF28C28);
const _softOrange = Color(0xFFFFF4E8);
const _pageBgColor = Color(0xFFF5F6F8);
const _lineColor = Color(0xFFF0E7DE);
const _textColor = Color(0xFF242424);
const _mutedColor = Color(0xFF77716B);

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
  StreamSubscription? _wsSubscription;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();

    _wsSubscription = ShopWebSocketService.instance
        .streamForModule('ORDER')
        .listen((msg) {
      if (!mounted) return;

      final shouldRefresh = {
        'CREATED',
        'UPDATED',
        'ACCEPTED',
        'REJECTED',
        'COMPLETED',
        'CANCELED',
      }.contains(msg.action);

      if (shouldRefresh) {
        _load(silent: true);
      }
    });
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
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
        backgroundColor: _pageBgColor, // 现代化浅灰背景
        appBar: AppBar(
          title: const Text('订单管理', style: TextStyle(fontWeight: FontWeight.w700)),
          backgroundColor: Colors.white,
          foregroundColor: _textColor,
          elevation: 0,
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(52),
            child: Align(
              alignment: Alignment.centerLeft,
              child: TabBar(
                isScrollable: true,
                tabAlignment: TabAlignment.start,
                indicatorColor: _warmOrange,
                labelColor: _warmOrange,
                unselectedLabelColor: _mutedColor,
                indicatorSize: TabBarIndicatorSize.label,
                dividerColor: const Color(0xFFF0E7DE),
                tabs: [
                  for (final filter in _filters)
                    Tab(child: Text(_statusText(filter))),
                ],
              ),
            ),
          ),
        ),
        body: RefreshIndicator(
          color: _warmOrange,
          backgroundColor: Colors.white,
          onRefresh: _load,
          child: _buildBody(),
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: 5, // 渲染5个订单骨架卡片
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (_, __) => Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: const [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _SkeletonBox(width: 160, height: 20),
                  _SkeletonBox(width: 60, height: 24, borderRadius: 6),
                ],
              ),
              SizedBox(height: 12),
              _SkeletonBox(width: 120, height: 14),
              SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _SkeletonBox(width: 80, height: 28),
                  _SkeletonBox(width: 72, height: 32, borderRadius: 8),
                ],
              ),
            ],
          ),
        ),
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
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: const [
            Icon(Icons.receipt_long_outlined, size: 54, color: Color(0xFFE0E0E0)),
            SizedBox(height: 16),
            Text('该分类下暂无订单', style: TextStyle(color: _mutedColor)),
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: orders.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
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
      borderRadius: BorderRadius.circular(12),
      onTap: onOpen,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        padding: const EdgeInsets.all(18),
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
                      color: _textColor,
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                _StatusBadge(status: order.status),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              _formatTime(order.createdAt),
              style: const TextStyle(color: _mutedColor, fontSize: 13),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 14),
              child: Divider(color: _lineColor, height: 1),
            ),
            Row(
              children: [
                const Text('实付 ', style: TextStyle(color: _mutedColor, fontSize: 13)),
                Text(
                  '¥${order.totalAmount.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: _textColor,
                    fontSize: 20,
                    fontWeight: FontWeight.w900,
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
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      minimumSize: const Size(64, 36),
                    ),
                    onPressed: onReject,
                    child: const Text('不接单'),
                  ),
                  const SizedBox(width: 10),
                ],
                if (actionLabel != null)
                  FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: _warmOrange,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      minimumSize: const Size(64, 36),
                    ),
                    onPressed: onAction,
                    child: Text(actionLabel, style: const TextStyle(fontWeight: FontWeight.bold)),
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
    Color bgColor;
    Color textColor;

    // 针对不同状态进行色彩分级，让商家一眼识别订单优先级
    switch (status) {
      case 'SUBMITTED': // 待接单：高亮橙色警示
        bgColor = _softOrange;
        textColor = _warmOrange;
        break;
      case 'ACCEPTED': // 制作中：蓝色/青色系代表进行中
        bgColor = const Color(0xFFE3F2FD);
        textColor = const Color(0xFF1976D2);
        break;
      case 'COMPLETED': // 已完成：绿色或沉稳的灰色代表完结
        bgColor = const Color(0xFFF5F6F8);
        textColor = const Color(0xFF388E3C);
        break;
      case 'CANCELLED': // 已取消：纯灰色减弱视觉干扰
        bgColor = const Color(0xFFF5F6F8);
        textColor = _mutedColor;
        break;
      default:
        bgColor = _softOrange;
        textColor = _warmOrange;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        _statusText(status),
        style: TextStyle(
          color: textColor,
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
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _softOrange,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: _warmOrange),
          const SizedBox(width: 12),
          Expanded(child: Text(message, style: const TextStyle(color: _textColor))),
          TextButton(
            onPressed: onRetry,
            child: const Text('重试', style: TextStyle(color: _warmOrange)),
          ),
        ],
      ),
    );
  }
}

String? _actionLabelFor(String status) {
  if (status == 'SUBMITTED') return '接单';
  if (status == 'ACCEPTED') return '制作完成';
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

// --------------------------------------------------------------------------
// 骨架屏占位组件
// --------------------------------------------------------------------------
class _SkeletonBox extends StatefulWidget {
  final double width;
  final double height;
  final double borderRadius;

  const _SkeletonBox({
    required this.width,
    required this.height,
    this.borderRadius = 4,
  });

  @override
  State<_SkeletonBox> createState() => _SkeletonBoxState();
}

class _SkeletonBoxState extends State<_SkeletonBox> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<Color?> _colorTween;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat(reverse: true);
    _colorTween = ColorTween(
      begin: const Color(0xFFF0F0F0),
      end: const Color(0xFFE0E0E0),
    ).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _colorTween,
      builder: (context, child) {
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            color: _colorTween.value,
            borderRadius: BorderRadius.circular(widget.borderRadius),
          ),
        );
      },
    );
  }
}