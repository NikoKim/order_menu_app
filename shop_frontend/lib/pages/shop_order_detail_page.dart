import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../widgets/app_notice.dart';

const _warmOrange = Color(0xFFF28C28);
const _softOrange = Color(0xFFFFF4E8);
const _pageBgColor = Color(0xFFF5F6F8);
const _lineColor = Color(0xFFF0E7DE);
const _textColor = Color(0xFF242424);
const _mutedColor = Color(0xFF77716B);

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
  StreamSubscription? _wsSubscription;

  @override
  void initState() {
    super.initState();

    _load();

    _wsSubscription = ShopWebSocketService.instance
        .streamForModule('ORDER')
        .listen((msg) {
          if (!mounted) return;

          final orderId = int.tryParse('${msg.data['orderId']}');
          if (orderId == widget.orderId) {
            _load(silent: true);
          }
        });
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    super.dispose();
  }

  Future _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }

    try {
      final order = await shopApi.fetchOrderDetail(widget.orderId);

      if (mounted) {
        setState(() {
          _order = order;
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
    return Scaffold(
      backgroundColor: _pageBgColor,
      appBar: AppBar(
        title: const Text(
          '订单详情',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
      ),
      body: RefreshIndicator(
        color: _warmOrange,
        backgroundColor: Colors.white,
        onRefresh: _load,
        child: _buildBody(_order),
      ),
      bottomNavigationBar: _buildBottomBar(),
    );
  }

  Widget _buildBody(OrderDetail? order) {
    if (_loading) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSkeletonCard(height: 140),
          const SizedBox(height: 12),
          _buildSkeletonCard(height: 220),
          const SizedBox(height: 12),
          _buildSkeletonCard(height: 100),
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
          Center(
            child: Column(
              children: const [
                Icon(Icons.search_off, size: 54, color: Color(0xFFE0E0E0)),
                SizedBox(height: 16),
                Text('订单不存在或已被删除', style: TextStyle(color: _mutedColor)),
              ],
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 卡片 1：订单基础信息与顾客信息
        _BaseCard(
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
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  _StatusBadge(status: order.status),
                ],
              ),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 14),
                child: Divider(color: _lineColor, height: 1),
              ),
              _CustomerPanel(order: order),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 14),
                child: Divider(color: _lineColor, height: 1),
              ),
              _DetailRow(label: '下单时间', value: _formatTime(order.createdAt)),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // 卡片 2：菜品清单（图文升级版）
        _BaseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '菜品明细',
                style: TextStyle(
                  color: _textColor,
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 16),
              for (final item in order.items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _OrderItemImage(imageUrl: item.dishImageUrl),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              item.dishName,
                              style: const TextStyle(
                                color: _textColor,
                                fontSize: 15,
                                fontWeight: FontWeight.w600,
                                height: 1.3,
                              ),
                            ),
                            if (item.selectedOptions?.isNotEmpty == true) ...[
                              const SizedBox(height: 4),
                              Text(
                                item.selectedOptions!,
                                style: const TextStyle(
                                  color: _mutedColor,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                            const SizedBox(height: 4),
                            Text(
                              'x${item.quantity}',
                              style: const TextStyle(
                                color: _mutedColor,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        '¥${item.subtotalAmount.toStringAsFixed(2)}',
                        style: const TextStyle(
                          color: _textColor,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // 卡片 3：备注与金额合计
        _BaseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _DetailRow(
                label: '订单备注',
                value: order.remark == null || order.remark!.isEmpty
                    ? '无'
                    : order.remark!,
              ),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 14),
                child: Divider(color: _lineColor, height: 1),
              ),
              Row(
                children: [
                  const Text(
                    '实付金额',
                    style: TextStyle(
                      color: _textColor,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    '¥${order.totalAmount.toStringAsFixed(2)}',
                    style: const TextStyle(
                      color: _warmOrange,
                      fontSize: 24,
                      fontWeight: FontWeight.w900,
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

  Widget? _buildBottomBar() {
    final order = _order;
    if (order == null ||
        (order.status != 'SUBMITTED' && order.status != 'ACCEPTED')) {
      return null;
    }

    return SafeArea(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 12,
              offset: const Offset(0, -4),
            ),
          ],
        ),
        child: Row(
          children: [
            if (order.status == 'SUBMITTED') ...[
              Expanded(
                flex: 1,
                child: OutlinedButton(
                  onPressed: _submitting ? null : _rejectOrder,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.redAccent,
                    side: const BorderSide(color: Colors.redAccent),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text(
                    '不接单',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ),
              ),
              const SizedBox(width: 12),
            ],
            Expanded(
              flex: 2,
              child: FilledButton(
                onPressed: _submitting ? null : _handleAction,
                style: FilledButton.styleFrom(
                  backgroundColor: _warmOrange,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: Text(
                  _submitting
                      ? '处理中...'
                      : (order.status == 'SUBMITTED' ? '接单' : '制作完成'),
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleAction() async {
    final order = _order;
    if (order == null) return;

    setState(() => _submitting = true);
    try {
      if (order.status == 'SUBMITTED') {
        await shopApi.acceptOrder(order.id);
        _showSnackBar('已接单');
      } else if (order.status == 'ACCEPTED') {
        await shopApi.completeOrder(order.id);
        _showSnackBar('订单已完成');
      }

      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _rejectOrder() async {
    final order = _order;
    if (order == null) return;

    setState(() => _submitting = true);
    try {
      await shopApi.rejectOrder(order.id);
      _showSnackBar('已拒绝接单');
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showSnackBar(String message) {
    AppNotice.show(context, message);
  }

  Widget _buildSkeletonCard({required double height}) {
    return Container(
      height: height,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: const _SkeletonBox(
        width: double.infinity,
        height: double.infinity,
      ),
    );
  }
}

// --------------------------------------------------------------------------
// 视图组件抽取
// --------------------------------------------------------------------------

class _BaseCard extends StatelessWidget {
  const _BaseCard({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.02),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
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
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 64,
          child: Text(
            label,
            style: const TextStyle(color: _mutedColor, fontSize: 14),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              color: _textColor,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
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
          width: 52,
          height: 52,
          clipBehavior: Clip.antiAlias,
          decoration: const BoxDecoration(
            color: _softOrange,
            shape: BoxShape.circle,
          ),
          child: avatarUrl?.isNotEmpty == true
              ? Image.network(
                  _absoluteImageUrl(avatarUrl!),
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) =>
                      const Icon(Icons.person, color: _warmOrange),
                )
              : const Icon(Icons.person, color: _warmOrange),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(
                    order.customerNickname?.isNotEmpty == true
                        ? order.customerNickname!
                        : '顾客 #${order.customerUserId}',
                    style: const TextStyle(
                      color: _textColor,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  if (order.familyCustomer) ...[
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: _softOrange,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        '家庭成员',
                        style: TextStyle(
                          color: _warmOrange,
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  const Icon(
                    Icons.phone_outlined,
                    size: 14,
                    color: _mutedColor,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    order.customerPhone?.isNotEmpty == true
                        ? order.customerPhone!
                        : '暂无电话信息',
                    style: const TextStyle(color: _mutedColor, fontSize: 13),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _OrderItemImage extends StatelessWidget {
  const _OrderItemImage({this.imageUrl});
  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: _pageBgColor,
        borderRadius: BorderRadius.circular(8),
      ),
      clipBehavior: Clip.antiAlias,
      child: imageUrl?.isNotEmpty == true
          ? Image.network(
              _absoluteImageUrl(imageUrl!),
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => const Icon(
                Icons.restaurant_menu,
                color: _mutedColor,
                size: 20,
              ),
            )
          : const Icon(Icons.restaurant_menu, color: _mutedColor, size: 20),
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

    switch (status) {
      case 'SUBMITTED':
        bgColor = _softOrange;
        textColor = _warmOrange;
        break;
      case 'ACCEPTED':
        bgColor = const Color(0xFFE3F2FD);
        textColor = const Color(0xFF1976D2);
        break;
      case 'COMPLETED':
        bgColor = const Color(0xFFF5F6F8);
        textColor = const Color(0xFF388E3C);
        break;
      case 'CANCELLED':
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
          Expanded(
            child: Text(message, style: const TextStyle(color: _textColor)),
          ),
          TextButton(
            onPressed: onRetry,
            child: const Text('重试', style: TextStyle(color: _warmOrange)),
          ),
        ],
      ),
    );
  }
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
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  final api = Uri.parse(ApiConfig.baseUrl);
  return api.replace(path: url.startsWith('/') ? url : '/$url').toString();
}

// --------------------------------------------------------------------------
// 骨架屏组件
// --------------------------------------------------------------------------
class _SkeletonBox extends StatefulWidget {
  final double width;
  final double height;
  final double borderRadius;

  const _SkeletonBox({
    required this.width,
    required this.height,
    this.borderRadius = 8,
  });

  @override
  State<_SkeletonBox> createState() => _SkeletonBoxState();
}

class _SkeletonBoxState extends State<_SkeletonBox>
    with SingleTickerProviderStateMixin {
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
