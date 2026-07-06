import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';
import 'dish_edit_page.dart';

class DishManagePage extends StatefulWidget {
  const DishManagePage({super.key});

  @override
  State<DishManagePage> createState() => _DishManagePageState();
}

class _DishManagePageState extends State<DishManagePage> {
  List<CategoryInfo> _categories = const [];
  List<DishInfo> _dishes = const [];
  bool _loading = true;
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
      final results = await Future.wait([
        shopApi.fetchCategories(),
        shopApi.fetchDishes(),
      ]);
      if (!mounted) {
        return;
      }
      setState(() {
        _categories = results[0] as List<CategoryInfo>;
        _dishes = (results[1] as PageResult<DishInfo>).records;
      });
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
    final tabs = [null, ..._categories];

    return DefaultTabController(
      key: ValueKey(tabs.length),
      length: tabs.isEmpty ? 1 : tabs.length,
      child: Scaffold(
        backgroundColor: Colors.white,
        appBar: AppBar(
          backgroundColor: Colors.white,
          foregroundColor: textColor,
          elevation: 0,
          title: const Text('菜品管理'),
          actions: [
            IconButton(
              tooltip: '新增分类',
              onPressed: _showCategoryDialog,
              icon: const Icon(Icons.create_new_folder_outlined),
              color: warmOrange,
            ),
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: FilledButton.icon(
                onPressed: _categories.isEmpty ? null : () => _openEditPage(),
                icon: const Icon(Icons.add),
                label: const Text('新增'),
                style: FilledButton.styleFrom(
                  backgroundColor: warmOrange,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ),
          ],
          bottom: TabBar(
            isScrollable: true,
            labelColor: warmOrange,
            unselectedLabelColor: mutedColor,
            indicatorColor: warmOrange,
            tabs: [
              const Tab(text: '全部'),
              for (final category in _categories) Tab(text: category.name),
            ],
          ),
        ),
        body: RefreshIndicator(
          color: warmOrange,
          onRefresh: _load,
          child: _buildBody(tabs),
        ),
      ),
    );
  }

  Widget _buildBody(List<CategoryInfo?> tabs) {
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
    if (_categories.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _ErrorPanel(
            message: '暂无分类，请先新增分类后再添加菜品',
            onRetry: _showCategoryDialog,
          ),
        ],
      );
    }

    return TabBarView(
      children: [
        for (final category in tabs)
          _DishList(
            dishes: category == null
                ? _dishes
                : _dishes
                      .where((dish) => dish.categoryId == category.id)
                      .toList(),
            categories: _categories,
            onEdit: (dish) => _openEditPage(dish: dish),
            onToggleStatus: _toggleStatus,
            onDelete: _deleteDish,
          ),
      ],
    );
  }

  Future<void> _openEditPage({DishInfo? dish}) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => DishEditPage(dish: dish, categories: _categories),
      ),
    );
    if (changed == true) {
      await _load();
    }
  }

  Future<void> _toggleStatus(DishInfo dish) async {
    final nextStatus = dish.isOnSale ? 'OFF_SALE' : 'ON_SALE';
    try {
      await shopApi.updateDishStatus(dish.id, nextStatus);
      await _load();
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    }
  }

  Future<void> _deleteDish(DishInfo dish) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除菜品'),
        content: Text('确定删除「${dish.name}」吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }

    try {
      await shopApi.deleteDish(dish.id);
      await _load();
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    }
  }

  Future<void> _showCategoryDialog() async {
    final controller = TextEditingController();
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('新增分类'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(hintText: '例如：盖饭、饮品'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (created != true) {
      return;
    }
    final name = controller.text.trim();
    if (name.isEmpty) {
      _showSnackBar('请填写分类名称');
      return;
    }

    try {
      await shopApi.createCategory(
        name: name,
        sortOrder: (_categories.length + 1) * 10,
      );
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

class _DishList extends StatelessWidget {
  const _DishList({
    required this.dishes,
    required this.categories,
    required this.onEdit,
    required this.onToggleStatus,
    required this.onDelete,
  });

  final List<DishInfo> dishes;
  final List<CategoryInfo> categories;
  final ValueChanged<DishInfo> onEdit;
  final ValueChanged<DishInfo> onToggleStatus;
  final ValueChanged<DishInfo> onDelete;

  @override
  Widget build(BuildContext context) {
    if (dishes.isEmpty) {
      return const Center(
        child: Text('暂无菜品', style: TextStyle(color: mutedColor)),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: dishes.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final dish = dishes[index];
        final category = categories
            .where((item) => item.id == dish.categoryId)
            .map((item) => item.name)
            .firstOrNull;
        return _DishCard(
          dish: dish,
          categoryName: category ?? '未分类',
          onEdit: () => onEdit(dish),
          onToggleStatus: () => onToggleStatus(dish),
          onDelete: () => onDelete(dish),
        );
      },
    );
  }
}

class _DishCard extends StatelessWidget {
  const _DishCard({
    required this.dish,
    required this.categoryName,
    required this.onEdit,
    required this.onToggleStatus,
    required this.onDelete,
  });

  final DishInfo dish;
  final String categoryName;
  final VoidCallback onEdit;
  final VoidCallback onToggleStatus;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: lineColor),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          _DishImage(imageUrl: dish.imageUrl),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        dish.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: textColor,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    _StatusBadge(isAvailable: dish.isOnSale),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      categoryName,
                      style: const TextStyle(color: mutedColor),
                    ),
                    Text(
                      '¥${dish.price.toStringAsFixed(2)}',
                      style: const TextStyle(
                        color: warmOrange,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text(
                      '月售 ${dish.salesCount}',
                      style: const TextStyle(color: mutedColor),
                    ),
                    Text(
                      '库存 ${dish.stock}',
                      style: const TextStyle(color: mutedColor),
                    ),
                  ],
                ),
              ],
            ),
          ),
          PopupMenuButton<String>(
            iconColor: warmOrange,
            onSelected: (value) {
              switch (value) {
                case 'edit':
                  onEdit();
                case 'status':
                  onToggleStatus();
                case 'delete':
                  onDelete();
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(value: 'edit', child: Text('编辑')),
              PopupMenuItem(
                value: 'status',
                child: Text(dish.isOnSale ? '下架' : '上架'),
              ),
              const PopupMenuItem(value: 'delete', child: Text('删除')),
            ],
          ),
        ],
      ),
    );
  }
}

class _DishImage extends StatelessWidget {
  const _DishImage({this.imageUrl});

  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    final url = imageUrl;
    if (url != null && url.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.network(
          _absoluteImageUrl(url),
          width: 56,
          height: 56,
          fit: BoxFit.cover,
          errorBuilder: (_, _, _) => _placeholder(),
        ),
      );
    }
    return _placeholder();
  }

  Widget _placeholder() {
    return Container(
      width: 56,
      height: 56,
      decoration: BoxDecoration(
        color: const Color(0xFFFFD2B8),
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Icon(Icons.restaurant_menu, color: textColor, size: 30),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.isAvailable});

  final bool isAvailable;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isAvailable ? softOrange : const Color(0xFFF5F5F5),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        isAvailable ? '上架中' : '已下架',
        style: TextStyle(
          color: isAvailable ? warmOrange : mutedColor,
          fontSize: 12,
          fontWeight: FontWeight.w600,
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
        color: softOrange,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: lineColor),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: warmOrange),
          const SizedBox(width: 12),
          Expanded(child: Text(message)),
          TextButton(onPressed: onRetry, child: const Text('处理')),
        ],
      ),
    );
  }
}

String _absoluteImageUrl(String url) {
  if (url.startsWith('http')) {
    return url;
  }
  final api = Uri.parse(
    const String.fromEnvironment(
      'SHOP_API_BASE_URL',
      defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
    ),
  );
  return api.replace(path: url).toString();
}
