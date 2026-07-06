import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

class FeedbackPage extends StatefulWidget {
  const FeedbackPage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<FeedbackPage> createState() => _FeedbackPageState();
}

class _FeedbackPageState extends State<FeedbackPage> {
  final _contentController = TextEditingController();
  late Future<PageResult<Shop>> _shopsFuture;
  int? _shopId;
  String _type = '建议';
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _shopsFuture = widget.api.shops();
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

  void _showSnack(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('留言建议')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: [
          const Text(
            '你的反馈会帮助商家和我们改进体验。',
            style: TextStyle(color: pageMutedColor),
          ),
          const SizedBox(height: 18),
          FutureBuilder<PageResult<Shop>>(
            future: _shopsFuture,
            builder: (context, snapshot) {
              final shops = snapshot.data?.records ?? const <Shop>[];
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
                      value: shop.id,
                      child: Text(shop.name),
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
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: const Icon(Icons.send_outlined),
            label: Text(_submitting ? '提交中...' : '提交'),
          ),
          const AppFooter(),
        ],
      ),
    );
  }
}
