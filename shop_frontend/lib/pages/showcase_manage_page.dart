import 'package:flutter/material.dart';
import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';

class ShowcaseManagePage extends StatefulWidget {
  const ShowcaseManagePage({super.key});

  @override
  State<ShowcaseManagePage> createState() => _ShowcaseManagePageState();
}

class _ShowcaseManagePageState extends State<ShowcaseManagePage> {
  List<DishInfo> _allDishes = [];
  List<DishInfo> _selectedDishes = [];
  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final result = await shopApi.fetchDishes(status: 'ON_SALE', pageSize: 500);
      if (!mounted) return;

      setState(() {
        _allDishes = result.records;

        // 利用 recommendOrder 进行回显，大于 0 说明被设置过推荐
        final preSelected = _allDishes.where((d) => d.recommendOrder > 0).toList();
        // 按照设置的序号从小到大排序
        preSelected.sort((a, b) => a.recommendOrder.compareTo(b.recommendOrder));

        _selectedDishes = preSelected;
        _loading = false;
      });
    } catch (e) {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    // 强制校验：必须选满 8 个
    if (_selectedDishes.length != 8) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('必须选满 8 个菜品才能保存生效'), behavior: SnackBarBehavior.floating),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      final selectedIds = _selectedDishes.map((d) => d.id).toList();

      await shopApi.updateShowcaseDishes(selectedIds);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('首页展示设置已保存'), behavior: SnackBarBehavior.floating),
        );
        Navigator.pop(context); // 退出页面
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('保存失败: $e'), behavior: SnackBarBehavior.floating),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showAddDialog() {
    final unselected = _allDishes.where((d) => !_selectedDishes.contains(d)).toList();

    showModalBottomSheet(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) {
        return DraggableScrollableSheet(
          initialChildSize: 0.6,
          minChildSize: 0.4,
          maxChildSize: 0.9,
          expand: false,
          builder: (context, scrollController) {
            return unselected.isEmpty
                ? const Center(child: Text('没有可添加的在售菜品', style: TextStyle(color: mutedColor)))
                : ListView.builder(
              controller: scrollController,
              itemCount: unselected.length,
              itemBuilder: (context, index) {
                final dish = unselected[index];
                return ListTile(
                  leading: _buildThumb(dish.imageUrl),
                  title: Text(dish.name),
                  subtitle: Text('¥${dish.price.toStringAsFixed(2)}', style: const TextStyle(color: warmOrange)),
                  trailing: const Icon(Icons.add_circle_outline, color: warmOrange),
                  onTap: () {
                    if (_selectedDishes.length >= 8) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('最多只能选择 8 个菜品哦'), behavior: SnackBarBehavior.floating),
                      );
                      Navigator.pop(context);
                      return;
                    }
                    setState(() => _selectedDishes.add(dish));
                    Navigator.pop(context);
                  },
                );
              },
            );
          },
        );
      },
    );
  }

  Widget _buildThumb(String? imageUrl) {
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(color: softOrange, borderRadius: BorderRadius.circular(6)),
      clipBehavior: Clip.antiAlias,
      child: imageUrl?.isNotEmpty == true
          ? Image.network(
          imageUrl!.startsWith('http') ? imageUrl : 'https://order-menu-backend.zsj-unknown.top/api/$imageUrl',
          fit: BoxFit.cover,
          errorBuilder: (_,__,___) => const Icon(Icons.fastfood, color: warmOrange, size: 20)
      )
          : const Icon(Icons.fastfood, color: warmOrange, size: 20),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: const Text('首页展示设置'),
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: Text(_saving ? '保存中...' : '保存', style: const TextStyle(color: warmOrange, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: warmOrange))
          : Column(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            color: softOrange,
            child: Row(
              children: [
                const Icon(Icons.info_outline, color: warmOrange, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '请选择 8 个在售菜品在顾客端首页卡片展示。长按可拖拽排序。已选 ${_selectedDishes.length}/8',
                    style: const TextStyle(color: textColor, fontSize: 13),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: _selectedDishes.isEmpty
                ? const Center(child: Text('暂未选择展示菜品', style: TextStyle(color: mutedColor)))
                : ReorderableListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: _selectedDishes.length,
              onReorder: (oldIndex, newIndex) {
                setState(() {
                  if (newIndex > oldIndex) newIndex -= 1;
                  final item = _selectedDishes.removeAt(oldIndex);
                  _selectedDishes.insert(newIndex, item);
                });
              },
              itemBuilder: (context, index) {
                final dish = _selectedDishes[index];
                return Material(
                  key: ValueKey(dish.id),
                  color: Colors.transparent,
                  child: Container(
                    margin: const EdgeInsets.only(bottom: 12),
                    decoration: BoxDecoration(
                      border: Border.all(color: lineColor),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: ListTile(
                      leading: _buildThumb(dish.imageUrl),
                      title: Text(dish.name),
                      subtitle: Text('第 ${index + 1} 位展示'),
                      trailing: IconButton(
                        icon: const Icon(Icons.remove_circle_outline, color: Colors.redAccent),
                        onPressed: () {
                          setState(() => _selectedDishes.removeAt(index));
                        },
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: _selectedDishes.length < 8
          ? FloatingActionButton.extended(
        onPressed: _showAddDialog,
        backgroundColor: warmOrange,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const Text('添加展示菜品'),
      )
          : null,
    );
  }
}