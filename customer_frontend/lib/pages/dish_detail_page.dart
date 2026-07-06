import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

class DishDetailPage extends StatefulWidget {
  const DishDetailPage({
    super.key,
    required this.dish,
    required this.shop,
    this.initialQuantity = 0,
    this.onAdd,
    this.onAddItem,
    this.onRemove,
  });

  final Dish dish;
  final Shop shop;
  final int initialQuantity;
  final VoidCallback? onAdd;
  final ValueChanged<CartItem>? onAddItem;
  final VoidCallback? onRemove;

  @override
  State<DishDetailPage> createState() => _DishDetailPageState();
}

class _DishDetailPageState extends State<DishDetailPage> {
  late int _quantity;
  final Map<int, DishOptionItem> _selected = {};

  @override
  void initState() {
    super.initState();
    _quantity = widget.initialQuantity;
    for (final group in widget.dish.optionGroups) {
      if (group.options.isNotEmpty) {
        _selected[group.id] = group.options.first;
      }
    }
  }

  bool get _canAdd {
    final stock = widget.dish.stock;
    return widget.onAdd != null && (stock == null || _quantity < stock);
  }

  double get _optionAmount =>
      _selected.values.fold(0, (sum, option) => sum + option.priceDelta);

  List<int> get _selectedIds =>
      _selected.values.map((option) => option.id).toList();

  String? get _selectedText {
    if (_selected.isEmpty) return null;
    final labels = <String>[];
    for (final group in widget.dish.optionGroups) {
      final option = _selected[group.id];
      if (option != null) {
        labels.add('${group.name}:${option.name}');
      }
    }
    return labels.join('；');
  }

  void _add() {
    if (!_canAdd) return;
    if (widget.onAddItem != null && _selected.isNotEmpty) {
      widget.onAddItem!(
        CartItem(
          dish: widget.dish,
          quantity: 1,
          selectedOptionIds: _selectedIds,
          selectedOptionText: _selectedText,
          optionAmount: _optionAmount,
        ),
      );
    } else {
      widget.onAdd!();
    }
    setState(() => _quantity += 1);
  }

  void _remove() {
    if (_quantity <= 0 || widget.onRemove == null) return;
    widget.onRemove!();
    setState(() => _quantity -= 1);
  }

  @override
  Widget build(BuildContext context) {
    final dish = widget.dish;
    final shop = widget.shop;
    return Scaffold(
      appBar: AppBar(title: const Text('菜品详情')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 96),
        children: [
          Container(
            height: 190,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              color: const Color(0xFFFFE1A8),
              borderRadius: BorderRadius.circular(8),
            ),
            child: dish.imageUrl?.isNotEmpty == true
                ? Image.network(
                    resolveCustomerImageUrl(dish.imageUrl!),
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => const Icon(
                      Icons.ramen_dining,
                      size: 80,
                      color: Color(0xFF7A4314),
                    ),
                  )
                : const Icon(
                    Icons.ramen_dining,
                    size: 80,
                    color: Color(0xFF7A4314),
                  ),
          ),
          const SizedBox(height: 18),
          Text(
            dish.name,
            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(shop.name, style: const TextStyle(color: pageMutedColor)),
          const SizedBox(height: 16),
          Row(
            children: [
              Text(
                '¥${(dish.price + _optionAmount).toStringAsFixed(2)}',
                style: const TextStyle(
                  color: warmOrange,
                  fontSize: 24,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const Spacer(),
              Text(
                '月售 ${dish.salesCount ?? 0}',
                style: const TextStyle(color: pageMutedColor),
              ),
            ],
          ),
          const SizedBox(height: 18),
          for (final group in dish.optionGroups) ...[
            _Panel(
              title: group.name,
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final option in group.options)
                    ChoiceChip(
                      label: Text(
                        option.priceDelta > 0
                            ? '${option.name} +¥${option.priceDelta.toStringAsFixed(2)}'
                            : option.name,
                      ),
                      selected: _selected[group.id]?.id == option.id,
                      onSelected: (_) {
                        setState(() => _selected[group.id] = option);
                      },
                    ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
          _Panel(
            title: '菜品介绍',
            child: Text(
              dish.description?.isNotEmpty == true ? dish.description! : '暂无介绍',
              style: const TextStyle(color: pageTextColor, height: 1.6),
            ),
          ),
          const AppFooter(),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 16),
          child: _quantity > 0
              ? _DetailStepper(
                  quantity: _quantity,
                  canAdd: _canAdd,
                  onAdd: _add,
                  onRemove: _remove,
                )
              : FilledButton.icon(
                  onPressed: widget.onAdd == null ? null : _add,
                  icon: const Icon(Icons.add_shopping_cart),
                  label: Text(widget.onAdd == null ? '暂不可购买' : '加入购物车'),
                ),
        ),
      ),
    );
  }
}

class _DetailStepper extends StatelessWidget {
  const _DetailStepper({
    required this.quantity,
    required this.canAdd,
    required this.onAdd,
    required this.onRemove,
  });

  final int quantity;
  final bool canAdd;
  final VoidCallback onAdd;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      decoration: BoxDecoration(
        color: warmOrange,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            tooltip: '减少',
            onPressed: onRemove,
            icon: const Icon(Icons.remove, color: Colors.white),
          ),
          SizedBox(
            width: 72,
            child: Text(
              '$quantity',
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w900,
              ),
            ),
          ),
          IconButton(
            tooltip: '增加',
            onPressed: canAdd ? onAdd : null,
            icon: Icon(
              Icons.add,
              color: canAdd ? Colors.white : const Color(0xFFFFD8B2),
            ),
          ),
        ],
      ),
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: pageLineColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
          ),
          const SizedBox(height: 10),
          child,
        ],
      ),
    );
  }
}
