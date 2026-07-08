import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

class MyFeedbackPage extends StatefulWidget {
  const MyFeedbackPage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<MyFeedbackPage> createState() => _MyFeedbackPageState();
}

class _MyFeedbackPageState extends State<MyFeedbackPage> {
  late Future<PageResult<FeedbackEntry>> _feedbackFuture;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  void _reload() {
    setState(() {
      _feedbackFuture = widget.api.myFeedback();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      appBar: AppBar(
        title: const Text('反馈记录'),
        backgroundColor: const Color(0xFFF7F7F7),
        foregroundColor: pageTextColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: warmOrange,
        backgroundColor: Colors.white,
        child: FutureBuilder<PageResult<FeedbackEntry>>(
          future: _feedbackFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const _FeedbackSkeleton();
            }
            if (snapshot.hasError) {
              return _StateMessage(
                icon: Icons.wifi_off_outlined,
                title: '反馈记录加载失败',
                message: snapshot.error.toString(),
                actionText: '重试',
                onPressed: _reload,
              );
            }

            final feedbacks = snapshot.data?.records ?? const [];
            if (feedbacks.isEmpty) {
              return _StateMessage(
                icon: Icons.chat_bubble_outline_rounded,
                title: '暂无反馈记录',
                message: '提交过的建议、问题和表扬会显示在这里。',
                actionText: '刷新',
                onPressed: _reload,
              );
            }

            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              itemCount: feedbacks.length + 2,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                if (index == 0) {
                  return const _PageHeaderCard(
                    icon: Icons.history_rounded,
                    title: '我的反馈记录',
                    message: '下拉可以刷新记录，商家或平台回复后会在卡片中展示。',
                  );
                }
                if (index == feedbacks.length + 1) return const AppFooter();
                final feedback = feedbacks[index - 1];
                return _FeedbackCard(feedback: feedback);
              },
            );
          },
        ),
      ),
    );
  }
}

class _FeedbackCard extends StatelessWidget {
  const _FeedbackCard({required this.feedback});

  final FeedbackEntry feedback;

  @override
  Widget build(BuildContext context) {
    final isReplied = feedback.reply != null && feedback.reply!.isNotEmpty;
    final type = _feedbackType(feedback.content);
    final body = _feedbackBody(feedback.content);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Row(
                  children: [
                    const Icon(Icons.schedule_rounded, color: warmOrange, size: 14),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        _formatTime(feedback.createdAt),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: pageMutedColor, fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              if (type != null) ...[
                _TypeBadge(text: type),
                const SizedBox(width: 6),
              ],
              _StatusBadge(isReplied: isReplied),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            body,
            style: const TextStyle(
              fontSize: 15,
              color: pageTextColor,
              height: 1.5,
              fontWeight: FontWeight.w600,
            ),
          ),
          if (isReplied) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: softOrange,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.storefront_outlined, color: warmOrange, size: 15),
                      SizedBox(width: 4),
                      Text(
                        '商家回复',
                        style: TextStyle(
                          color: warmOrange,
                          fontSize: 13,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    feedback.reply!,
                    style: const TextStyle(
                      color: pageTextColor,
                      fontSize: 14,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  String? _feedbackType(String value) {
    if (!value.startsWith('[')) return null;
    final end = value.indexOf(']');
    if (end <= 1) return null;
    return value.substring(1, end);
  }

  String _feedbackBody(String value) {
    if (!value.startsWith('[')) return value;
    final end = value.indexOf(']');
    if (end <= 1 || end + 1 >= value.length) return value;
    return value.substring(end + 1).trimLeft();
  }

  String _formatTime(String value) {
    if (value.isEmpty) return '';
    final formatted = value.replaceFirst('T', ' ');
    return formatted.length > 16 ? formatted.substring(0, 16) : formatted;
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.isReplied});

  final bool isReplied;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isReplied ? const Color(0xFFCFE8C9) : const Color(0xFFF7F7F7),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        isReplied ? '已回复' : '待处理',
        style: TextStyle(
          color: isReplied ? const Color(0xFF388E3C) : pageMutedColor,
          fontSize: 11,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

class _TypeBadge extends StatelessWidget {
  const _TypeBadge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: softOrange,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: warmOrange,
          fontSize: 11,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

class _FeedbackSkeleton extends StatelessWidget {
  const _FeedbackSkeleton();

  @override
  Widget build(BuildContext context) {
    const skeletonColor = Color(0xFFFFF4E8);
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      itemCount: 5,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        if (index == 0) {
          return const _PageHeaderCard(
            icon: Icons.history_rounded,
            title: '我的反馈记录',
            message: '下拉可以刷新记录，商家或平台回复后会在卡片中展示。',
          );
        }
        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(height: 14, width: 150, color: skeletonColor),
                  const Spacer(),
                  Container(
                    width: 54,
                    height: 22,
                    decoration: BoxDecoration(
                      color: skeletonColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Container(height: 16, width: double.infinity, color: skeletonColor),
              const SizedBox(height: 8),
              Container(height: 16, width: 220, color: skeletonColor),
              const SizedBox(height: 12),
              Container(
                height: 54,
                width: double.infinity,
                decoration: BoxDecoration(
                  color: skeletonColor,
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _StateMessage extends StatelessWidget {
  const _StateMessage({
    required this.icon,
    required this.title,
    required this.message,
    required this.actionText,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionText;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        const _PageHeaderCard(
          icon: Icons.history_rounded,
          title: '我的反馈记录',
          message: '下拉可以刷新记录，商家或平台回复后会在卡片中展示。',
        ),
        const SizedBox(height: 12),
        _WhiteCard(
          child: Column(
            children: [
              Icon(icon, color: Colors.grey[400], size: 56),
              const SizedBox(height: 16),
              Text(
                title,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                  color: pageTextColor,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: pageMutedColor, fontSize: 13),
              ),
              const SizedBox(height: 20),
              FilledButton.tonal(onPressed: onPressed, child: Text(actionText)),
            ],
          ),
        ),
        const AppFooter(),
      ],
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
