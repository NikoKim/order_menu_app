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
        shopApi.fetchDishes(status: 'ON_SALE'),
        shopApi.fetchDishes(status: 'OFF_SALE'),
      ]);
      if (!mounted) {
        return;
      }
      setState(() {
        _categories = results[0] as List<CategoryInfo>;
        final onSaleDishes = (results[1] as PageResult<DishInfo>).records;
        final offSaleDishes = (results[2] as PageResult<DishInfo>).records;

        _dishes = [...onSaleDishes, ...offSaleDishes];
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
    final virtualOffSaleCategory = CategoryInfo(
      id: -1,
      name: '未上架',
      sortOrder: 9999,
      status: 1,
    );

    final tabs = [null,virtualOffSaleCategory, ..._categories];

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
              tooltip: '管理分类排序',
              onPressed: _categories.isEmpty ? null : _showCategoryManageDialog,
              icon: const Icon(Icons.sort),
              color: warmOrange,
            ),
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
              const Tab(text: '未上架'),
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

    return TabBarView(
      children: [
        for (final category in tabs)
          _DishList(
            dishes: category == null
                ? _dishes // 1. "全部" 标签：展示所有菜品（包含已上架和未上架）
                : category.id == -1
                ? _dishes.where((dish) => !dish.isOnSale).toList() // 2. "未上架" 标签：严格过滤，只保留 isOnSale 为 false 的菜品
                : _dishes.where((dish) => dish.categoryId == category.id).toList(), // 3. 常规分类：按分类 ID 展示
            categories: _categories,
            onEdit: (dish) => _openEditPage(dish: dish),
            onToggleStatus: _toggleStatus,
            onDelete: _deleteDish,
          ),
      ],
    );
  }

  Future<void> _showCategoryManageDialog() async {
    final changed = await showDialog<bool>(
      context: context,
      builder: (context) => _CategoryManageDialog(categories: _categories),
    );
    if (changed == true) {
      await _load();
    }
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

class _CategoryManageDialog extends StatefulWidget {
  const _CategoryManageDialog({required this.categories});
  final List<CategoryInfo> categories;

  @override
  State<_CategoryManageDialog> createState() => _CategoryManageDialogState();
}

class _CategoryManageDialogState extends State<_CategoryManageDialog> {
  late List<CategoryInfo> _localCategories;
  bool _saving = false;
  // 记录是否发生过名称修改，如果有，即使取消排序也要刷新外层列表
  bool _hasChanges = false;

  @override
  void initState() {
    super.initState();
    _localCategories = List.from(widget.categories);
    _localCategories.sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  }

  // 新增：编辑分类名称的方法
  Future<void> _editCategoryName(CategoryInfo category, int index) async {
    final controller = TextEditingController(text: category.name);
    final newName = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('修改分类名称'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(hintText: '请输入新的分类名称'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消', style: TextStyle(color: mutedColor)),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            style: FilledButton.styleFrom(backgroundColor: warmOrange),
            child: const Text('确定'),
          ),
        ],
      ),
    );

    // 如果名称有实质性修改，调用接口更新
    if (newName != null && newName.isNotEmpty && newName != category.name) {
      setState(() => _saving = true);
      try {
        final updatedCategory = await shopApi.updateCategory(
          categoryId: category.id,
          name: newName,
          sortOrder: category.sortOrder,
        );
        if (mounted) {
          setState(() {
            _localCategories[index] = updatedCategory;
            _hasChanges = true;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('名称已更新'), behavior: SnackBarBehavior.floating),
          );
        }
      } catch (error) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('更新失败: $error'), behavior: SnackBarBehavior.floating),
          );
        }
      } finally {
        if (mounted) {
          setState(() => _saving = false);
        }
      }
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final futures = <Future>[];
      for (int i = 0; i < _localCategories.length; i++) {
        final cat = _localCategories[i];
        final newSortOrder = (i + 1) * 10;

        if (cat.sortOrder != newSortOrder) {
          futures.add(shopApi.updateCategory(
            categoryId: cat.id,
            name: cat.name,
            sortOrder: newSortOrder,
          ));
        }
      }

      if (futures.isNotEmpty) {
        await Future.wait(futures);
        _hasChanges = true; // 顺序发生了改变
      }

      if (mounted) {
        Navigator.pop(context, _hasChanges); // 将变更状态传给外层页面
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('保存失败: $error'), behavior: SnackBarBehavior.floating),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  // 新增：删除分类的方法
  Future<void> _deleteCategory(CategoryInfo category, int index) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除分类'),
        content: Text('确定要删除分类“${category.name}”吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消', style: TextStyle(color: mutedColor)),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            child: const Text('删除'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() => _saving = true);
    try {
      await shopApi.deleteCategory(category.id);
      if (mounted) {
        setState(() {
          _localCategories.removeAt(index);
          _hasChanges = true; // 触发外层页面刷新以同步 Tab 栏
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('分类已删除'), behavior: SnackBarBehavior.floating),
        );
      }
    } on ApiException catch (error) {
      if (mounted) {
        // 拦截后端返回的“当前分类中存在菜品...”提示并展示给商家
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message), behavior: SnackBarBehavior.floating),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_saving,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop && result == null) {
          // 处理用户点击安卓物理返回键的情况
          Navigator.pop(context, _hasChanges);
        }
      },
      child: AlertDialog(
        title: const Text('管理分类', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 0, vertical: 16),
        content: SizedBox(
          width: double.maxFinite,
          height: 350,
          child: ReorderableListView.builder(
            shrinkWrap: true,
            itemCount: _localCategories.length,
            onReorder: (oldIndex, newIndex) {
              setState(() {
                if (newIndex > oldIndex) {
                  newIndex -= 1;
                }
                final item = _localCategories.removeAt(oldIndex);
                _localCategories.insert(newIndex, item);
              });
            },
            itemBuilder: (context, index) {
              final cat = _localCategories[index];
              return Material(
                key: ValueKey(cat.id),
                color: Colors.transparent,
                child: ListTile(
                  title: Text(cat.name),
                  leading: const Icon(Icons.drag_indicator, color: mutedColor),
                  tileColor: index.isEven ? const Color(0xFFFAFAFA) : Colors.white,
                  // 替换原有 trailing，支持编辑与删除
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: '重命名分类',
                        icon: const Icon(Icons.edit_outlined, color: warmOrange, size: 20),
                        onPressed: _saving ? null : () => _editCategoryName(cat, index),
                      ),
                      IconButton(
                        tooltip: '删除分类',
                        icon: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20),
                        onPressed: _saving ? null : () => _deleteCategory(cat, index),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
        actions: [
          TextButton(
            // 取消时，同样返回 _hasChanges 状态，防止已经改了名字但由于没点保存导致外层数据不刷新的问题
            onPressed: _saving ? null : () => Navigator.pop(context, _hasChanges),
            child: const Text('关闭', style: TextStyle(color: mutedColor)),
          ),
          FilledButton(
            onPressed: _saving ? null : _save,
            style: FilledButton.styleFrom(backgroundColor: warmOrange),
            child: Text(_saving ? '处理中...' : '保存排序'),
          ),
        ],
      ),
    );
  }
}
