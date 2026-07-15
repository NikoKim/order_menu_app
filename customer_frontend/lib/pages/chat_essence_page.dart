import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';
import 'chat_detail_page.dart';

class ChatEssencePage extends StatefulWidget {
  const ChatEssencePage({super.key, required this.conversationId});
  final int conversationId;

  @override
  State<ChatEssencePage> createState() => _ChatEssencePageState();
}

class _ChatEssencePageState extends State<ChatEssencePage> {
  late CustomerApi _api;
  late Future<ChatEssenceList> _future;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _api = CustomerApi(AppScope.of(context).api);
    _future = _load();
  }

  Future<ChatEssenceList> _load() async {
    final result = await _api.chatEssences(widget.conversationId);
    if (result.items.isNotEmpty) {
      await _api.markEssencesRead(widget.conversationId);
    }
    return result;
  }

  Future<void> _toggleLike(ChatMessage message) async {
    try {
      await _api.setEssenceLiked(
        widget.conversationId,
        message.id,
        !message.essenceLikedByMe,
      );
      if (mounted) setState(() => _future = _load());
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(
      title: const Text('群精华'),
      backgroundColor: const Color(0xFFF5F5F5),
      surfaceTintColor: Colors.transparent,
    ),
    body: FutureBuilder<ChatEssenceList>(
      future: _future,
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          if (snapshot.hasError) {
            return Center(
              child: TextButton(
                onPressed: () => setState(() => _future = _load()),
                child: const Text('加载失败，点击重试'),
              ),
            );
          }
          return const Center(child: CircularProgressIndicator());
        }
        final items = snapshot.data!.items;
        if (items.isEmpty) return const Center(child: Text('暂无精华消息'));
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: items.length,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (context, index) {
            final message = items[index];
            return Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 15,
                        backgroundImage:
                            message.senderAvatarUrl?.isNotEmpty == true
                            ? NetworkImage(
                                resolveCustomerImageUrl(
                                  message.senderAvatarUrl!,
                                ),
                              )
                            : null,
                        child: message.senderAvatarUrl?.isNotEmpty == true
                            ? null
                            : const Icon(Icons.person_outline, size: 16),
                      ),
                      const SizedBox(width: 8),
                      Expanded(child: Text(message.senderNickname ?? '用户')),
                      if (message.essencePinned)
                        const _EssenceTag(icon: Icons.push_pin, text: '置顶'),
                    ],
                  ),
                  const SizedBox(height: 10),
                  if (message.isDishShare)
                    DishShareCard(message: message)
                  else if (message.isOrderShare)
                    OrderShareCard(message: message)
                  else if (message.isImageShare)
                    ChatImageCard(message: message)
                  else
                    Text(message.content),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      onPressed: () => _toggleLike(message),
                      icon: Icon(
                        message.essenceLikedByMe
                            ? Icons.favorite
                            : Icons.favorite_border,
                      ),
                      label: Text('${message.essenceLikeCount}'),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    ),
  );
}

class _EssenceTag extends StatelessWidget {
  const _EssenceTag({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
    decoration: BoxDecoration(
      color: const Color(0xFFFFE7CD),
      borderRadius: BorderRadius.circular(4),
    ),
    child: Row(
      children: [
        Icon(icon, size: 12),
        const SizedBox(width: 2),
        Text(text, style: const TextStyle(fontSize: 11)),
      ],
    ),
  );
}
