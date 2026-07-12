import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../widgets/app_notice.dart';
import 'dish_edit_page.dart';

const _warmOrange = Color(0xFFF28C28);
const _softOrange = Color(0xFFFFF4E8);
const _pageBgColor = Color(0xFFF7F7F7);
const _cardColor = Colors.white;
const _lineColor = Color(0xFFF0E7DE);
const _textColor = Color(0xFF242424);
const _mutedColor = Color(0xFF77716B);
const _greenColor = Color(0xFF3E8F55);

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
  bool _creatingCategory = false;
  bool _showCreateCategoryPanel = false;
  bool _showCategoryManagePanel = false;
  bool _savingCategoryChange = false;
  final _newCategoryController = TextEditingController();

  // 使用普通筛选状态，避免分类数量变化时触发 TabController 相关问题。
  String _selectedFilterKey = _DishFilterKey.all;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _newCategoryController.dispose();
    super.dispose();
  }

  Future<void> _load({bool showLoading = true}) async {
    if (showLoading && mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    } else if (mounted) {
      setState(() => _error = null);
    }

    try {
      final results = await Future.wait([
        shopApi.fetchCategories(),
        shopApi.fetchDishes(status: 'ON_SALE'),
        shopApi.fetchDishes(status: 'OFF_SALE'),
      ]);
      if (!mounted) return;

      final categories = results[0] as List<CategoryInfo>;
      final onSaleDishes = (results[1] as PageResult<DishInfo>).records;
      final offSaleDishes = (results[2] as PageResult<DishInfo>).records;

      setState(() {
        _categories = [...categories]
          ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
        _dishes = [...onSaleDishes, ...offSaleDishes];
        _loading = false;

        if (_selectedFilterKey.startsWith(_DishFilterKey.categoryPrefix)) {
          final selectedId = _categoryIdFromKey(_selectedFilterKey);
          final exists = _categories.any(
            (category) => category.id == selectedId,
          );
          if (!exists) _selectedFilterKey = _DishFilterKey.all;
        }
      });
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _error = error.message;
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _pageBgColor,
      appBar: AppBar(
        backgroundColor: _pageBgColor,
        foregroundColor: _textColor,
        elevation: 0,
        surfaceTintColor: _pageBgColor,
        title: const Text(
          '菜品管理',
          style: TextStyle(fontWeight: FontWeight.w900),
        ),
        actions: [
          IconButton(
            tooltip: '新增分类',
            onPressed: _creatingCategory
                ? null
                : () {
                    FocusManager.instance.primaryFocus?.unfocus();
                    setState(() {
                      _showCategoryManagePanel = false;
                      _showCreateCategoryPanel = !_showCreateCategoryPanel;
                    });
                  },
            icon: const Icon(Icons.create_new_folder_outlined),
          ),
          IconButton(
            tooltip: '修改分类',
            onPressed: _categories.isEmpty || _savingCategoryChange
                ? null
                : () {
                    FocusManager.instance.primaryFocus?.unfocus();
                    setState(() {
                      _showCreateCategoryPanel = false;
                      _showCategoryManagePanel = !_showCategoryManagePanel;
                    });
                  },
            icon: const Icon(Icons.edit_note_rounded),
          ),
          IconButton(
            tooltip: '新增菜品',
            // 顶部工具栏新增菜品：
            // 选中具体分类时默认使用该分类；选中“全部”或“已下架”时默认第一个分类。
            onPressed: _categories.isEmpty
                ? null
                : () => _openEditPage(
                    preferredCategoryId: _preferredCategoryIdForNewDish(),
                  ),
            icon: const Icon(Icons.add_circle_outline_rounded),
          ),
          IconButton(
            tooltip: '刷新',
            onPressed: _loading ? null : () => _load(showLoading: false),
            icon: const Icon(Icons.refresh_rounded),
          ),
          const SizedBox(width: 6),
        ],
      ),
      body: RefreshIndicator(
        color: _warmOrange,
        backgroundColor: Colors.white,
        onRefresh: () => _load(showLoading: false),
        child: _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const _DishSkeletonList();
    }

    if (_error != null) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [_ErrorPanel(message: _error!, onRetry: _load)],
      );
    }

    final filteredDishes = _filteredDishes();
    final onSaleCount = _dishes.where((dish) => dish.isOnSale).length;
    final offSaleCount = _dishes.length - onSaleCount;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        _CompactStatsBar(
          dishCount: _dishes.length,
          onSaleCount: onSaleCount,
          offSaleCount: offSaleCount,
          categoryCount: _categories.length,
        ),
        const SizedBox(height: 10),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 160),
          child: _showCreateCategoryPanel
              ? Padding(
                  key: const ValueKey('create-category'),
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _CreateCategoryPanel(
                    controller: _newCategoryController,
                    creating: _creatingCategory,
                    onCancel: () {
                      if (_creatingCategory) return;
                      FocusManager.instance.primaryFocus?.unfocus();
                      _newCategoryController.clear();
                      setState(() => _showCreateCategoryPanel = false);
                    },
                    onSave: _createCategoryInline,
                  ),
                )
              : const SizedBox.shrink(key: ValueKey('no-create-category')),
        ),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 160),
          child: _showCategoryManagePanel
              ? Padding(
                  key: const ValueKey('manage-category'),
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _InlineCategoryManagePanel(
                    categories: _categories,
                    saving: _savingCategoryChange,
                    onClose: () {
                      if (_savingCategoryChange) return;
                      FocusManager.instance.primaryFocus?.unfocus();
                      setState(() => _showCategoryManagePanel = false);
                    },
                    onRename: _renameCategoryInline,
                    onDelete: _deleteCategoryInline,
                    onSaveOrder: _saveCategoryOrderInline,
                  ),
                )
              : const SizedBox.shrink(key: ValueKey('no-manage-category')),
        ),
        _SectionCard(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: _CategoryFilterBar(
            categories: _categories,
            selectedKey: _selectedFilterKey,
            onSelected: (key) => setState(() => _selectedFilterKey = key),
          ),
        ),
        const SizedBox(height: 10),
        if (filteredDishes.isEmpty)
          _EmptyDishPanel(
            onAddDish: _categories.isEmpty
                ? null
                : () => _openEditPage(
                    preferredCategoryId: _preferredCategoryIdForNewDish(),
                  ),
          )
        else
          for (int index = 0; index < filteredDishes.length; index++) ...[
            _DishCard(
              dish: filteredDishes[index],
              categoryName: _categoryNameOf(filteredDishes[index]),
              onEdit: () => _openEditPage(dish: filteredDishes[index]),
              onToggleStatus: () => _toggleStatus(filteredDishes[index]),
              onDelete: () => _deleteDish(filteredDishes[index]),
            ),
            if (index != filteredDishes.length - 1) const SizedBox(height: 10),
          ],
      ],
    );
  }

  List<DishInfo> _filteredDishes() {
    if (_selectedFilterKey == _DishFilterKey.all) return _dishes;
    if (_selectedFilterKey == _DishFilterKey.offSale) {
      return _dishes.where((dish) => !dish.isOnSale).toList();
    }
    final categoryId = _categoryIdFromKey(_selectedFilterKey);
    if (categoryId == null) return _dishes;
    return _dishes.where((dish) => dish.categoryId == categoryId).toList();
  }

  String _categoryNameOf(DishInfo dish) {
    for (final category in _categories) {
      if (category.id == dish.categoryId) return category.name;
    }
    return '未分类';
  }

  int? _categoryIdFromKey(String key) {
    if (!key.startsWith(_DishFilterKey.categoryPrefix)) return null;
    return int.tryParse(key.substring(_DishFilterKey.categoryPrefix.length));
  }

  Future<CategoryInfo?> _renameCategoryInline(
    CategoryInfo category,
    String rawName,
  ) async {
    final normalizedName = rawName.trim();
    if (normalizedName.isEmpty) {
      _showSnackBar('请填写分类名称');
      return null;
    }
    if (normalizedName.length > 30) {
      _showSnackBar('分类名称不能超过30个字');
      return null;
    }
    if (normalizedName == category.name.trim()) return category;

    final duplicated = _categories.any(
      (item) => item.id != category.id && item.name.trim() == normalizedName,
    );
    if (duplicated) {
      _showSnackBar('该类别名称已存在');
      return null;
    }

    setState(() => _savingCategoryChange = true);
    try {
      final updatedCategory = await shopApi.updateCategory(
        categoryId: category.id,
        name: normalizedName,
        sortOrder: category.sortOrder,
      );
      if (!mounted) return null;

      setState(() {
        _categories =
            _categories
                .map(
                  (item) =>
                      item.id == updatedCategory.id ? updatedCategory : item,
                )
                .toList()
              ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
      });
      _showSnackBar('分类名称已更新');
      return updatedCategory;
    } on ApiException catch (error) {
      if (mounted) _showSnackBar('更新失败：${error.message}');
      return null;
    } catch (error) {
      if (mounted) _showSnackBar('更新失败：$error');
      return null;
    } finally {
      if (mounted) setState(() => _savingCategoryChange = false);
    }
  }

  Future<bool> _saveCategoryOrderInline(
    List<CategoryInfo> orderedCategories,
  ) async {
    if (orderedCategories.isEmpty) return true;

    setState(() => _savingCategoryChange = true);
    try {
      final futures = <Future<CategoryInfo>>[];
      for (int i = 0; i < orderedCategories.length; i++) {
        final category = orderedCategories[i];
        final newSortOrder = (i + 1) * 10;
        if (category.sortOrder != newSortOrder) {
          futures.add(
            shopApi.updateCategory(
              categoryId: category.id,
              name: category.name,
              sortOrder: newSortOrder,
            ),
          );
        }
      }

      if (futures.isNotEmpty) await Future.wait(futures);
      if (!mounted) return false;
      await _load(showLoading: false);
      if (mounted) _showSnackBar('分类排序已保存');
      return true;
    } on ApiException catch (error) {
      if (mounted) _showSnackBar('保存失败：${error.message}');
      return false;
    } catch (error) {
      if (mounted) _showSnackBar('保存失败：$error');
      return false;
    } finally {
      if (mounted) setState(() => _savingCategoryChange = false);
    }
  }

  Future<bool> _deleteCategoryInline(CategoryInfo category) async {
    setState(() => _savingCategoryChange = true);
    try {
      await shopApi.deleteCategory(category.id);
      if (!mounted) return false;
      setState(() {
        _categories = _categories
            .where((item) => item.id != category.id)
            .toList();
        if (_selectedFilterKey == _DishFilterKey.category(category.id)) {
          _selectedFilterKey = _DishFilterKey.all;
        }
      });
      _showSnackBar('分类已删除');
      return true;
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
      return false;
    } catch (error) {
      if (mounted) _showSnackBar(error.toString());
      return false;
    } finally {
      if (mounted) setState(() => _savingCategoryChange = false);
    }
  }

  int? _preferredCategoryIdForNewDish() {
    if (!_selectedFilterKey.startsWith(_DishFilterKey.categoryPrefix)) {
      return null;
    }
    return _categoryIdFromKey(_selectedFilterKey);
  }

  List<CategoryInfo> _categoriesForDishEdit({int? preferredCategoryId}) {
    final categories = [..._categories];
    if (preferredCategoryId == null) {
      return categories;
    }

    final index = categories.indexWhere(
      (category) => category.id == preferredCategoryId,
    );
    if (index <= 0) {
      return categories;
    }

    // DishEditPage 新增菜品时会默认使用传入分类列表的第一个分类。
    // 当当前筛选是具体分类时，把该分类临时放到第一位。
    // 当前筛选为“全部”或“已下架”时 preferredCategoryId 为 null，仍默认原本第一个分类。
    final selectedCategory = categories.removeAt(index);
    categories.insert(0, selectedCategory);
    return categories;
  }

  Future<void> _openEditPage({DishInfo? dish, int? preferredCategoryId}) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => DishEditPage(
          dish: dish,
          categories: _categoriesForDishEdit(
            preferredCategoryId: dish == null ? preferredCategoryId : null,
          ),
        ),
      ),
    );
    if (!mounted) return;
    if (changed == true) await _load(showLoading: false);
  }

  Future<void> _toggleStatus(DishInfo dish) async {
    final nextStatus = dish.isOnSale ? 'OFF_SALE' : 'ON_SALE';
    try {
      await shopApi.updateDishStatus(dish.id, nextStatus);
      await _load(showLoading: false);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } catch (error) {
      if (mounted) _showSnackBar(error.toString());
    }
  }

  Future<void> _deleteDish(DishInfo dish) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        title: const Text('删除菜品'),
        content: Text('确定删除「${dish.name}」吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消', style: TextStyle(color: _mutedColor)),
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

    try {
      await shopApi.deleteDish(dish.id);
      await _load(showLoading: false);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } catch (error) {
      if (mounted) _showSnackBar(error.toString());
    }
  }

  Future<void> _createCategoryInline() async {
    final normalizedName = _newCategoryController.text.trim();
    if (normalizedName.isEmpty) {
      _showSnackBar('请填写分类名称');
      return;
    }
    if (normalizedName.length > 30) {
      _showSnackBar('分类名称不能超过30个字');
      return;
    }
    final duplicated = _categories.any(
      (category) => category.name.trim() == normalizedName,
    );
    if (duplicated) {
      _showSnackBar('该类别名称已存在');
      return;
    }

    FocusManager.instance.primaryFocus?.unfocus();
    setState(() => _creatingCategory = true);
    try {
      final createdCategory = await shopApi.createCategory(
        name: normalizedName,
        sortOrder: (_categories.length + 1) * 10,
      );
      if (!mounted) return;

      _selectedFilterKey = _DishFilterKey.category(createdCategory.id);
      _newCategoryController.clear();
      setState(() => _showCreateCategoryPanel = false);
      await _load(showLoading: false);
      if (mounted) _showSnackBar('分类已新增');
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } catch (error) {
      if (mounted) _showSnackBar(error.toString());
    } finally {
      if (mounted) setState(() => _creatingCategory = false);
    }
  }

  void _showSnackBar(String message) {
    AppNotice.show(context, message);
  }
}

class _DishFilterKey {
  static const all = 'all';
  static const offSale = 'off_sale';
  static const categoryPrefix = 'category:';

  static String category(int id) => '$categoryPrefix$id';
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.child,
    this.padding = const EdgeInsets.all(14),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: _cardColor,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: _lineColor.withOpacity(0.7)),
      ),
      child: child,
    );
  }
}

class _CompactStatsBar extends StatelessWidget {
  const _CompactStatsBar({
    required this.dishCount,
    required this.onSaleCount,
    required this.offSaleCount,
    required this.categoryCount,
  });

  final int dishCount;
  final int onSaleCount;
  final int offSaleCount;
  final int categoryCount;

  @override
  Widget build(BuildContext context) {
    return _SectionCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        children: [
          const Icon(
            Icons.restaurant_menu_rounded,
            color: _warmOrange,
            size: 20,
          ),
          const SizedBox(width: 8),
          const Text(
            '菜品',
            style: TextStyle(
              color: _textColor,
              fontSize: 16,
              fontWeight: FontWeight.w900,
            ),
          ),
          const Spacer(),
          _CompactStat(label: '全部', value: dishCount),
          _CompactStat(label: '上架', value: onSaleCount, highlight: true),
          _CompactStat(label: '下架', value: offSaleCount),
          _CompactStat(label: '分类', value: categoryCount),
        ],
      ),
    );
  }
}

class _CompactStat extends StatelessWidget {
  const _CompactStat({
    required this.label,
    required this.value,
    this.highlight = false,
  });

  final String label;
  final int value;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 10),
      child: RichText(
        text: TextSpan(
          children: [
            TextSpan(
              text: '$value',
              style: TextStyle(
                color: highlight ? _warmOrange : _textColor,
                fontSize: 15,
                fontWeight: FontWeight.w900,
              ),
            ),
            TextSpan(
              text: ' $label',
              style: const TextStyle(
                color: _mutedColor,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CreateCategoryPanel extends StatelessWidget {
  const _CreateCategoryPanel({
    required this.controller,
    required this.creating,
    required this.onCancel,
    required this.onSave,
  });

  final TextEditingController controller;
  final bool creating;
  final VoidCallback onCancel;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return _SectionCard(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller,
              enabled: !creating,
              maxLength: 30,
              textInputAction: TextInputAction.done,
              decoration: InputDecoration(
                counterText: '',
                isDense: true,
                hintText: '新增分类名称，例如：饮品',
                filled: true,
                fillColor: _pageBgColor,
                prefixIcon: const Icon(Icons.category_outlined, size: 18),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide.none,
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: _warmOrange),
                ),
              ),
              onSubmitted: (_) {
                if (!creating) onSave();
              },
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            tooltip: '取消',
            onPressed: creating ? null : onCancel,
            icon: const Icon(Icons.close_rounded, color: _mutedColor),
          ),
          FilledButton(
            onPressed: creating ? null : onSave,
            style: FilledButton.styleFrom(
              backgroundColor: _warmOrange,
              minimumSize: const Size(56, 40),
              padding: const EdgeInsets.symmetric(horizontal: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            child: Text(creating ? '保存中' : '保存'),
          ),
        ],
      ),
    );
  }
}

class _CategoryFilterBar extends StatelessWidget {
  const _CategoryFilterBar({
    required this.categories,
    required this.selectedKey,
    required this.onSelected,
  });

  final List<CategoryInfo> categories;
  final String selectedKey;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 34,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 10),
        children: [
          _CategoryFilterChip(
            title: '全部',
            selected: selectedKey == _DishFilterKey.all,
            onTap: () => onSelected(_DishFilterKey.all),
          ),
          _CategoryFilterChip(
            title: '未上架',
            selected: selectedKey == _DishFilterKey.offSale,
            onTap: () => onSelected(_DishFilterKey.offSale),
          ),
          for (final category in categories)
            _CategoryFilterChip(
              title: category.name,
              selected: selectedKey == _DishFilterKey.category(category.id),
              onTap: () => onSelected(_DishFilterKey.category(category.id)),
            ),
        ],
      ),
    );
  }
}

class _CategoryFilterChip extends StatelessWidget {
  const _CategoryFilterChip({
    required this.title,
    required this.selected,
    required this.onTap,
  });

  final String title;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(minWidth: 58),
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
          decoration: BoxDecoration(
            color: selected ? _warmOrange : _pageBgColor,
            borderRadius: BorderRadius.circular(999),
          ),
          child: Center(
            child: Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: selected ? Colors.white : _textColor,
                fontSize: 12,
                fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _DishSkeletonList extends StatelessWidget {
  const _DishSkeletonList();

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      itemCount: 6,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (_, index) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          children: const [
            _SkeletonBox(width: 82, height: 82, borderRadius: 12),
            SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SkeletonBox(width: 150, height: 18),
                  SizedBox(height: 12),
                  _SkeletonBox(width: 100, height: 14),
                  SizedBox(height: 8),
                  _SkeletonBox(width: 210, height: 14),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyDishPanel extends StatelessWidget {
  const _EmptyDishPanel({this.onAddDish});

  final VoidCallback? onAddDish;

  @override
  Widget build(BuildContext context) {
    return _SectionCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 44),
      child: Column(
        children: [
          Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              color: _softOrange,
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Icon(
              Icons.restaurant_menu_rounded,
              size: 30,
              color: _warmOrange,
            ),
          ),
          const SizedBox(height: 14),
          const Text(
            '暂无相关菜品',
            style: TextStyle(
              color: _textColor,
              fontSize: 16,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '可以切换分类，或新增一道菜品',
            style: TextStyle(color: _mutedColor, fontSize: 13),
          ),
          if (onAddDish != null) ...[
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onAddDish,
              icon: const Icon(Icons.add_rounded, size: 18),
              label: const Text('新增菜品'),
              style: FilledButton.styleFrom(backgroundColor: _warmOrange),
            ),
          ],
        ],
      ),
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
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _DishImage(imageUrl: dish.imageUrl),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        dish.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: _textColor,
                          fontSize: 15,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    _StatusBadge(isAvailable: dish.isOnSale),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  '¥${dish.price.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: _warmOrange,
                    fontSize: 17,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 5,
                  runSpacing: 5,
                  children: [
                    _MiniTag(text: categoryName),
                    _MiniTag(text: '月售 ${dish.salesCount}'),
                    _MiniTag(text: '库存 ${dish.stock}'),
                  ],
                ),
              ],
            ),
          ),
          SizedBox(
            width: 34,
            height: 34,
            child: PopupMenuButton<String>(
              padding: EdgeInsets.zero,
              icon: const Icon(
                Icons.more_vert_rounded,
                color: _mutedColor,
                size: 20,
              ),
              color: Colors.white,
              surfaceTintColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
              onSelected: (value) {
                switch (value) {
                  case 'edit':
                    onEdit();
                    break;
                  case 'status':
                    onToggleStatus();
                    break;
                  case 'delete':
                    onDelete();
                    break;
                }
              },
              itemBuilder: (context) => [
                const PopupMenuItem(value: 'edit', child: Text('编辑菜品')),
                PopupMenuItem(
                  value: 'status',
                  child: Text(dish.isOnSale ? '下架菜品' : '重新上架'),
                ),
                const PopupMenuItem(
                  value: 'delete',
                  child: Text(
                    '删除菜品',
                    style: TextStyle(color: Colors.redAccent),
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

class _DishImage extends StatelessWidget {
  const _DishImage({this.imageUrl});

  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    final url = imageUrl;
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: url != null && url.isNotEmpty
          ? Image.network(
              _absoluteImageUrl(url),
              width: 68,
              height: 68,
              fit: BoxFit.cover,
              loadingBuilder: (context, child, loadingProgress) {
                if (loadingProgress == null) return child;
                return const _SkeletonBox(
                  width: 68,
                  height: 68,
                  borderRadius: 12,
                );
              },
              errorBuilder: (_, __, ___) => _placeholder(),
            )
          : _placeholder(),
    );
  }

  Widget _placeholder() {
    return Container(
      width: 68,
      height: 68,
      color: _softOrange,
      child: const Icon(
        Icons.restaurant_menu_rounded,
        color: _warmOrange,
        size: 28,
      ),
    );
  }
}

class _MiniTag extends StatelessWidget {
  const _MiniTag({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
      decoration: BoxDecoration(
        color: _pageBgColor,
        borderRadius: BorderRadius.circular(5),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: _mutedColor,
          fontSize: 10,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.isAvailable});

  final bool isAvailable;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(
        color: isAvailable ? _softOrange : const Color(0xFFF5F5F5),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        isAvailable ? '上架' : '下架',
        style: TextStyle(
          color: isAvailable ? _warmOrange : _mutedColor,
          fontSize: 10,
          fontWeight: FontWeight.w800,
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
    return _SectionCard(
      padding: const EdgeInsets.all(18),
      child: Column(
        children: [
          Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(
              color: _softOrange,
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Icon(
              Icons.wifi_off_outlined,
              color: _warmOrange,
              size: 28,
            ),
          ),
          const SizedBox(height: 12),
          const Text(
            '加载失败',
            style: TextStyle(
              color: _textColor,
              fontSize: 16,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: _mutedColor, fontSize: 13),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: onRetry,
            style: FilledButton.styleFrom(backgroundColor: _warmOrange),
            child: const Text('重试'),
          ),
        ],
      ),
    );
  }
}

String _absoluteImageUrl(String url) {
  if (url.startsWith('http')) return url;
  final api = Uri.parse(
    const String.fromEnvironment(
      'SHOP_API_BASE_URL',
      defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
    ),
  );
  return api.replace(path: url).toString();
}

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

class _SkeletonBoxState extends State<_SkeletonBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Color?> _colorTween;

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

class _InlineCategoryManagePanel extends StatefulWidget {
  const _InlineCategoryManagePanel({
    required this.categories,
    required this.saving,
    required this.onClose,
    required this.onRename,
    required this.onDelete,
    required this.onSaveOrder,
  });

  final List<CategoryInfo> categories;
  final bool saving;
  final VoidCallback onClose;
  final Future<CategoryInfo?> Function(CategoryInfo category, String newName)
  onRename;
  final Future<bool> Function(CategoryInfo category) onDelete;
  final Future<bool> Function(List<CategoryInfo> orderedCategories) onSaveOrder;

  @override
  State<_InlineCategoryManagePanel> createState() =>
      _InlineCategoryManagePanelState();
}

class _InlineCategoryManagePanelState
    extends State<_InlineCategoryManagePanel> {
  late List<CategoryInfo> _localCategories;
  int? _editingCategoryId;
  TextEditingController? _editingController;

  @override
  void initState() {
    super.initState();
    _localCategories = _sortedCopy(widget.categories);
  }

  @override
  void didUpdateWidget(covariant _InlineCategoryManagePanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_editingCategoryId == null && !widget.saving) {
      _localCategories = _sortedCopy(widget.categories);
    }
  }

  @override
  void dispose() {
    _editingController?.dispose();
    super.dispose();
  }

  List<CategoryInfo> _sortedCopy(List<CategoryInfo> categories) {
    return [...categories]..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  }

  void _startEdit(CategoryInfo category) {
    if (widget.saving) return;
    _editingController?.dispose();
    setState(() {
      _editingCategoryId = category.id;
      _editingController = TextEditingController(text: category.name);
    });
  }

  void _cancelEdit() {
    FocusManager.instance.primaryFocus?.unfocus();
    _editingController?.dispose();
    setState(() {
      _editingCategoryId = null;
      _editingController = null;
    });
  }

  Future<void> _submitEdit(CategoryInfo category) async {
    if (widget.saving) return;
    final controller = _editingController;
    if (controller == null) return;

    final newName = controller.text.trim();
    FocusManager.instance.primaryFocus?.unfocus();

    // 等键盘先退出，不销毁路由，避免输入法回收阶段触发断言。
    await Future<void>.delayed(const Duration(milliseconds: 120));
    if (!mounted) return;

    final updated = await widget.onRename(category, newName);
    if (!mounted || updated == null) return;

    final index = _localCategories.indexWhere((item) => item.id == updated.id);
    setState(() {
      if (index >= 0) _localCategories[index] = updated;
      _editingCategoryId = null;
      _editingController?.dispose();
      _editingController = null;
    });
  }

  Future<void> _delete(CategoryInfo category) async {
    if (widget.saving) return;
    final deleted = await widget.onDelete(category);
    if (!mounted || !deleted) return;
    setState(() {
      _localCategories.removeWhere((item) => item.id == category.id);
      if (_editingCategoryId == category.id) {
        _editingCategoryId = null;
        _editingController?.dispose();
        _editingController = null;
      }
    });
  }

  Future<void> _saveOrder() async {
    if (widget.saving) return;
    FocusManager.instance.primaryFocus?.unfocus();
    final saved = await widget.onSaveOrder(_localCategories);
    if (!mounted || !saved) return;
    setState(() => _localCategories = _sortedCopy(widget.categories));
  }

  @override
  Widget build(BuildContext context) {
    final listHeight = (_localCategories.length * 62.0)
        .clamp(96.0, 360.0)
        .toDouble();

    return _SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: _softOrange,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(
                  Icons.sort_rounded,
                  color: _warmOrange,
                  size: 20,
                ),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '管理分类',
                      style: TextStyle(
                        color: _textColor,
                        fontSize: 16,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    SizedBox(height: 2),
                    Text(
                      '拖拽排序，点击铅笔可直接改名',
                      style: TextStyle(color: _mutedColor, fontSize: 12),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: widget.saving ? null : widget.onClose,
                icon: const Icon(Icons.close_rounded, color: _mutedColor),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (_localCategories.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(
                child: Text('暂无分类', style: TextStyle(color: _mutedColor)),
              ),
            )
          else
            SizedBox(
              height: listHeight,
              child: ReorderableListView.builder(
                buildDefaultDragHandles: false,
                itemCount: _localCategories.length,
                onReorder: widget.saving
                    ? (_, __) {}
                    : (oldIndex, newIndex) {
                        setState(() {
                          if (newIndex > oldIndex) newIndex -= 1;
                          final item = _localCategories.removeAt(oldIndex);
                          _localCategories.insert(newIndex, item);
                        });
                      },
                proxyDecorator: (child, index, animation) {
                  return Material(
                    color: Colors.transparent,
                    child: ScaleTransition(
                      scale: Tween<double>(
                        begin: 1,
                        end: 1.02,
                      ).animate(animation),
                      child: child,
                    ),
                  );
                },
                itemBuilder: (context, index) {
                  final category = _localCategories[index];
                  final isEditing = _editingCategoryId == category.id;
                  return _CategoryManageRow(
                    key: ValueKey(category.id),
                    index: index,
                    category: category,
                    isEditing: isEditing,
                    saving: widget.saving,
                    controller: isEditing ? _editingController : null,
                    onStartEdit: () => _startEdit(category),
                    onCancelEdit: _cancelEdit,
                    onSubmitEdit: () => _submitEdit(category),
                    onDelete: () => _delete(category),
                  );
                },
              ),
            ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: widget.saving ? null : widget.onClose,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: _textColor,
                    side: const BorderSide(color: _lineColor),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  child: const Text('关闭'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton.icon(
                  onPressed: widget.saving ? null : _saveOrder,
                  style: FilledButton.styleFrom(
                    backgroundColor: _warmOrange,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  icon: widget.saving
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.save_outlined, size: 18),
                  label: Text(widget.saving ? '处理中...' : '保存排序'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _CategoryManageRow extends StatelessWidget {
  const _CategoryManageRow({
    super.key,
    required this.index,
    required this.category,
    required this.isEditing,
    required this.saving,
    required this.controller,
    required this.onStartEdit,
    required this.onCancelEdit,
    required this.onSubmitEdit,
    required this.onDelete,
  });

  final int index;
  final CategoryInfo category;
  final bool isEditing;
  final bool saving;
  final TextEditingController? controller;
  final VoidCallback onStartEdit;
  final VoidCallback onCancelEdit;
  final VoidCallback onSubmitEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFFFAFAFA),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _lineColor),
      ),
      child: isEditing ? _buildEditingRow() : _buildNormalRow(),
    );
  }

  Widget _buildNormalRow() {
    return Row(
      children: [
        ReorderableDragStartListener(
          index: index,
          enabled: !saving,
          child: Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: _pageBgColor,
              borderRadius: BorderRadius.circular(9),
            ),
            child: const Icon(
              Icons.drag_indicator_rounded,
              color: _mutedColor,
              size: 20,
            ),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            category.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: _textColor,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        IconButton(
          tooltip: '重命名',
          onPressed: saving ? null : onStartEdit,
          icon: const Icon(Icons.edit_outlined, color: _warmOrange, size: 20),
        ),
        IconButton(
          tooltip: '删除',
          onPressed: saving ? null : onDelete,
          icon: const Icon(
            Icons.delete_outline,
            color: Colors.redAccent,
            size: 20,
          ),
        ),
      ],
    );
  }

  Widget _buildEditingRow() {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: controller,
            enabled: !saving,
            autofocus: false,
            maxLength: 30,
            textInputAction: TextInputAction.done,
            decoration: InputDecoration(
              isDense: true,
              counterText: '',
              hintText: '分类名称',
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _lineColor),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _lineColor),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _warmOrange),
              ),
            ),
            onSubmitted: (_) => onSubmitEdit(),
          ),
        ),
        const SizedBox(width: 8),
        IconButton(
          tooltip: '取消',
          onPressed: saving ? null : onCancelEdit,
          icon: const Icon(Icons.close_rounded, color: _mutedColor),
        ),
        FilledButton(
          onPressed: saving ? null : onSubmitEdit,
          style: FilledButton.styleFrom(
            backgroundColor: _warmOrange,
            minimumSize: const Size(58, 38),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
          child: Text(saving ? '保存中' : '保存'),
        ),
      ],
    );
  }
}
