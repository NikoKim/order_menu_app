import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';

class ShopOrderDetailPage extends StatefulWidget {
  const ShopOrderDetailPage({super.key, required this.orderId});

  final int orderId;

  @override
  State<ShopOrderDetailPage> createState() => _ShopOrderDetailPageState();
}

class _ShopOrderDetailPageState extends State<ShopOrderDetailPage> {
  OrderDetail? _order;
  bool _loading = true;
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final order = await shopApi.fetchOrderDetail(widget.orderId);
      if (mounted) {
        setState(() => _order = order);
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = _order;
    final actionLabel = order == null ? null : _actionLabelFor(order.status);

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: const Text('订单详情'),
        backgroundColor: Colors.white,
        foregroundColor: textColor,
        elevation: 0,
      ),
      body: RefreshIndicator(
        color: warmOrange,
        onRefresh: _load,
        child: _buildBody(order),
      ),
      bottomNavigationBar: actionLabel == null
          ? null
          : SafeArea(
              minimum: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: SizedBox(
                height: 48,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: warmOrange,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  onPressed: _submitting ? null : _handleAction,
                  child: Text(_submitting ? '处理中...' : actionLabel),
                ),
              ),
            ),
    );
  }

  Widget _buildBody(OrderDetail? order) {
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
    if (order == null) {
      return ListView(
        children: [
          const SizedBox(height: 220),
          const Center(
            child: Text('订单不存在', style: TextStyle(color: mutedColor)),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _InfoCard(
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
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  _StatusBadge(status: order.status),
                ],
              ),
              const SizedBox(height: 14),
              _CustomerPanel(order: order),
              const SizedBox(height: 12),
              _DetailRow(label: '状态', value: _statusText(order.status)),
              _DetailRow(label: '时间', value: _formatTime(order.createdAt)),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _InfoCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '菜品',
                style: TextStyle(
                  color: textColor,
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 12),
              for (final item in order.items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: warmOrange,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${item.dishName} x${item.quantity}',
                              style: const TextStyle(
                                color: textColor,
                                fontSize: 15,
                              ),
                            ),
                            if (item.selectedOptions?.isNotEmpty == true)
                              Text(
                                item.selectedOptions!,
                                style: const TextStyle(
                                  color: mutedColor,
                                  fontSize: 12,
                                ),
                              ),
                          ],
                        ),
                      ),
                      Text(
                        '¥${item.subtotalAmount.toStringAsFixed(2)}',
                        style: const TextStyle(color: mutedColor),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _InfoCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _DetailRow(
                label: '备注',
                value: order.remark == null || order.remark!.isEmpty
                    ? '无'
                    : order.remark!,
              ),
              const Divider(height: 28, color: lineColor),
              Row(
                children: [
                  const Text(
                    '金额',
                    style: TextStyle(color: mutedColor, fontSize: 14),
                  ),
                  const Spacer(),
                  Text(
                    '¥${order.totalAmount.toStringAsFixed(2)}',
                    style: const TextStyle(
                      color: warmOrange,
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _handleAction() async {
    final order = _order;
    if (order == null) {
      return;
    }
    setState(() => _submitting = true);
    try {
      if (order.status == 'SUBMITTED') {
        await shopApi.acceptOrder(order.id);
      } else if (order.status == 'ACCEPTED') {
        await shopApi.completeOrder(order.id);
      }
      await _load();
      if (mounted) {
        Navigator.pop(context, true);
      }
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
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

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: lineColor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: child,
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 48,
            child: Text(
              label,
              style: const TextStyle(color: mutedColor, fontSize: 14),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: textColor,
                fontSize: 15,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CustomerPanel extends StatelessWidget {
  const _CustomerPanel({required this.order});

  final OrderDetail order;

  @override
  Widget build(BuildContext context) {
    final avatarUrl = order.customerAvatarUrl;
    return Row(
      children: [
        Container(
          width: 54,
          height: 54,
          clipBehavior: Clip.antiAlias,
          decoration: const BoxDecoration(
            color: softOrange,
            shape: BoxShape.circle,
          ),
          child: avatarUrl?.isNotEmpty == true
              ? Image.network(
                  _absoluteImageUrl(avatarUrl!),
                  fit: BoxFit.cover,
                  loadingBuilder: (context, child, progress) => progress == null
                      ? child
                      : const Center(
                          child: CircularProgressIndicator(
                            color: warmOrange,
                            strokeWidth: 2,
                          ),
                        ),
                  errorBuilder: (_, _, _) =>
                      const Icon(Icons.person, color: warmOrange),
                )
              : const Icon(Icons.person, color: warmOrange),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                order.customerNickname?.isNotEmpty == true
                    ? order.customerNickname!
                    : '顾客 #${order.customerUserId}',
                style: const TextStyle(
                  color: textColor,
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              if (order.familyCustomer) ...[
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: softOrange,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Text(
                    '家庭用户',
                    style: TextStyle(
                      color: warmOrange,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(height: 4),
              ],
              Text(
                order.customerPhone?.isNotEmpty == true
                    ? order.customerPhone!
                    : '暂无电话',
                style: const TextStyle(color: mutedColor, fontSize: 13),
              ),
            ],
          ),
        ),
      ],
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

String _statusText(String status) {
  switch (status) {
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

String _absoluteImageUrl(String url) {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  final api = Uri.parse(ApiConfig.baseUrl);
  return api.replace(path: url.startsWith('/') ? url : '/$url').toString();
}
