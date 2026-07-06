import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import 'dish_manage_page.dart';
import 'feedback_manage_page.dart';
import 'order_manage_page.dart';

const _warmOrange = Color(0xFFF28C28);
const _softOrange = Color(0xFFFFF4E8);
const _lineColor = Color(0xFFF0E7DE);
const _textColor = Color(0xFF242424);
const _mutedColor = Color(0xFF77716B);

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  ShopInfo? _shop;
  List<OrderSummary> _orders = const [];
  Timer? _refreshTimer;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
    _refreshTimer = Timer.periodic(const Duration(seconds: 8), (_) {
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
      final results = await Future.wait([
        shopApi.fetchMyShop(),
        shopApi.fetchOrders(pageSize: 100),
      ]);
      if (!mounted) {
        return;
      }
      setState(() {
        _shop = results[0] as ShopInfo;
        _orders = (results[1] as PageResult<OrderSummary>).records;
        _error = null;
      });
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
      backgroundColor: Colors.white,
      body: SafeArea(
        child: RefreshIndicator(
          color: _warmOrange,
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 28),
            children: [
              if (_loading)
                const LinearProgressIndicator(color: _warmOrange)
              else if (_error != null)
                _ErrorBanner(message: _error!, onRetry: _load),
              _ShopHeader(shop: _shop),
              const SizedBox(height: 20),
              _MetricGrid(orders: _orders),
              const SizedBox(height: 24),
              const _SectionTitle(title: '快捷入口'),
              const SizedBox(height: 12),
              const _ShortcutGrid(),
            ],
          ),
        ),
      ),
    );
  }
}

class _ShopHeader extends StatelessWidget {
  const _ShopHeader({this.shop});

  final ShopInfo? shop;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _softOrange,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _lineColor),
      ),
      child: Row(
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.storefront, color: _warmOrange, size: 30),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  shop?.name.isNotEmpty == true ? shop!.name : '商家工作台',
                  style: const TextStyle(
                    color: _textColor,
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                _OpenStatusBadge(isOpen: shop?.isOpen ?? true),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _OpenStatusBadge extends StatelessWidget {
  const _OpenStatusBadge({required this.isOpen});

  final bool isOpen;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            color: isOpen ? const Color(0xFF20A464) : _mutedColor,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 6),
        Text(
          isOpen ? '营业中' : '休息中',
          style: TextStyle(
            color: isOpen ? const Color(0xFF20A464) : _mutedColor,
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _MetricGrid extends StatelessWidget {
  const _MetricGrid({required this.orders});

  final List<OrderSummary> orders;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final isWide = constraints.maxWidth >= 680;
        final itemWidth = isWide
            ? (constraints.maxWidth - 24) / 3
            : constraints.maxWidth;

        final today = DateTime.now().toIso8601String().substring(0, 10);
        final todayOrders = orders
            .where((order) => order.createdAt.startsWith(today))
            .toList();
        final todayIncome = todayOrders.fold<double>(
          0,
          (sum, order) => sum + order.totalAmount,
        );
        final pending = orders
            .where((order) => order.status == 'SUBMITTED')
            .length;

        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            SizedBox(
              width: itemWidth,
              child: _MetricCard(
                title: '今日订单',
                value: '${todayOrders.length}',
                unit: '单',
                icon: Icons.receipt_long_outlined,
              ),
            ),
            SizedBox(
              width: itemWidth,
              child: _MetricCard(
                title: '今日收入',
                value: todayIncome.toStringAsFixed(0),
                unit: '元',
                icon: Icons.payments_outlined,
              ),
            ),
            SizedBox(
              width: itemWidth,
              child: _MetricCard(
                title: '待处理',
                value: '$pending',
                unit: '项',
                icon: Icons.pending_actions_outlined,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: const Color(0xFFFFF4E8),
        borderRadius: BorderRadius.circular(8),
        child: ListTile(
          leading: const Icon(Icons.info_outline, color: _warmOrange),
          title: Text(message, style: const TextStyle(color: _textColor)),
          trailing: TextButton(onPressed: onRetry, child: const Text('重试')),
        ),
      ),
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    required this.title,
    required this.value,
    required this.unit,
    required this.icon,
  });

  final String title;
  final String value;
  final String unit;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _lineColor),
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: _softOrange,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(icon, color: _warmOrange, size: 24),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(color: _mutedColor, fontSize: 13),
                ),
                const SizedBox(height: 6),
                RichText(
                  text: TextSpan(
                    style: const TextStyle(color: _textColor),
                    children: [
                      TextSpan(
                        text: value,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      TextSpan(
                        text: ' $unit',
                        style: const TextStyle(
                          color: _mutedColor,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
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

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Text(
      title,
      style: const TextStyle(
        color: _textColor,
        fontSize: 18,
        fontWeight: FontWeight.w800,
      ),
    );
  }
}

class _ShortcutGrid extends StatelessWidget {
  const _ShortcutGrid();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final itemWidth = constraints.maxWidth >= 680
            ? (constraints.maxWidth - 12) / 2
            : constraints.maxWidth;

        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            SizedBox(
              width: itemWidth,
              child: _ShortcutCard(
                icon: Icons.restaurant_menu_outlined,
                title: '菜品管理',
                description: '上架新品、调整价格与售卖状态',
              ),
            ),
            SizedBox(
              width: itemWidth,
              child: _ShortcutCard(
                icon: Icons.receipt_long_outlined,
                title: '订单处理',
                description: '查看新订单、接单与更新制作进度',
              ),
            ),
            SizedBox(
              width: itemWidth,
              child: _ShortcutCard(
                icon: Icons.campaign_outlined,
                title: '门店公告',
                description: '发布营业提醒、优惠和临时通知',
              ),
            ),
            SizedBox(
              width: itemWidth,
              child: _ShortcutCard(
                icon: Icons.forum_outlined,
                title: '顾客反馈',
                description: '查看评价与待回复消息',
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ShortcutCard extends StatelessWidget {
  const _ShortcutCard({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  VoidCallback _tapAction(BuildContext context) {
    if (icon == Icons.restaurant_menu_outlined) {
      return () {
        Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => const DishManagePage()));
      };
    }
    if (icon == Icons.receipt_long_outlined) {
      return () {
        Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => const OrderManagePage()));
      };
    }
    if (icon == Icons.forum_outlined) {
      return () {
        Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => const FeedbackManagePage()));
      };
    }
    return () {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('请在店铺页面维护公告信息'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    };
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: _lineColor),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: _tapAction(context),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: _softOrange,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, color: _warmOrange, size: 24),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        color: _textColor,
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      description,
                      style: const TextStyle(
                        color: _mutedColor,
                        fontSize: 13,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: _mutedColor),
            ],
          ),
        ),
      ),
    );
  }
}
