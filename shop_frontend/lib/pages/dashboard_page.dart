import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import 'dish_manage_page.dart';
import 'feedback_manage_page.dart';
import 'order_manage_page.dart';
import 'showcase_manage_page.dart';
import 'shop_family_page.dart';

const _warmOrange = Color(0xFFF28C28);
const _softOrange = Color(0xFFFFF4E8);
const _pageBgColor = Color(0xFFF5F6F8);
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
  int _totalOnSaleDishes = 0;
  ShopNotificationInfo? _notifications;
  int _pendingFeedbackCount = 0; // 新增：保存留言数
  StreamSubscription? _wsSubscription;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
    _wsSubscription = ShopWebSocketService.instance.messageStream.listen((msg) {
      if (!mounted) return;

      final shouldRefresh = switch (msg.module) {
        'ORDER' => {'CREATED', 'UPDATED', 'ACCEPTED', 'REJECTED', 'COMPLETED'}.contains(msg.action),
        'FEEDBACK' => {'CREATED', 'UPDATED'}.contains(msg.action),
        'FAMILY' => {'MEMBER_APPLIED', 'MEMBER_APPROVED', 'MEMBER_REJECTED'}.contains(msg.action),
        'NOTIFICATION' => true,
        'SHOP' => true,
        _ => false,
      };

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
      final results = await Future.wait([
        shopApi.fetchMyShop(),
        shopApi.fetchOrders(pageSize: 100),
        shopApi.fetchDishes(status: 'ON_SALE', pageSize: 1),
        shopApi.fetchNotifications(),
        // 主动获取未处理留言
        shopApi.fetchFeedback(status: 'PENDING', pageSize: 1),
      ]);
      if (!mounted) return;

      setState(() {
        _shop = results[0] as ShopInfo;
        _orders = (results[1] as PageResult<OrderSummary>).records;
        _totalOnSaleDishes = (results[2] as PageResult<DishInfo>).total;
        _notifications = results[3] as ShopNotificationInfo;
        _pendingFeedbackCount = (results[4] as PageResult<FeedbackInfo>).total;
        _error = null;
      });
    } on ApiException catch (error) {
      if (mounted && !silent) setState(() => _error = error.message);
    } finally {
      if (mounted && !silent) setState(() => _loading = false);
    }
  }

  Future<void> _changeBusinessStatus(String businessStatus) async {
    final current = _shop;

    if (current == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('店铺信息加载中，请稍后再试')),
        );
      }
      return;
    }

    try {
      final updated = await shopApi.updateMyShop(
        name: current.name,
        description: current.description,
        coverImageUrl: current.coverImageUrl,
        address: current.address,
        phone: current.phone,
        visibilityType: current.visibilityType ?? 'PUBLIC',
        familyName: current.familyName,
        familyJoinMode: current.familyJoinMode ?? 'OPEN',
        familyImageUrl: current.familyImageUrl,
        businessStatus: businessStatus,
      );

      if (!mounted) return;

      setState(() {
        _shop = updated;
        _error = null;
      });

      await _load(silent: true);
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
      rethrow;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _pageBgColor,
      body: SafeArea(
        child: RefreshIndicator(
          color: _warmOrange,
          backgroundColor: Colors.white,
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              if (_loading && _shop == null)
                const Center(
                  child: Padding(
                    padding: EdgeInsets.all(32),
                    child: CircularProgressIndicator(color: _warmOrange),
                  ),
                )
              else ...[
                if (_error != null)
                  _ErrorBanner(message: _error!, onRetry: _load),
                _ShopHeader(shop: _shop),
                const SizedBox(height: 14),
                _PendingAlertBanner(orders: _orders),
                _DataDashboardCard(orders: _orders),
                const SizedBox(height: 20),
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                  child: Text(
                    '快捷应用',
                    style: TextStyle(color: _textColor, fontSize: 17, fontWeight: FontWeight.w800),
                  ),
                ),
                _ShortcutGrid(
                  shop: _shop,
                  notifications: _notifications,
                  pendingFeedbackCount: _pendingFeedbackCount,
                  onBusinessStatusChanged: _changeBusinessStatus,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _BaseCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;

  const _BaseCard({required this.child, this.padding});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding ?? const EdgeInsets.all(18),
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
      child: child,
    );
  }
}

class _ShopHeader extends StatelessWidget {
  const _ShopHeader({this.shop});
  final ShopInfo? shop;

  @override
  Widget build(BuildContext context) {
    return _BaseCard(
      child: Row(
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: _softOrange,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.storefront, color: _warmOrange, size: 28),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  shop?.name.isNotEmpty == true ? shop!.name : '商家工作台',
                  style: const TextStyle(color: _textColor, fontSize: 20, fontWeight: FontWeight.w800),
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

class _PendingAlertBanner extends StatelessWidget {
  const _PendingAlertBanner({required this.orders});
  final List<OrderSummary> orders;

  @override
  Widget build(BuildContext context) {
    final pendingCount = orders.where((o) => o.status == 'SUBMITTED').length;
    if (pendingCount == 0) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: _softOrange,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: _warmOrange.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.notifications_active_outlined, color: _warmOrange),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '您有 $pendingCount 笔新订单待处理',
                style: const TextStyle(color: _warmOrange, fontWeight: FontWeight.w700, fontSize: 14),
              ),
            ),
            FilledButton(
              onPressed: () {
                Navigator.of(context).push(MaterialPageRoute(builder: (_) => const OrderManagePage()));
              },
              style: FilledButton.styleFrom(
                backgroundColor: _warmOrange,
                minimumSize: const Size(72, 34),
                padding: const EdgeInsets.symmetric(horizontal: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('立即处理', style: TextStyle(fontSize: 13)),
            )
          ],
        ),
      ),
    );
  }
}

class _DataDashboardCard extends StatelessWidget {
  const _DataDashboardCard({required this.orders});
  final List<OrderSummary> orders;

  @override
  Widget build(BuildContext context) {
    final today = DateTime.now().toIso8601String().substring(0, 10);
    final todayOrders = orders.where((o) => o.createdAt.startsWith(today)).toList();
    final todayIncome = todayOrders.fold<double>(0, (sum, o) => sum + o.totalAmount);
    final pendingCount = orders.where((o) => o.status == 'SUBMITTED').length;

    return _BaseCard(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('今日实收金额 (元)', style: TextStyle(color: _mutedColor, fontSize: 13, fontWeight: FontWeight.w600)),
              Text(today, style: const TextStyle(color: _mutedColor, fontSize: 12)),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            todayIncome.toStringAsFixed(2),
            style: const TextStyle(color: _textColor, fontSize: 32, fontWeight: FontWeight.w900),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Divider(color: Color(0xFFF0E7DE), height: 1),
          ),
          IntrinsicHeight(
            child: Row(
              children: [
                _buildSubMetric('今日成单', '${todayOrders.length}'),
                const VerticalDivider(color: Color(0xFFF0E7DE), width: 32),
                _buildSubMetric('待接单', '$pendingCount', highlight: pendingCount > 0),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSubMetric(String label, String value, {bool highlight = false}) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: _mutedColor, fontSize: 13)),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              color: highlight ? Colors.redAccent : _textColor,
              fontSize: 20,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _ShortcutGrid extends StatelessWidget {
  final ShopInfo? shop;
  final ShopNotificationInfo? notifications;
  final int pendingFeedbackCount;
  final Future<void> Function(String businessStatus) onBusinessStatusChanged;

  const _ShortcutGrid({
    super.key,
    this.shop,
    this.notifications,
    this.pendingFeedbackCount = 0,
    required this.onBusinessStatusChanged,
  });

  @override
  Widget build(BuildContext context) {
    return _BaseCard(
      padding: const EdgeInsets.all(20),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final itemWidth = (constraints.maxWidth - 20) / 2;

          return Wrap(
            spacing: 20,
            runSpacing: 20,
            children: [
              SizedBox(
                width: itemWidth,
                child: const _GridShortcutItem(
                  icon: Icons.restaurant_menu_outlined,
                  title: '菜品管理',
                  targetPage: DishManagePage(),
                ),
              ),
              SizedBox(
                width: itemWidth,
                child: _GridShortcutItem(
                  icon: Icons.receipt_long_outlined,
                  title: '订单处理',
                  targetPage: const OrderManagePage(),
                  badgeCount: notifications?.pendingOrders ?? 0,
                ),
              ),
              SizedBox(
                width: itemWidth,
                child: const _GridShortcutItem(
                  icon: Icons.view_carousel_outlined,
                  title: '首页展示',
                  targetPage: ShowcaseManagePage(),
                ),
              ),
              SizedBox(
                width: itemWidth,
                child: _GridShortcutItem(
                  icon: Icons.family_restroom,
                  title: '家庭管理',
                  targetPage: const ShopFamilyPage(),
                  badgeCount: notifications?.pendingFamilyMembers ?? 0,
                ),
              ),
              SizedBox(
                width: itemWidth,
                child: _GridShortcutItem(
                  icon: Icons.schedule_outlined,
                  title: '营业状态',
                  onTap: () {
                    if (shop != null) {
                      _showStatusToggleDialog(
                        context,
                        shop!,
                        onBusinessStatusChanged,
                      );
                    } else {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('店铺信息加载中，请稍后再试')),
                      );
                    }
                  },
                ),
              ),
              SizedBox(
                width: itemWidth,
                child: _GridShortcutItem(
                  icon: Icons.forum_outlined,
                  title: '顾客反馈',
                  targetPage: const FeedbackManagePage(),
                  badgeCount: pendingFeedbackCount,
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _GridShortcutItem extends StatelessWidget {
  const _GridShortcutItem({
    required this.icon,
    required this.title,
    this.targetPage,
    this.onTap,
    this.badgeCount = 0,
  });

  final IconData icon;
  final String title;
  final Widget? targetPage;
  final VoidCallback? onTap;
  final int badgeCount;

  @override
  Widget build(BuildContext context) {
    Widget iconWidget = Icon(icon, color: const Color(0xFFF28C28), size: 22);
    // 展示数量红点，颜色固定为与订单相同的红
    if (badgeCount > 0) {
      iconWidget = Badge(
        label: Text(badgeCount > 9 ? '...' : '$badgeCount'),
        offset: const Offset(8, -6),
        backgroundColor: Colors.redAccent,
        child: iconWidget,
      );
    }

    return InkWell(
      onTap: () {
        if (onTap != null) {
          onTap!();
        } else if (targetPage != null) {
          Navigator.of(context).push(MaterialPageRoute(builder: (_) => targetPage!));
        }
      },
      borderRadius: BorderRadius.circular(8),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: const Color(0xFFFFF8F0),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Center(child: iconWidget),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(color: Color(0xFF242424), fontSize: 14, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
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
        borderRadius: BorderRadius.circular(12),
        child: ListTile(
          leading: const Icon(Icons.info_outline, color: _warmOrange),
          title: Text(message, style: const TextStyle(color: _textColor)),
          trailing: TextButton(onPressed: onRetry, child: const Text('重试')),
        ),
      ),
    );
  }
}

void _showStatusToggleDialog(
    BuildContext context,
    ShopInfo shop,
    Future<void> Function(String businessStatus) onBusinessStatusChanged,
    ) {
  showModalBottomSheet(
    context: context,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
    builder: (context) {
      bool isSubmitting = false;
      return StatefulBuilder(
        builder: (context, setModalState) {
          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text('设置营业状态', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: _textColor)),
                    ),
                  ),
                  const SizedBox(height: 8),
                  ListTile(
                    leading: const Icon(Icons.check_circle, color: Color(0xFF20A464)),
                    title: const Text('正常营业', style: TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: const Text('顾客可正常浏览菜单并下单', style: TextStyle(fontSize: 13, color: _mutedColor)),
                    trailing: shop.isOpen
                        ? const Icon(Icons.radio_button_checked, color: _warmOrange)
                        : const Icon(Icons.radio_button_off, color: Color(0xFFE0E0E0)),
                    onTap: shop.isOpen || isSubmitting
                        ? null
                        : () async {
                      setModalState(() => isSubmitting = true);

                      try {
                        await onBusinessStatusChanged('OPEN');

                        if (context.mounted) {
                          Navigator.pop(context);
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('店铺已切换为营业中')),
                          );
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(e.toString())),
                          );
                        }

                        setModalState(() => isSubmitting = false);
                      }
                    },
                  ),
                  ListTile(
                    leading: const Icon(Icons.remove_circle, color: _mutedColor),
                    title: const Text('店铺休息', style: TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: const Text('店铺将显示休息中，顾客无法下单', style: TextStyle(fontSize: 13, color: _mutedColor)),
                    trailing: !shop.isOpen
                        ? const Icon(Icons.radio_button_checked, color: _warmOrange)
                        : const Icon(Icons.radio_button_off, color: Color(0xFFE0E0E0)),
                    onTap: !shop.isOpen || isSubmitting
                        ? null
                        : () async {
                      setModalState(() => isSubmitting = true);

                      try {
                        await onBusinessStatusChanged('CLOSED');

                        if (context.mounted) {
                          Navigator.pop(context);
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('店铺已切换为休息中')),
                          );
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(e.toString())),
                          );
                        }

                        setModalState(() => isSubmitting = false);
                      }
                    },
                  ),
                ],
              ),
            ),
          );
        },
      );
    },
  );
}