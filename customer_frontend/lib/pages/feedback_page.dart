import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import '../widgets/app_notice.dart';
import 'my_feedback_page.dart';

class FeedbackPage extends StatefulWidget {
  const FeedbackPage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<FeedbackPage> createState() => _FeedbackPageState();
}

class _FeedbackPageState extends State<FeedbackPage> {
  final _contentController = TextEditingController();
  late Future<List<MapEntry<int, String>>> _shopsFuture;
  int? _shopId;
  String _type = '建议';
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _shopsFuture = _fetchShopsAndFamilies();
  }

  Future<List<MapEntry<int, String>>> _fetchShopsAndFamilies() async {
    final Map<int, String> options = {};

    try {
      final res = await widget.api.shops();
      for (final shop in res.records) {
        options[shop.id] = shop.name;
      }
    } catch (e) {
      debugPrint('加载公开商家失败: $e');
    }

    try {
      final families = await widget.api.myFamilies();
      for (final family in families) {
        if (family.isApproved) {
          options[family.shopId] = family.shopName ?? family.familyName;
        }
      }
    } catch (e) {
      debugPrint('加载家庭商家失败: $e');
    }

    return options.entries.toList();
  }

  @override
  void dispose() {
    _contentController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final content = _contentController.text.trim();
    if (content.isEmpty) {
      _showSnack('请先填写留言内容');
      return;
    }
    setState(() => _submitting = true);
    try {
      await widget.api.submitFeedback(
        shopId: _shopId,
        content: '[$_type] $content',
      );
      _contentController.clear();
      if (!mounted) return;
      _showSnack('已提交$_type，感谢反馈');
    } catch (error) {
      _showSnack(error.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _openFeedbackHistory() {
    Navigator.of(
      context,
    ).push(MaterialPageRoute(builder: (_) => MyFeedbackPage(api: widget.api)));
  }

  void _showSnack(String message) {
    AppNotice.show(context, message);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      appBar: AppBar(
        title: const Text('留言建议'),
        backgroundColor: const Color(0xFFF7F7F7),
        foregroundColor: pageTextColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        actions: [
          TextButton.icon(
            onPressed: _openFeedbackHistory,
            icon: const Icon(Icons.history_rounded, size: 18),
            label: const Text('反馈记录'),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          children: [
            const _PageHeaderCard(
              icon: Icons.chat_bubble_outline_rounded,
              title: '你的反馈很重要',
              message: '可以选择关联商家，也可以直接给平台留言，我们会持续改进点餐体验。',
            ),
            const SizedBox(height: 12),
            _WhiteCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  FutureBuilder<List<MapEntry<int, String>>>(
                    future: _shopsFuture,
                    builder: (context, snapshot) {
                      final shops = snapshot.data ?? const [];
                      return DropdownButtonFormField<int?>(
                        initialValue: _shopId,
                        decoration: const InputDecoration(
                          labelText: '关联商家',
                          prefixIcon: Icon(Icons.storefront_outlined),
                        ),
                        items: [
                          const DropdownMenuItem<int?>(
                            value: null,
                            child: Text('不指定商家'),
                          ),
                          for (final shop in shops)
                            DropdownMenuItem<int?>(
                              value: shop.key,
                              child: Text(shop.value),
                            ),
                        ],
                        onChanged: (value) {
                          setState(() => _shopId = value);
                        },
                      );
                    },
                  ),
                  const SizedBox(height: 14),
                  SegmentedButton<String>(
                    style: ButtonStyle(
                      backgroundColor: MaterialStateProperty.resolveWith((
                        states,
                      ) {
                        if (states.contains(MaterialState.selected))
                          return warmOrange;
                        return Colors.white;
                      }),
                      foregroundColor: MaterialStateProperty.resolveWith((
                        states,
                      ) {
                        if (states.contains(MaterialState.selected))
                          return Colors.white;
                        return pageTextColor;
                      }),
                      side: MaterialStateProperty.resolveWith((states) {
                        if (states.contains(MaterialState.selected)) {
                          return const BorderSide(color: warmOrange);
                        }
                        return const BorderSide(color: pageLineColor);
                      }),
                    ),
                    segments: const [
                      ButtonSegment(value: '建议', label: Text('建议')),
                      ButtonSegment(value: '问题', label: Text('问题')),
                      ButtonSegment(value: '表扬', label: Text('表扬')),
                    ],
                    selected: {_type},
                    onSelectionChanged: (values) {
                      setState(() => _type = values.first);
                    },
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _contentController,
                    maxLines: 6,
                    maxLength: 1000,
                    decoration: const InputDecoration(
                      labelText: '留言内容',
                      alignLabelWithHint: true,
                      hintText: '例如：希望增加米饭选项，或者某道菜很好吃。',
                    ),
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: _submitting ? null : _submit,
                      icon: const Icon(Icons.send_outlined),
                      label: Text(_submitting ? '提交中...' : '提交'),
                    ),
                  ),
                ],
              ),
            ),
            const AppFooter(),
          ],
        ),
      ),
    );
  }
}

class _PageHeaderCard extends StatelessWidget {
  const _PageHeaderCard({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return _WhiteCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: softOrange,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: warmOrange, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: pageTextColor,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  message,
                  style: const TextStyle(
                    color: pageMutedColor,
                    fontSize: 13,
                    height: 1.35,
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

class _WhiteCard extends StatelessWidget {
  const _WhiteCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: child,
    );
  }
}
