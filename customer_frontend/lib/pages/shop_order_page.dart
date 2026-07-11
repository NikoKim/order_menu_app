import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import 'auth_page.dart';
import 'checkout_page.dart';
import 'dish_detail_page.dart';
import 'chat_detail_page.dart';

class ShopOrderPage extends StatefulWidget {
  const ShopOrderPage({super.key, required this.shopId});

  final int shopId;

  @override
  State<ShopOrderPage> createState() => _ShopOrderPageState();
}

class _ShopOrderPageState extends State<ShopOrderPage> {
  late final CustomerApi _api;
  late Future<ShopMenu> _menuFuture;
  final Map<String, CartItem> _cart = {};
  final GlobalKey _cartKey = GlobalKey();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _api = CustomerApi(AppScope.of(context).api);
    _menuFuture = _api.menu(widget.shopId);
  }

  int get _itemCount =>
      _cart.values.fold(0, (sum, item) => sum + item.quantity);

  double get _total => _cart.values.fold(0, (sum, item) => sum + item.subtotal);

  int _dishQuantity(Dish dish) => _cart.values
      .where((item) => item.dish.id == dish.id)
      .fold(0, (sum, item) => sum + item.quantity);

  bool _canAdd(Dish dish) {
    final stock = dish.stock;
    return stock == null || _dishQuantity(dish) < stock;
  }

  void _addCartItem(CartItem item, {Offset? from}) {
    if (!_canAdd(item.dish)) {
      _showSnack('已达到库存上限');
      return;
    }
    setState(() {
      final current = _cart[item.key];
      _cart[item.key] = current == null
          ? item
          : current.copyWith(quantity: current.quantity + item.quantity);
    });
    if (from != null) {
      _playAddAnimation(from);
    }
  }

  void _addDish(Dish dish, {Offset? from}) {
    if (dish.optionGroups.isNotEmpty) {
      _showSpecSheet(dish, from: from);
      return;
    }
    _addCartItem(CartItem(dish: dish, quantity: 1), from: from);
  }

  void _removeDish(Dish dish) {
    final item = _cart.values.where((i) => i.dish.id == dish.id).firstOrNull;
    if (item != null) {
      _removeItem(item);
    }
  }

  void _removeItem(CartItem item) {
    setState(() {
      final current = _cart[item.key];
      if (current == null) return;
      if (current.quantity <= 1) {
        _cart.remove(item.key);
      } else {
        _cart[item.key] = current.copyWith(quantity: current.quantity - 1);
      }
    });
  }

  void _setItemQuantity(CartItem item, int quantity) {
    final safeQuantity = quantity.clamp(0, item.dish.stock ?? quantity);
    setState(() {
      if (safeQuantity <= 0) {
        _cart.remove(item.key);
      } else {
        _cart[item.key] = item.copyWith(quantity: safeQuantity);
      }
    });
  }

  Future<void> _checkout(ShopMenu menu) async {
    final services = AppScope.of(context);
    if (!services.auth.isLoggedIn) {
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const AuthPage()));
      if (!mounted || !services.auth.isLoggedIn) return;
    }
    if (_cart.isEmpty) {
      _showSnack('请先选择菜品');
      return;
    }
    final submitted = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => CheckoutPage(
          shop: menu.shop,
          items: _cart.values.toList(),
          api: _api,
        ),
      ),
    );
    if (submitted == true) {
      setState(_cart.clear);
    }
  }

  void _showSpecSheet(Dish dish, {Offset? from}) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      builder: (sheetContext) => _SpecSheet(
        dish: dish,
        onConfirm: (item) {
          Navigator.of(sheetContext).pop();
          _addCartItem(item, from: from);
        },
      ),
    );
  }

  void _showCartSheet(ShopMenu menu) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      builder: (sheetContext) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            final items = _cart.values.toList();
            return SafeArea(
              child: Padding(
                padding: EdgeInsets.fromLTRB(20, 0, 20, 16 + MediaQuery.of(context).viewInsets.bottom),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Text('购物车', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                        const Spacer(),
                        if (items.isNotEmpty)
                          TextButton.icon(
                            onPressed: () {
                              setState(_cart.clear);
                              setSheetState(() {});
                            },
                            icon: const Icon(Icons.delete_outline, size: 18),
                            label: const Text('清空', style: TextStyle(color: pageMutedColor)),
                          ),
                      ],
                    ),
                    if (items.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 36),
                        child: Center(child: Text('还没有选择菜品', style: TextStyle(color: pageMutedColor))),
                      )
                    else
                      ConstrainedBox(
                        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.5),
                        child: ListView.separated(
                          shrinkWrap: true,
                          itemBuilder: (context, index) {
                            final item = items[index];
                            return _CartSheetItem(
                              item: item,
                              canAdd: _canAdd(item.dish),
                              onAdd: () {
                                _addCartItem(item.copyWith(quantity: 1));
                                setSheetState(() {});
                              },
                              onRemove: () {
                                _removeItem(item);
                                setSheetState(() {});
                              },
                              onSetQuantity: (quantity) {
                                _setItemQuantity(item, quantity);
                                setSheetState(() {});
                              },
                            );
                          },
                          separatorBuilder: (_, _) => const Divider(color: pageLineColor, height: 1),
                          itemCount: items.length,
                        ),
                      ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            '共 $_itemCount 份  ¥${_total.toStringAsFixed(2)}',
                            style: const TextStyle(color: pageTextColor, fontSize: 16, fontWeight: FontWeight.w900),
                          ),
                        ),
                        FilledButton(
                          onPressed: items.isEmpty
                              ? null
                              : () {
                            Navigator.of(sheetContext).pop();
                            _checkout(menu);
                          },
                          child: const Text('去下单'),
                        ),
                      ],
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

  void _playAddAnimation(Offset from) {
    final cartBox = _cartKey.currentContext?.findRenderObject() as RenderBox?;
    final overlay = Overlay.of(context);
    if (cartBox == null) return;
    final to = cartBox.localToGlobal(cartBox.size.center(Offset.zero));
    late OverlayEntry entry;
    entry = OverlayEntry(
      builder: (context) => TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: 1),
        duration: const Duration(milliseconds: 520),
        curve: Curves.easeInOutCubic,
        onEnd: entry.remove,
        builder: (context, value, child) {
          final x = from.dx + (to.dx - from.dx) * value;
          final y = from.dy + (to.dy - from.dy) * value - 82 * value * (1 - value);
          return Positioned(
            left: x - 12,
            top: y - 12,
            child: IgnorePointer(
              child: Material(
                color: warmOrange,
                shape: const CircleBorder(),
                child: const SizedBox(
                  width: 24,
                  height: 24,
                  child: Center(
                    child: Text('1', style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w900)),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
    overlay.insert(entry);
  }

  void _showSnack(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<ShopMenu>(
      future: _menuFuture,
      builder: (context, snapshot) {
        final menu = snapshot.data;
        return Scaffold(
          backgroundColor: Colors.white,
          appBar: AppBar(
            title: Text(menu?.shop.name ?? '商家点餐', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            backgroundColor: Colors.white,
            surfaceTintColor: Colors.white,
            elevation: 0,
            actions: [
              if (menu != null && AppScope.of(context).auth.isLoggedIn)
                IconButton(
                  tooltip: '联系商家',
                  icon: const Icon(Icons.chat_bubble_outline),
                  onPressed: () async {
                    try {
                      final conversation = await _api.startChatWithShop(menu.shop.id);
                      if (!context.mounted) return;
                      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChatDetailPage(conversation: conversation)));
                    } on ApiException catch (error) {
                      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
                    }
                  },
                ),
            ],
          ),
          body: switch (snapshot.connectionState) {
            ConnectionState.waiting => const _MenuSkeleton(),
            _ when snapshot.hasError => _LoadError(
              message: snapshot.error.toString(),
              onRetry: () => setState(() {
                _menuFuture = _api.menu(widget.shopId);
              }),
            ),
            _ => _MenuBody(
              menu: menu!,
              cart: _cart,
              dishQuantity: _dishQuantity,
              canAdd: _canAdd,
              onAdd: _addDish,
              onRemove: _removeDish,
            ),
          },
          bottomNavigationBar: menu == null || _itemCount == 0
              ? null
              : Padding(
            padding: EdgeInsets.fromLTRB(16, 10, 16, MediaQuery.of(context).padding.bottom + 12),
            child: Container(
              height: 56,
              padding: const EdgeInsets.only(left: 16, right: 6),
              decoration: BoxDecoration(
                color: const Color(0xFF2A2A2A),
                borderRadius: BorderRadius.circular(28),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.15),
                    blurRadius: 16,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: Row(
                children: [
                  GestureDetector(
                    key: _cartKey,
                    onTap: () => _showCartSheet(menu),
                    behavior: HitTestBehavior.opaque,
                    child: Stack(
                      clipBehavior: Clip.none,
                      alignment: Alignment.center,
                      children: [
                        const Icon(Icons.shopping_bag_outlined, color: Colors.white, size: 28),
                        Positioned(
                          right: -6,
                          top: -4,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                            decoration: BoxDecoration(
                              color: warmOrange,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              '$_itemCount',
                              style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold),
                            ),
                          ),
                        )
                      ],
                    ),
                  ),
                  const SizedBox(width: 20),
                  Expanded(
                    child: GestureDetector(
                      onTap: () => _showCartSheet(menu),
                      behavior: HitTestBehavior.opaque,
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '¥${_total.toStringAsFixed(2)}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  SizedBox(
                    height: 44,
                    width: 90,
                    child: FilledButton(
                      style: FilledButton.styleFrom(
                        backgroundColor: warmOrange,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
                        padding: EdgeInsets.zero,
                      ),
                      onPressed: () => _checkout(menu),
                      child: const Text('去结算', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _MenuBody extends StatefulWidget {
  const _MenuBody({
    required this.menu,
    required this.cart,
    required this.dishQuantity,
    required this.canAdd,
    required this.onAdd,
    required this.onRemove,
  });

  final ShopMenu menu;
  final Map<String, CartItem> cart;
  final int Function(Dish dish) dishQuantity;
  final bool Function(Dish dish) canAdd;
  final void Function(Dish dish, {Offset? from}) onAdd;
  final void Function(Dish dish) onRemove;

  @override
  State<_MenuBody> createState() => _MenuBodyState();
}

class _MenuBodyState extends State<_MenuBody> {
  int _selectedCategory = 0;
  final List<GlobalKey> _categoryKeys = [];
  bool _isManualScrolling = false;

  final ScrollController _leftController = ScrollController();

  @override
  void initState() {
    super.initState();
    _initKeys();
  }

  @override
  void dispose() {
    _leftController.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant _MenuBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.menu.categories.length != widget.menu.categories.length) {
      _initKeys();
    }
  }

  void _initKeys() {
    _categoryKeys.clear();
    for (int i = 0; i < widget.menu.categories.length; i++) {
      _categoryKeys.add(GlobalKey());
    }
  }

  void _scrollToCategory(int index) async {
    if (_categoryKeys.isEmpty || index >= _categoryKeys.length) return;

    final context = _categoryKeys[index].currentContext;
    if (context == null) return;

    // 开始锁定手动滑动状态
    setState(() {
      _selectedCategory = index;
      _isManualScrolling = true;
    });

    // 拿到右侧菜品列表的 Scrollable
    final scrollable = Scrollable.maybeOf(context);
    if (scrollable != null) {
      final scrollPosition = scrollable.position;
      final box = context.findRenderObject() as RenderBox?;
      final scrollableBox = scrollable.context.findRenderObject() as RenderBox?;

      if (box != null && scrollableBox != null) {
        // 计算目标分类距离当前滚动视口顶部的相对偏移量
        final offsetDiff = box.localToGlobal(Offset.zero, ancestor: scrollableBox).dy;

        // 真正的目标位置 = 当前已经滚动的像素 + 偏移量
        final targetOffset = (scrollPosition.pixels + offsetDiff).clamp(
          scrollPosition.minScrollExtent,
          scrollPosition.maxScrollExtent,
        );

        // 直接驱动底层 position 滚动，完美避开 NestedScrollView 的坑
        await scrollPosition.animateTo(
          targetOffset,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOutCubic,
        );
      }
    }

    // 留出一小段缓冲时间防止由于惯性触发错误的高亮更新
    await Future.delayed(const Duration(milliseconds: 50));
    if (mounted) {
      setState(() {
        _isManualScrolling = false;
      });
    }
  }

  void _onScroll() {
    if (_isManualScrolling || _categoryKeys.isEmpty) return;

    int activeIndex = 0;
    for (int i = 0; i < _categoryKeys.length; i++) {
      final context = _categoryKeys[i].currentContext;
      if (context != null) {
        final box = context.findRenderObject() as RenderBox?;
        if (box != null) {
          final position = box.localToGlobal(Offset.zero).dy;
          if (position > 180) {
            activeIndex = (i - 1).clamp(0, _categoryKeys.length - 1);
            break;
          } else {
            activeIndex = i;
          }
        }
      }
    }

    if (_selectedCategory != activeIndex) {
      setState(() {
        _selectedCategory = activeIndex;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final categories = widget.menu.categories;
    if (categories.isEmpty) {
      return Column(
        children: [
          Container(
            color: Colors.white,
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
            child: _ShopHeader(shop: widget.menu.shop),
          ),
          Container(height: 8, color: const Color(0xFFF7F7F7)),
          const Expanded(child: _EmptyMenu()),
        ],
      );
    }

    return NestedScrollView(
      headerSliverBuilder: (context, innerBoxIsScrolled) {
        return [
          SliverToBoxAdapter(
            child: Column(
              children: [
                Container(
                  color: Colors.white,
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                  child: _ShopHeader(shop: widget.menu.shop),
                ),
                Container(
                  height: 8,
                  color: const Color(0xFFF7F7F7),
                ),
              ],
            ),
          ),
        ];
      },
      body: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 86,
            color: const Color(0xFFF7F7F7),
            child: ListView.builder(
              controller: _leftController,
              padding: const EdgeInsets.only(bottom: 32),
              itemCount: categories.length,
              itemBuilder: (context, index) {
                final category = categories[index];
                final isSelected = index == _selectedCategory;

                int categoryCount = 0;
                for (final dish in category.dishes) {
                  categoryCount += widget.dishQuantity(dish);
                }

                return InkWell(
                  onTap: () => _scrollToCategory(index),
                  child: Container(
                    decoration: BoxDecoration(
                      color: isSelected ? Colors.white : Colors.transparent,
                    ),
                    // 使用 Stack 来使内容和角标分离，基于格子进行定位
                    child: Stack(
                      clipBehavior: Clip.none,
                      children: [
                        // 类别文字本身
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 18),
                          alignment: Alignment.centerLeft,
                          child: Text(
                            category.name,
                            style: TextStyle(
                              color: isSelected ? pageTextColor : const Color(0xFF77716B),
                              fontSize: 13,
                              fontWeight: isSelected ? FontWeight.w900 : FontWeight.normal,
                            ),
                          ),
                        ),
                        // 将角标固定在整个格子的右上角，保证竖直方向完美对齐
                        if (categoryCount > 0)
                          Positioned(
                            top: 4,
                            right: 6,
                            child: Container(
                              width: 16,
                              height: 16, // 固定宽高为正圆，保证 >9 时宽度也不会变
                              alignment: Alignment.center,
                              decoration: const BoxDecoration(
                                color: Color(0xFFF28C28), // warmOrange
                                shape: BoxShape.circle,
                              ),
                              // 超过 9 时使用密集的点表示
                              child: categoryCount > 9
                                  ? const Row(
                                mainAxisSize: MainAxisSize.min,
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(Icons.circle, size: 2.5, color: Colors.white),
                                  SizedBox(width: 1),
                                  Icon(Icons.circle, size: 2.5, color: Colors.white),
                                  SizedBox(width: 1),
                                  Icon(Icons.circle, size: 2.5, color: Colors.white),
                                ],
                              )
                                  : Text(
                                '$categoryCount',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          Expanded(
            child: Container(
              color: Colors.white,
              child: NotificationListener<ScrollNotification>(
                onNotification: (notification) {
                  _onScroll();
                  return false;
                },
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(12, 0, 16, 32),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (int i = 0; i < categories.length; i++)
                        ...[
                          Container(
                            key: _categoryKeys[i],
                            padding: const EdgeInsets.only(top: 12, bottom: 16),
                            child: Text(
                              categories[i].name,
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w900, color: pageTextColor),
                            ),
                          ),
                          for (int j = 0; j < categories[i].dishes.length; j++)
                            Column(
                              children: [
                                _DishCard(
                                  shop: widget.menu.shop,
                                  dish: categories[i].dishes[j],
                                  quantity: widget.dishQuantity(categories[i].dishes[j]),
                                  canAdd: widget.canAdd(categories[i].dishes[j]),
                                  onAdd: widget.onAdd,
                                  onRemove: widget.onRemove,
                                ),
                                // 已经彻底去除了底部的灰色横线 (Divider)
                                // 改用 24px 的纯净留白替代，让卡片呼吸感更强
                                if (j < categories[i].dishes.length - 1)
                                  const SizedBox(height: 24),
                              ],
                            ),
                        ],
                      const SizedBox(height: 16),
                      const AppFooter(),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DishCard extends StatelessWidget {
  const _DishCard({
    super.key,
    required this.shop,
    required this.dish,
    required this.quantity,
    required this.canAdd,
    required this.onAdd,
    required this.onRemove,
  });

  final Shop shop;
  final Dish dish;
  final int quantity;
  final bool canAdd;
  final void Function(Dish dish, {Offset? from}) onAdd;
  final void Function(Dish dish) onRemove;

  @override
  Widget build(BuildContext context) {
    final soldOut = dish.status == 'OFF_SALE' || dish.status == 'OFF_SHELF' || dish.stock == 0;
    final hasSpecs = dish.optionGroups.isNotEmpty;
    const primaryOrange = Color(0xFFF28C28);

    return InkWell(
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => DishDetailPage(
              dish: dish,
              shop: shop,
              initialQuantity: quantity,
              onAdd: canAdd ? () => onAdd(dish) : null,
              onRemove: () => onRemove(dish),
            ),
          ),
        );
      },
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 80,
            height: 80,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              color: const Color(0xFFF7F7F7),
              borderRadius: BorderRadius.circular(8),
            ),
            child: dish.imageUrl?.isNotEmpty == true
                ? Image.network(
              resolveCustomerImageUrl(dish.imageUrl!),
              fit: BoxFit.cover,
              loadingBuilder: (context, child, progress) {
                if (progress == null) {
                  return TweenAnimationBuilder<double>(
                    tween: Tween(begin: 0.0, end: 1.0),
                    duration: const Duration(milliseconds: 300),
                    builder: (context, value, child) =>
                        Opacity(opacity: value, child: child),
                    child: child,
                  );
                }
                // 替换转圈动画为呼吸色块
                return const _PulsePlaceholder();
              },
              errorBuilder: (context, error, stackTrace) =>
              const Icon(Icons.fastfood_rounded, color: Colors.grey, size: 32),
            )
                : const Icon(Icons.fastfood_rounded, color: Colors.grey, size: 32),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 80),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        dish.name,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: pageTextColor,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (dish.description?.isNotEmpty == true)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            dish.description!,
                            style: const TextStyle(
                              fontSize: 11,
                              color: Color(0xFF999999),
                              height: 1.3,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      const SizedBox(height: 6),
                      Text(
                        '月售 ${dish.salesCount ?? 0}',
                        style: const TextStyle(fontSize: 11, color: Color(0xFF999999)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Flexible(
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          alignment: Alignment.bottomLeft,
                          child: Text.rich(
                            TextSpan(
                              children: [
                                const TextSpan(
                                  text: '¥ ',
                                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                                ),
                                TextSpan(
                                  text: dish.price.toStringAsFixed(2),
                                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                                ),
                              ],
                            ),
                            style: const TextStyle(color: primaryOrange),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      _buildActionArea(context, soldOut, hasSpecs, primaryOrange),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionArea(BuildContext context, bool soldOut, bool hasSpecs, Color primaryColor) {
    if (soldOut) {
      return const Padding(
        padding: EdgeInsets.only(bottom: 2),
        child: Text(
          '已售罄',
          style: TextStyle(fontSize: 11, color: Color(0xFFCCCCCC), fontWeight: FontWeight.w600),
        ),
      );
    }

    if (hasSpecs) {
      return GestureDetector(
        onTap: canAdd ? () => onAdd(dish, from: _centerOf(context)) : null,
        behavior: HitTestBehavior.opaque,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          decoration: BoxDecoration(
            color: primaryColor,
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Text('选规格', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
        ),
      );
    }

    final isExpanded = quantity > 0;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOutCubic,
      width: isExpanded ? 68.0 : 22.0,
      height: 24.0,
      child: ClipRect(
        child: OverflowBox(
          maxWidth: 68.0,
          minWidth: 68.0,
          alignment: Alignment.centerRight,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              GestureDetector(
                onTap: isExpanded ? () => onRemove(dish) : null,
                behavior: HitTestBehavior.opaque,
                child: Icon(Icons.remove_circle_outline, color: primaryColor, size: 22),
              ),
              SizedBox(
                width: 24,
                child: Text(
                  quantity.toString(),
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: pageTextColor),
                ),
              ),
              GestureDetector(
                onTap: canAdd ? () => onAdd(dish, from: _centerOf(context)) : null,
                behavior: HitTestBehavior.opaque,
                child: Icon(
                  Icons.add_circle,
                  color: canAdd ? primaryColor : const Color(0xFFFFD4C2),
                  size: 22,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Offset _centerOf(BuildContext context) {
    final box = context.findRenderObject() as RenderBox?;
    if (box == null) return Offset.zero;
    return box.localToGlobal(box.size.center(Offset.zero));
  }
}

class _ShopHeader extends StatelessWidget {
  const _ShopHeader({required this.shop});

  final Shop shop;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SquareImage(
          imageUrl: shop.coverImageUrl,
          size: 64,
          fallbackIcon: Icons.storefront,
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 商家名称
              Text(
                shop.name,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                  color: pageTextColor,
                ),
              ),
              const SizedBox(height: 6),
              // 商家描述
              Text(
                shop.description?.isNotEmpty == true
                    ? shop.description!
                    : '欢迎下单，商家正在准备好味道',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Color(0xFF999999), fontSize: 12),
              ),
              // 新增：如果商家开启了家庭属性，则在底部显示家庭徽标
              if (shop.familyName?.isNotEmpty == true)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFF4E8), // softOrange
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
                            color: Color(0xFFF28C28), // warmOrange
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _SpecSheet extends StatefulWidget {
  const _SpecSheet({required this.dish, required this.onConfirm});

  final Dish dish;
  final ValueChanged<CartItem> onConfirm;

  @override
  State<_SpecSheet> createState() => _SpecSheetState();
}

class _SpecSheetState extends State<_SpecSheet> {
  final Map<int, Set<int>> _selected = {};
  int _quantity = 1;

  List<int> get _selectedIds {
    final ids = <int>[];
    for (final values in _selected.values) {
      ids.addAll(values);
    }
    ids.sort();
    return ids;
  }

  double get _optionAmount {
    double amount = 0;
    for (final group in widget.dish.optionGroups) {
      for (final option in group.options) {
        if (_selected[group.id]?.contains(option.id) == true) {
          amount += option.priceDelta;
        }
      }
    }
    return amount;
  }

  String? get _selectedText {
    final labels = <String>[];
    for (final group in widget.dish.optionGroups) {
      final names = group.options
          .where((option) => _selected[group.id]?.contains(option.id) == true)
          .map((option) => option.name)
          .toList();
      if (names.isNotEmpty) {
        labels.add('${group.name}: ${names.join(", ")}');
      }
    }
    return labels.isEmpty ? null : labels.join('; ');
  }

  String? _validationMessage() {
    for (final group in widget.dish.optionGroups) {
      final count = _selected[group.id]?.length ?? 0;
      if (count < group.minSelect) {
        return '${group.name} 至少选择 ${group.minSelect} 项';
      }
      final maxSelect = group.isMultiple ? group.maxSelect : 1;
      if (maxSelect != null && count > maxSelect) {
        return '${group.name} 最多选择 $maxSelect 项';
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final unitPrice = widget.dish.price + _optionAmount;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          0,
          20,
          16 + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _SquareImage(
                  imageUrl: widget.dish.imageUrl,
                  size: 58,
                  fallbackIcon: Icons.ramen_dining,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.dish.name,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      Text(
                        '¥${unitPrice.toStringAsFixed(2)}',
                        style: const TextStyle(
                          color: warmOrange,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: [
                  for (final group in widget.dish.optionGroups)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${group.name}${group.minSelect > 0 ? '（必选）' : ''}',
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              for (final option in group.options)
                                ChoiceChip(
                                  selected:
                                  _selected[group.id]?.contains(option.id) ==
                                      true,
                                  label: Text(
                                    option.priceDelta > 0
                                        ? '${option.name} +¥${option.priceDelta.toStringAsFixed(2)}'
                                        : option.name,
                                  ),
                                  onSelected: (selected) {
                                    setState(() {
                                      final values = _selected.putIfAbsent(
                                        group.id,
                                            () => <int>{},
                                      );
                                      if (group.isMultiple) {
                                        selected
                                            ? values.add(option.id)
                                            : values.remove(option.id);
                                      } else {
                                        if (selected) {
                                          values
                                            ..clear()
                                            ..add(option.id);
                                        } else {
                                          values.remove(option.id);
                                        }
                                      }
                                    });
                                  },
                                ),
                            ],
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            Row(
              children: [
                _QuantityInput(
                  quantity: _quantity,
                  onChanged: (value) => setState(() => _quantity = value),
                ),
                const Spacer(),
                FilledButton.icon(
                  onPressed: () {
                    final message = _validationMessage();
                    if (message != null) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(message)),
                      );
                      return;
                    }
                    widget.onConfirm(
                      CartItem(
                        dish: widget.dish,
                        quantity: _quantity,
                        selectedOptionIds: _selectedIds,
                        selectedOptionText: _selectedText,
                        optionAmount: _optionAmount,
                      ),
                    );
                  },
                  icon: const Icon(Icons.add_shopping_cart),
                  label: const Text('加入购物车'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _CartSheetItem extends StatelessWidget {
  const _CartSheetItem({
    required this.item,
    required this.canAdd,
    required this.onAdd,
    required this.onRemove,
    required this.onSetQuantity,
  });

  final CartItem item;
  final bool canAdd;
  final VoidCallback onAdd;
  final VoidCallback onRemove;
  final ValueChanged<int> onSetQuantity;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          _SquareImage(
            imageUrl: item.dish.imageUrl,
            size: 48,
            fallbackIcon: Icons.ramen_dining,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.dish.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                if (item.selectedOptionText?.isNotEmpty == true) ...[
                  const SizedBox(height: 3),
                  Text(
                    item.selectedOptionText!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: pageMutedColor, fontSize: 11),
                  ),
                ],
                const SizedBox(height: 4),
                Text(
                  '¥${item.unitPrice.toStringAsFixed(2)}',
                  style: const TextStyle(color: warmOrange, fontWeight: FontWeight.bold),
                ),
              ],
            ),
          ),
          _QuantityInput(
            quantity: item.quantity,
            canAdd: canAdd,
            onAdd: onAdd,
            onRemove: onRemove,
            onChanged: onSetQuantity,
          ),
        ],
      ),
    );
  }
}

class _QuantityInput extends StatelessWidget {
  const _QuantityInput({
    required this.quantity,
    required this.onChanged,
    this.canAdd = true,
    this.onAdd,
    this.onRemove,
  });

  final int quantity;
  final bool canAdd;
  final VoidCallback? onAdd;
  final VoidCallback? onRemove;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          onPressed: onRemove ?? () => onChanged(quantity - 1),
          icon: const Icon(Icons.remove_circle_outline, color: warmOrange),
        ),
        InkWell(
          onTap: () async {
            final value = await _showQuantityDialog(context, quantity);
            if (value != null) {
              onChanged(value);
            }
          },
          child: SizedBox(
            width: 30,
            child: Text(
              '$quantity',
              textAlign: TextAlign.center,
              style: const TextStyle(fontWeight: FontWeight.w900),
            ),
          ),
        ),
        IconButton(
          onPressed: canAdd ? (onAdd ?? () => onChanged(quantity + 1)) : null,
          icon: Icon(
            Icons.add_circle,
            color: canAdd ? warmOrange : const Color(0xFFFFD4C2),
          ),
        ),
      ],
    );
  }
}

Future<int?> _showQuantityDialog(BuildContext context, int initial) {
  final controller = TextEditingController(text: '$initial');
  return showDialog<int>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text('输入数量'),
      content: TextField(
        controller: controller,
        keyboardType: TextInputType.number,
        autofocus: true,
        decoration: const InputDecoration(hintText: '数量'),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () {
            final value = int.tryParse(controller.text.trim());
            Navigator.of(context).pop(value == null || value < 1 ? 1 : value);
          },
          child: const Text('确定'),
        ),
      ],
    ),
  );
}

class _SquareImage extends StatelessWidget {
  const _SquareImage({
    required this.imageUrl,
    required this.size,
    required this.fallbackIcon,
  });

  final String? imageUrl;
  final double size;
  final IconData fallbackIcon;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: const Color(0xFFF7F7F7),
        borderRadius: BorderRadius.circular(8),
      ),
      child: imageUrl?.isNotEmpty == true
          ? Image.network(
        resolveCustomerImageUrl(imageUrl!),
        fit: BoxFit.cover,
        loadingBuilder: (context, child, progress) {
          if (progress == null) {
            return TweenAnimationBuilder<double>(
              tween: Tween(begin: 0.0, end: 1.0),
              duration: const Duration(milliseconds: 300),
              builder: (context, value, child) =>
                  Opacity(opacity: value, child: child),
              child: child,
            );
          }
          // 替换转圈动画为呼吸色块
          return const _PulsePlaceholder();
        },
        errorBuilder: (_, _, _) => Icon(
          fallbackIcon,
          color: Colors.grey[400],
          size: size * 0.48,
        ),
      )
          : Icon(
        fallbackIcon,
        color: Colors.grey[400],
        size: size * 0.48,
      ),
    );
  }
}

class _EmptyMenu extends StatelessWidget {
  const _EmptyMenu();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(
        child: Text('商家暂未上架菜品', style: TextStyle(color: pageMutedColor)),
      ),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off_outlined, color: warmOrange, size: 42),
            const SizedBox(height: 12),
            const Text('菜单加载失败', style: TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: pageMutedColor),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}

class _MenuSkeleton extends StatelessWidget {
  const _MenuSkeleton();

  @override
  Widget build(BuildContext context) {
    const skeletonColor = Color(0xFFFFF4E8); // 使用 softOrange 风格的占位色

    return Column(
      children: [
        // 顶部商家信息骨架屏
        Container(
          color: Colors.white,
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: skeletonColor,
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(height: 20, width: 150, color: skeletonColor),
                    const SizedBox(height: 10),
                    Container(height: 14, width: double.infinity, color: skeletonColor),
                    const SizedBox(height: 4),
                    Container(height: 14, width: 200, color: skeletonColor),
                  ],
                ),
              ),
            ],
          ),
        ),
        Container(height: 8, color: const Color(0xFFF7F7F7)),
        // 底部菜单骨架屏
        Expanded(
          child: Row(
            children: [
              // 左侧分类占位
              Container(
                width: 86,
                color: const Color(0xFFF7F7F7),
                child: Column(
                  children: List.generate(
                    6,
                        (index) => Container(
                      height: 50,
                      margin: const EdgeInsets.only(bottom: 2),
                      color: index == 0 ? Colors.white : Colors.transparent,
                      child: index == 0
                          ? Center(
                        child: Container(
                          height: 14,
                          width: 40,
                          color: skeletonColor,
                        ),
                      )
                          : null,
                    ),
                  ),
                ),
              ),
              // 右侧菜品列表占位
              Expanded(
                child: Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(height: 18, width: 80, color: skeletonColor),
                      const SizedBox(height: 16),
                      ...List.generate(
                        4,
                            (index) => Padding(
                          padding: const EdgeInsets.only(bottom: 24),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                width: 80,
                                height: 80,
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
                                    Container(height: 16, width: 120, color: skeletonColor),
                                    const SizedBox(height: 8),
                                    Container(height: 12, width: double.infinity, color: skeletonColor),
                                    const SizedBox(height: 4),
                                    Container(height: 12, width: 150, color: skeletonColor),
                                    const SizedBox(height: 12),
                                    Container(height: 16, width: 60, color: skeletonColor),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _PulsePlaceholder extends StatefulWidget {
  const _PulsePlaceholder();

  @override
  State<_PulsePlaceholder> createState() => _PulsePlaceholderState();
}

class _PulsePlaceholderState extends State<_PulsePlaceholder>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<Color?> _colorAnimation;

  @override
  void initState() {
    super.initState();
    // 设置 800 毫秒的动画时长，并开启无限往返播放
    _controller = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    )..repeat(reverse: true);

    // 设置颜色的渐变范围：从淡灰色到淡淡灰色
    _colorAnimation = ColorTween(
      begin: const Color(0xFFEAEAEA),
      end: const Color(0xFFF8F8F8),
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _colorAnimation,
      builder: (context, child) {
        return Container(
          width: double.infinity,
          height: double.infinity,
          color: _colorAnimation.value,
        );
      },
    );
  }
}
