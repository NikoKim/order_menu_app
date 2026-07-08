import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';

const Color _warmOrange = Color(0xFFF28C28);
const Color _softOrange = Color(0xFFFFF4E8);
const Color _pageBgColor = Color(0xFFF5F6F8);
const Color _lineColor = Color(0xFFF0E7DE);
const Color _textColor = Color(0xFF242424);
const Color _mutedColor = Color(0xFF77716B);

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
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _pageBgColor,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
        title: const Text('留言建议', style: TextStyle(fontWeight: FontWeight.w700)),
      ),
      body: SafeArea(
        child: RefreshIndicator(
          color: _warmOrange,
          backgroundColor: Colors.white,
          onRefresh: _load,
          child: _buildBody(),
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: 4,
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (_, __) => Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: const [
              Row(
                children: [
                  _SkeletonBox(width: 40, height: 40, borderRadius: 20),
                  SizedBox(width: 12),
                  _SkeletonBox(width: 100, height: 16),
                  Spacer(),
                  _SkeletonBox(width: 50, height: 24, borderRadius: 6),
                ],
              ),
              SizedBox(height: 16),
              _SkeletonBox(width: double.infinity, height: 14),
              SizedBox(height: 8),
              _SkeletonBox(width: 200, height: 14),
            ],
          ),
        ),
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
          const SizedBox(height: 180),
          Center(
            child: Column(
              children: const [
                Icon(Icons.mark_chat_read_outlined, size: 54, color: Color(0xFFE0E0E0)),
                SizedBox(height: 16),
                Text('暂无顾客留言', style: TextStyle(color: _mutedColor)),
              ],
            ),
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
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        content: SizedBox(
          width: double.maxFinite,
          child: TextField(
            controller: controller,
            maxLines: 4,
            decoration: InputDecoration(
              hintText: '请输入回复内容',
              hintStyle: const TextStyle(color: Color(0xFFB8AFA7)),
              filled: true,
              fillColor: const Color(0xFFF9FAFB),
              contentPadding: const EdgeInsets.all(12),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: Color(0xFFE8E8E8)),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: _warmOrange),
              ),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消', style: TextStyle(color: _mutedColor)),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: _warmOrange),
            child: const Text('发送'),
          ),
        ],
      ),
    );

    final reply = controller.text.trim();
    controller.dispose();

    if (submitted != true) return;
    if (reply.isEmpty) {
      _showSnackBar('请填写回复内容');
      return;
    }

    try {
      await shopApi.replyFeedback(feedbackId: feedback.id, reply: reply);
      await _load();
      if (mounted) _showSnackBar('已回复');
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message), behavior: SnackBarBehavior.floating));
  }
}

class _FeedbackCard extends StatelessWidget {
  const _FeedbackCard({required this.feedback, required this.onReply});

  final FeedbackInfo feedback;
  final VoidCallback onReply;

  @override
  Widget build(BuildContext context) {
    final isReplied = feedback.status == 'REPLIED';

    // 解析昵称，如果为空则回退到展示 ID
    final nickname = feedback.userNickname?.trim().isNotEmpty == true
        ? feedback.userNickname!
        : '顾客 #${feedback.userId}';

    final avatarUrl = feedback.userAvatarUrl;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.02),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              // 顾客头像区
              Container(
                width: 40,
                height: 40,
                decoration: const BoxDecoration(
                  color: _softOrange,
                  shape: BoxShape.circle,
                ),
                clipBehavior: Clip.antiAlias, // 切割成圆形
                child: avatarUrl != null && avatarUrl.isNotEmpty
                    ? Image.network(
                  _absoluteImageUrl(avatarUrl),
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => const Icon(Icons.person, color: _warmOrange, size: 20),
                )
                    : const Icon(Icons.person, color: _warmOrange, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 顾客昵称展示
                    Text(
                      nickname,
                      style: const TextStyle(color: _textColor, fontSize: 15, fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      feedback.createdAt.replaceFirst('T', ' '),
                      style: const TextStyle(color: _mutedColor, fontSize: 12),
                    ),
                  ],
                ),
              ),
              _StatusBadge(status: feedback.status),
            ],
          ),
          const SizedBox(height: 16),

          // 留言内容
          Text(
            feedback.content,
            style: const TextStyle(color: _textColor, height: 1.5, fontSize: 15),
          ),

          // 商家回复展示
          if (isReplied && feedback.reply != null && feedback.reply!.isNotEmpty) ...[
            const SizedBox(height: 16),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFFF9FAFB),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: _lineColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('商家回复', style: TextStyle(color: _warmOrange, fontSize: 12, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 4),
                  Text(feedback.reply!, style: const TextStyle(color: _textColor, height: 1.5, fontSize: 14)),
                ],
              ),
            ),
          ],
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 14),
            child: Divider(color: _lineColor, height: 1),
          ),

          // 操作按钮
          Align(
            alignment: Alignment.centerRight,
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: isReplied ? _mutedColor : _warmOrange,
                side: BorderSide(color: isReplied ? const Color(0xFFE8E8E8) : _warmOrange),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                minimumSize: const Size(80, 36),
                padding: const EdgeInsets.symmetric(horizontal: 16),
              ),
              onPressed: onReply,
              icon: Icon(isReplied ? Icons.edit_outlined : Icons.reply, size: 16),
              label: Text(isReplied ? '修改回复' : '立即回复', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
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
    final isReplied = status == 'REPLIED';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isReplied ? const Color(0xFFF5F6F8) : _softOrange,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        isReplied ? '已回复' : '待回复',
        style: TextStyle(
          color: isReplied ? _mutedColor : _warmOrange,
          fontSize: 12,
          fontWeight: FontWeight.w700,
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
      decoration: BoxDecoration(color: _softOrange, borderRadius: BorderRadius.circular(12)),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: _warmOrange),
          const SizedBox(width: 12),
          Expanded(child: Text(message, style: const TextStyle(color: _textColor))),
          TextButton(onPressed: onRetry, child: const Text('重试', style: TextStyle(color: _warmOrange))),
        ],
      ),
    );
  }
}

class _SkeletonBox extends StatefulWidget {
  final double width;
  final double height;
  final double borderRadius;

  const _SkeletonBox({required this.width, required this.height, this.borderRadius = 8});

  @override
  State<_SkeletonBox> createState() => _SkeletonBoxState();
}

class _SkeletonBoxState extends State<_SkeletonBox> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<Color?> _colorTween;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 1000))..repeat(reverse: true);
    _colorTween = ColorTween(begin: const Color(0xFFF0F0F0), end: const Color(0xFFE0E0E0)).animate(_controller);
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
          decoration: BoxDecoration(color: _colorTween.value, borderRadius: BorderRadius.circular(widget.borderRadius)),
        );
      },
    );
  }
}

String _absoluteImageUrl(String url) {
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  final api = Uri.parse(ApiConfig.baseUrl);
  return api.replace(path: url.startsWith('/') ? url : '/$url').toString();
}