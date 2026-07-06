import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import 'shop_order_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late Future<PageResult<Shop>> _shopsFuture;
  late Future<List<FamilyInfo>> _familiesFuture;
  String? _selectedFamilyCode;

  @override
  void initState() {
    super.initState();
    _shopsFuture = widget.api.shops();
    _familiesFuture = Future.value(const []);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final loggedIn = AppScope.of(context).auth.isLoggedIn;
    _familiesFuture = loggedIn
        ? widget.api.myFamilies()
        : Future.value(const []);
  }

  void _reload() {
    setState(() {
      _shopsFuture = widget.api.shops(familyCode: _selectedFamilyCode);
      _familiesFuture = AppScope.of(context).auth.isLoggedIn
          ? widget.api.myFamilies()
          : Future.value(const []);
    });
  }

  void _selectFamily(String? familyCode) {
    setState(() {
      _selectedFamilyCode = familyCode;
      _shopsFuture = widget.api.shops(familyCode: familyCode);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: warmOrange,
        backgroundColor: Colors.white,
        child: CustomScrollView(
          slivers: [
            // 顶部欢迎语区域 - 白色卡片包裹
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.all(Radius.circular(12)),
                  ),
                  child: Padding(
                    padding: EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '今天想吃点什么？',
                          style: TextStyle(
                            color: pageTextColor,
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                            height: 1.2,
                          ),
                        ),
                        SizedBox(height: 8),
                        Row(
                          children: [
                            Icon(
                              Icons.location_on_rounded,
                              size: 16,
                              color: warmOrange,
                            ),
                            SizedBox(width: 4),
                            Text(
                              '本地商家 · 即刻点餐',
                              style: TextStyle(color: pageMutedColor, fontSize: 13),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),

            // 滑动吸顶 Header - 家庭分类筛选
            SliverPersistentHeader(
              pinned: true,
              delegate: _StickyHeaderDelegate(
                child: Container(
                  color: const Color(0xFFF7F7F7),
                  child: FutureBuilder<List<FamilyInfo>>(
                    future: _familiesFuture,
                    builder: (context, snapshot) {
                      final families = (snapshot.data ?? const [])
                          .where((family) => family.isApproved)
                          .toList();
                      return ListView(
                        scrollDirection: Axis.horizontal,
                        padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                        children: [
                          _FamilyFilterChip(
                            selected: _selectedFamilyCode == null,
                            title: '全部商家',
                            onTap: () => _selectFamily(null),
                          ),
                          for (final family in families)
                            _FamilyFilterChip(
                              selected: _selectedFamilyCode == family.familyCode,
                              title: family.familyName,
                              subtitle: family.familyCode,
                              onTap: () => _selectFamily(family.familyCode),
                            ),
                        ],
                      );
                    },
                  ),
                ),
              ),
            ),

            // 商家列表区
            FutureBuilder<PageResult<Shop>>(
              future: _shopsFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const SliverFillRemaining(
                    child: Center(child: CircularProgressIndicator(color: warmOrange)),
                  );
                }
                if (snapshot.hasError) {
                  return SliverFillRemaining(
                    child: _StateMessage(
                      icon: Icons.wifi_off_outlined,
                      title: '商家加载失败',
                      message: snapshot.error.toString(),
                      actionText: '重试',
                      onPressed: _reload,
                    ),
                  );
                }
                final shops = snapshot.data?.records ?? const [];
                if (shops.isEmpty) {
                  return SliverFillRemaining(
                    child: _StateMessage(
                      icon: Icons.storefront_outlined,
                      title: '暂无商家',
                      message: '稍后刷新试试',
                      actionText: '刷新',
                      onPressed: _reload,
                    ),
                  );
                }
                return SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  sliver: SliverList.separated(
                    itemBuilder: (context, index) {
                      return _RestaurantCard(shop: shops[index], index: index);
                    },
                    separatorBuilder: (context, index) => const SizedBox(height: 12),
                    itemCount: shops.length,
                  ),
                );
              },
            ),
            const SliverToBoxAdapter(child: AppFooter()),
          ],
        ),
      ),
    );
  }
}

// ================= 组件 =================

class _RestaurantCard extends StatelessWidget {
  const _RestaurantCard({required this.shop, required this.index});

  final Shop shop;
  final int index;

  @override
  Widget build(BuildContext context) {
    final color = _shopColors[index % _shopColors.length];
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ShopOrderPage(shopId: shop.id)),
        );
      },
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _ShopAvatar(color: color, imageUrl: shop.coverImageUrl),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          shop.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: pageTextColor,
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      _StatusBadge(text: _businessStatusText(shop)),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    shop.description?.isNotEmpty == true
                        ? shop.description!
                        : '欢迎下单，商家正在准备好味道',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Color(0xFF999999), fontSize: 12),
                  ),
                  // 原有底部地址/电话标签已删除
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ShopAvatar extends StatelessWidget {
  const _ShopAvatar({required this.color, this.imageUrl});

  final Color color;
  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 72,
      height: 72,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: color.withOpacity(0.2),
        borderRadius: BorderRadius.circular(8),
      ),
      child: imageUrl?.isNotEmpty == true
          ? Image.network(
        resolveCustomerImageUrl(imageUrl!),
        fit: BoxFit.cover,
        loadingBuilder: (context, child, progress) => progress == null
            ? child
            : const Center(
          child: CircularProgressIndicator(color: warmOrange, strokeWidth: 2),
        ),
        errorBuilder: (_, _, _) => Icon(Icons.storefront, color: color, size: 32),
      )
          : Icon(Icons.storefront, color: color, size: 32),
    );
  }
}

class _FamilyFilterChip extends StatelessWidget {
  const _FamilyFilterChip({
    super.key,
    required this.selected,
    required this.title,
    this.subtitle,
    required this.onTap,
  });

  final bool selected;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 10),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(minWidth: 80),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: selected ? warmOrange : Colors.white,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                title,
                maxLines: 1,
                style: TextStyle(
                  color: selected ? Colors.white : pageTextColor,
                  fontSize: 14,
                  fontWeight: selected ? FontWeight.bold : FontWeight.w600,
                ),
              ),
              if (subtitle != null && subtitle!.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  subtitle!,
                  maxLines: 1,
                  style: TextStyle(
                    color: selected ? Colors.white70 : const Color(0xFF999999),
                    fontSize: 10,
                  ),
                ),
              ],
            ],
          ),
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
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.circle, color: warmOrange, size: 8),
        const SizedBox(width: 4),
        Text(
          text,
          style: const TextStyle(
            color: pageTextColor,
            fontSize: 12,
            fontWeight: FontWeight.bold,
          ),
        ),
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
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: Colors.grey[400], size: 56),
          const SizedBox(height: 16),
          Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: pageTextColor)),
          const SizedBox(height: 8),
          Text(message, textAlign: TextAlign.center, style: const TextStyle(color: pageMutedColor, fontSize: 13)),
          const SizedBox(height: 24),
          FilledButton.tonal(onPressed: onPressed, child: Text(actionText)),
        ],
      ),
    );
  }
}

// 辅助工具类：Sliver 吸顶委托
class _StickyHeaderDelegate extends SliverPersistentHeaderDelegate {
  final Widget child;
  _StickyHeaderDelegate({required this.child});

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) {
    return SizedBox.expand(child: child);
  }

  @override
  double get maxExtent => 74.0;

  @override
  double get minExtent => 74.0;

  @override
  bool shouldRebuild(covariant _StickyHeaderDelegate oldDelegate) {
    return oldDelegate.child != child;
  }
}

String _businessStatusText(Shop shop) {
  return shop.businessStatus == 'CLOSED' ? '休息中' : '营业中';
}

const _shopColors = [
  Color(0xFFFFD9A8),
  Color(0xFFFFB7A1),
  Color(0xFFCFE8C9),
  Color(0xFFFFC66E),
];