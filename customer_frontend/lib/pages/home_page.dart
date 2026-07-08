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

  // 新增：追踪登录状态，避免不必要的重新请求
  bool? _lastLoggedIn;

  @override
  void initState() {
    super.initState();
    // 赋予初始空占位，真实数据会在 didChangeDependencies 中进行加载
    _shopsFuture = Future.value(const PageResult(records: [], page: 1, pageSize: 0, total: 0));
    _familiesFuture = Future.value(const []);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final loggedIn = AppScope.of(context).auth.isLoggedIn;

    // 仅在首次进入或登录状态发生改变时触发拉取
    if (_lastLoggedIn != loggedIn) {
      _lastLoggedIn = loggedIn;
      _familiesFuture = loggedIn ? widget.api.myFamilies() : Future.value(const []);
      _shopsFuture = _fetchShops(_selectedFamilyCode, loggedIn);
    }
  }

  void _reload() {
    final loggedIn = AppScope.of(context).auth.isLoggedIn;
    setState(() {
      _familiesFuture = loggedIn ? widget.api.myFamilies() : Future.value(const []);
      _shopsFuture = _fetchShops(_selectedFamilyCode, loggedIn);
    });
  }

  void _selectFamily(String? familyCode) {
    final loggedIn = AppScope.of(context).auth.isLoggedIn;
    setState(() {
      _selectedFamilyCode = familyCode;
      _shopsFuture = _fetchShops(familyCode, loggedIn);
    });
  }

  // 智能合并商家拉取逻辑
  Future<PageResult<Shop>> _fetchShops(String? familyCode, bool loggedIn) async {
    // 如果明确选中了某个家庭分类，直接请求该家庭的商家
    if (familyCode != null) {
      return widget.api.shops(familyCode: familyCode);
    }

    // 如果是“全部商家”，先拉取基础的公开商家
    final publicPage = await widget.api.shops();
    final shops = List<Shop>.from(publicPage.records);
    final seenIds = shops.map((s) => s.id).toSet();

    // 在登录状态下，把已加入家庭的商家也一并拉取并合并进来
    if (loggedIn) {
      try {
        final families = await widget.api.myFamilies();
        final approvedFamilies = families.where((f) => f.isApproved).toList();

        // 使用 Future.wait 并发请求所有家庭的商家列表，提升加载速度
        final futures = approvedFamilies.map((f) => widget.api.shops(familyCode: f.familyCode));
        final results = await Future.wait(futures);

        for (final res in results) {
          for (final shop in res.records) {
            // 根据 ID 去重（防止有些家庭商家本身也是公开商家）
            if (!seenIds.contains(shop.id)) {
              shops.add(shop);
              seenIds.add(shop.id);
            }
          }
        }
      } catch (e) {
        debugPrint('合并家庭商家失败: $e');
      }
    }

    return PageResult<Shop>(
      records: shops,
      page: 1,
      pageSize: shops.length,
      total: shops.length,
    );
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
                  return const _HomeShopsSkeleton();
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
                      return _RestaurantCard(
                        shop: shops[index],
                        index: index,
                        // 传入判断条件：只有在未选择具体家庭（即“全部商家”）时，才显示家庭标签
                        showFamilyLabel: _selectedFamilyCode == null,
                      );
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
  const _RestaurantCard({
    required this.shop,
    required this.index,
    required this.showFamilyLabel,
  });

  final Shop shop;
  final int index;
  final bool showFamilyLabel;

  @override
  Widget build(BuildContext context) {
    final color = _shopColors[index % _shopColors.length];
    final previewDishes = shop.previewDishes ?? [];

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
        // 改用 Column 包裹，为了在底部放下菜品列表
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
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
                      if (showFamilyLabel && shop.familyName?.isNotEmpty == true)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 6),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFF4E8),
                              borderRadius: BorderRadius.circular(4),
                              border: Border.all(color: const Color(0xFFFFD7AA)),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.home_outlined, size: 12, color: Color(0xFFF28C28)),
                                const SizedBox(width: 2),
                                Text(
                                  shop.familyName!,
                                  style: const TextStyle(
                                    color: Color(0xFFF28C28),
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      Text(
                        shop.description?.isNotEmpty == true
                            ? shop.description!
                            : '欢迎下单，商家正在准备好味道',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Color(0xFF999999), fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),

            // 如果商家有推荐菜品，则在底部渲染滑动列表
            if (previewDishes.isNotEmpty) ...[
              const SizedBox(height: 12),
              _ShopDishesPreview(shopId: shop.id, dishes: previewDishes),
            ]
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

class _HomeShopsSkeleton extends StatelessWidget {
  const _HomeShopsSkeleton();

  @override
  Widget build(BuildContext context) {
    const skeletonColor = Color(0xFFFFF4E8);
    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      sliver: SliverList.separated(
        itemCount: 5, // 占位显示 5 个商家
        separatorBuilder: (context, index) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          return Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
            ),
            // 改为 Column 布局，适配下方的菜品滑动列表骨架
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                // 第一部分：商家头部信息骨架
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        color: skeletonColor,
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(child: Container(height: 18, color: skeletonColor)),
                              const SizedBox(width: 16),
                              Container(height: 16, width: 48, color: skeletonColor),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Container(height: 14, width: double.infinity, color: skeletonColor),
                          const SizedBox(height: 6),
                          Container(height: 14, width: 120, color: skeletonColor),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                // 第二部分：下方 5 个推荐菜品的占位骨架
                SizedBox(
                  height: 110,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    physics: const NeverScrollableScrollPhysics(), // 骨架屏禁止滑动
                    itemCount: 5,
                    separatorBuilder: (_, __) => const SizedBox(width: 8),
                    itemBuilder: (_, __) {
                      return SizedBox(
                        width: 72,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              width: 72,
                              height: 72,
                              decoration: BoxDecoration(
                                color: skeletonColor,
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                            const SizedBox(height: 4),
                            Container(height: 12, width: 60, color: skeletonColor),
                            const SizedBox(height: 4),
                            Container(height: 12, width: 40, color: skeletonColor),
                          ],
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _ShopDishesPreview extends StatefulWidget {
  final int shopId;
  final List<Dish> dishes; // 传入该商家的预览菜品列表

  const _ShopDishesPreview({
    super.key,
    required this.shopId,
    required this.dishes,
  });

  @override
  State<_ShopDishesPreview> createState() => _ShopDishesPreviewState();
}

class _ShopDishesPreviewState extends State<_ShopDishesPreview> {
  late ScrollController _scrollController;
  bool _isNavigating = false;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_isNavigating) return;
    // 监听滑动越界：当滑动超过最大距离 50 像素时，触发进入店铺的跳转
    if (_scrollController.hasClients &&
        _scrollController.position.pixels > _scrollController.position.maxScrollExtent + 50) {
      _isNavigating = true;
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ShopOrderPage(shopId: widget.shopId),
        ),
      ).then((_) {
        // 返回时重置状态
        if (mounted) setState(() => _isNavigating = false);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.dishes.isEmpty) return const SizedBox.shrink();

    // 限制最多显示 5 个菜品
    final displayDishes = widget.dishes.take(8).toList();
    final hasMore = widget.dishes.length > 7;

    return SizedBox(
      height: 115, // 预留给图片(72) + 间距 + 名字 + 价格的高度
      child: ListView.separated(
        controller: _scrollController,
        scrollDirection: Axis.horizontal,
        // 必须使用允许越界回弹的物理效果，才能触发左滑跳转
        physics: const BouncingScrollPhysics(),
        itemCount: displayDishes.length + (hasMore ? 1 : 0),
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          // 渲染尾部的“左滑进入”提示块
          if (index == displayDishes.length) {
            return GestureDetector(
              onTap: () {
                if (_isNavigating) return;
                _isNavigating = true;
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => ShopOrderPage(shopId: widget.shopId)),
                ).then((_) {
                  if (mounted) setState(() => _isNavigating = false);
                });
              },
              child: Container(
                width: 72,
                decoration: BoxDecoration(
                  color: const Color(0xFFF7F7F7),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.arrow_forward_ios, size: 16, color: Color(0xFF999999)),
                    SizedBox(height: 6),
                    Text('左滑\n进入', textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Color(0xFF999999), height: 1.2)),
                  ],
                ),
              ),
            );
          }

          // 渲染常规菜品卡片
          final dish = displayDishes[index];
          return SizedBox(
            width: 72,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 72,
                  height: 72,
                  clipBehavior: Clip.antiAlias,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF7F7F7),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: dish.imageUrl?.isNotEmpty == true
                      ? Image.network(
                    resolveCustomerImageUrl(dish.imageUrl!),
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Icon(Icons.fastfood_rounded, color: Colors.grey, size: 24),
                  )
                      : const Icon(Icons.fastfood_rounded, color: Colors.grey, size: 24),
                ),
                const SizedBox(height: 4),
                Text(
                  dish.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: pageTextColor),
                ),
                Text(
                  '¥${dish.price.toStringAsFixed(1)}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: Color(0xFFF28C28), fontWeight: FontWeight.bold),
                ),
              ],
            ),
          );
        },
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