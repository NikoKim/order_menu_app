import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';

class FeedbackManagePage extends StatefulWidget {
  const FeedbackManagePage({super.key});

  @override
  State<FeedbackManagePage> createState() => _FeedbackManagePageState();
}

class _FeedbackManagePageState extends State<FeedbackManagePage> {
  List<FeedbackInfo> _feedback = const [];
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
      final result = await shopApi.fetchFeedback(pageSize: 100);
      if (mounted) {
        setState(() => _feedback = result.records);
      }
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
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: textColor,
        elevation: 0,
        title: const Text('留言建议'),
      ),
      body: SafeArea(
        child: RefreshIndicator(
          color: warmOrange,
          onRefresh: _load,
          child: _buildBody(),
        ),
      ),
    );
  }

  Widget _buildBody() {
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
    if (_feedback.isEmpty) {
      return ListView(
        children: [
          const SizedBox(height: 220),
          const Center(
            child: Text('暂无留言', style: TextStyle(color: mutedColor)),
          ),
        ],
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: _feedback.length,
      separatorBuilder: (context, index) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final feedback = _feedback[index];
        return _FeedbackCard(
          feedback: feedback,
          onReply: () => _reply(feedback),
        );
      },
    );
  }

  Future<void> _reply(FeedbackInfo feedback) async {
    final controller = TextEditingController(text: feedback.reply ?? '');
    final submitted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('回复留言'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 4,
          decoration: const InputDecoration(hintText: '请输入回复内容'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('发送'),
          ),
        ],
      ),
    );
    if (submitted != true) {
      return;
    }
    final reply = controller.text.trim();
    if (reply.isEmpty) {
      _showSnackBar('请填写回复内容');
      return;
    }

    try {
      await shopApi.replyFeedback(feedbackId: feedback.id, reply: reply);
      await _load();
      if (mounted) {
        _showSnackBar('已回复');
      }
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

class _FeedbackCard extends StatelessWidget {
  const _FeedbackCard({required this.feedback, required this.onReply});

  final FeedbackInfo feedback;
  final VoidCallback onReply;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: lineColor),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: softOrange,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.person_outline, color: warmOrange),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '顾客 #${feedback.userId}',
                      style: const TextStyle(
                        color: textColor,
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      feedback.createdAt.replaceFirst('T', ' '),
                      style: const TextStyle(color: mutedColor, fontSize: 12),
                    ),
                  ],
                ),
              ),
              _StatusBadge(status: feedback.status),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            feedback.content,
            style: const TextStyle(color: textColor, height: 1.5),
          ),
          if (feedback.reply != null && feedback.reply!.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: softOrange,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                '商家回复：${feedback.reply}',
                style: const TextStyle(color: textColor, height: 1.4),
              ),
            ),
          ],
          const SizedBox(height: 14),
          Align(
            alignment: Alignment.centerRight,
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: warmOrange,
                side: const BorderSide(color: warmOrange),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              onPressed: onReply,
              icon: const Icon(Icons.reply, size: 18),
              label: Text(feedback.status == 'REPLIED' ? '修改回复' : '回复'),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final replied = status == 'REPLIED';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: replied ? const Color(0xFFF5F5F5) : softOrange,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        replied ? '已回复' : '待回复',
        style: TextStyle(
          color: replied ? mutedColor : warmOrange,
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
    return Material(
      color: softOrange,
      borderRadius: BorderRadius.circular(8),
      child: ListTile(
        leading: const Icon(Icons.info_outline, color: warmOrange),
        title: Text(message),
        trailing: TextButton(onPressed: onRetry, child: const Text('重试')),
      ),
    );
  }
}
