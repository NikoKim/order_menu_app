import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import '../widgets/app_notice.dart';

class CheckoutPage extends StatefulWidget {
  const CheckoutPage({
    super.key,
    required this.shop,
    required this.items,
    required this.api,
  });

  final Shop shop;
  final List<CartItem> items;
  final CustomerApi api;

  @override
  State<CheckoutPage> createState() => _CheckoutPageState();
}

class _CheckoutPageState extends State<CheckoutPage> {
  final _remarkController = TextEditingController();
  bool _submitting = false;

  double get _total => widget.items.fold(0, (sum, item) => sum + item.subtotal);

  @override
  void dispose() {
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _submitting = true);
    try {
      final result = await widget.api.createOrder(
        shopId: widget.shop.id,
        items: widget.items,
        remark: _remarkController.text.trim(),
      );
      if (!mounted) return;
      AppNotice.show(context, '订单已提交：${result['orderNo'] ?? ''}');
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted) return;
      AppNotice.show(context, error.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('确认下单')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 120),
        children: [
          _Panel(
            child: Row(
              children: [
                const Icon(Icons.storefront, color: warmOrange),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    widget.shop.name,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _Panel(
            child: Column(
              children: [
                for (final item in widget.items)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Row(
                      children: [
                        _DishThumb(imageUrl: item.dish.imageUrl),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(item.dish.name),
                              if (item.selectedOptionText?.isNotEmpty == true)
                                Text(
                                  item.selectedOptionText!,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    color: pageMutedColor,
                                    fontSize: 11,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Text(
                          'x${item.quantity}',
                          style: const TextStyle(color: pageMutedColor),
                        ),
                        const SizedBox(width: 14),
                        Text('¥${item.subtotal.toStringAsFixed(2)}'),
                      ],
                    ),
                  ),
                const Divider(color: pageLineColor),
                Row(
                  children: [
                    const Text(
                      '合计',
                      style: TextStyle(fontWeight: FontWeight.w800),
                    ),
                    const Spacer(),
                    Text(
                      '¥${_total.toStringAsFixed(2)}',
                      style: const TextStyle(
                        color: warmOrange,
                        fontSize: 20,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _remarkController,
            maxLines: 3,
            maxLength: 500,
            decoration: const InputDecoration(
              labelText: '订单备注',
              hintText: '例如：少辣、不要香菜、餐具数量',
            ),
          ),
          const AppFooter(),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 16),
          child: FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text('提交订单 ¥${_total.toStringAsFixed(2)}'),
          ),
        ),
      ),
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({required this.child});

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
      child: child,
    );
  }
}

class _DishThumb extends StatelessWidget {
  const _DishThumb({this.imageUrl});

  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 44,
      height: 44,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: const Color(0xFFFFE1A8),
        borderRadius: BorderRadius.circular(8),
      ),
      child: imageUrl?.isNotEmpty == true
          ? Image.network(
              resolveCustomerImageUrl(imageUrl!),
              fit: BoxFit.cover,
              loadingBuilder: (context, child, progress) => progress == null
                  ? child
                  : const Center(
                      child: CircularProgressIndicator(
                        color: warmOrange,
                        strokeWidth: 2,
                      ),
                    ),
              errorBuilder: (_, _, _) => const Icon(
                Icons.restaurant_menu,
                color: warmOrange,
                size: 22,
              ),
            )
          : const Icon(Icons.restaurant_menu, color: warmOrange, size: 22),
    );
  }
}
