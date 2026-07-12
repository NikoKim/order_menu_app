import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';

class OrderSharePage extends StatefulWidget {
  const OrderSharePage({super.key, required this.order, required this.api});

  final OrderDetail order;
  final CustomerApi api;

  @override
  State<OrderSharePage> createState() => _OrderSharePageState();
}

class _OrderSharePageState extends State<OrderSharePage> {
  late final Future<List<ChatConversation>> _conversations = widget.api
      .conversations();
  int? _sharingConversationId;

  Future<void> _share(ChatConversation conversation) async {
    if (_sharingConversationId != null) return;
    setState(() => _sharingConversationId = conversation.id);
    try {
      await widget.api.shareOrder(
        conversationId: conversation.id,
        orderId: widget.order.id,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        setState(() => _sharingConversationId = null);
        AppNotice.show(context, _friendlyError(error));
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(
      title: const Text('分享订单'),
      backgroundColor: const Color(0xFFF5F5F5),
      foregroundColor: pageTextColor,
      surfaceTintColor: Colors.transparent,
    ),
    body: FutureBuilder<List<ChatConversation>>(
      future: _conversations,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return const Center(child: Text('会话加载失败'));
        }
        final conversations = snapshot.data ?? const [];
        if (conversations.isEmpty) {
          return const Center(child: Text('暂无可分享的会话'));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: conversations.length,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (context, index) {
            final item = conversations[index];
            final sharing = _sharingConversationId == item.id;
            return Material(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              clipBehavior: Clip.antiAlias,
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                leading: CircleAvatar(
                  backgroundImage: item.imageUrl?.isNotEmpty == true
                      ? NetworkImage(resolveCustomerImageUrl(item.imageUrl!))
                      : null,
                  child: item.imageUrl?.isNotEmpty == true
                      ? null
                      : Icon(item.isDirect ? Icons.person : Icons.groups),
                ),
                title: Text(item.title),
                subtitle: Text(item.isDirect ? '私聊' : '家庭群聊'),
                trailing: sharing
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send_outlined),
                onTap: sharing ? null : () => _share(item),
              ),
            );
          },
        );
      },
    ),
  );
}

String _friendlyError(Object error) {
  if (error is ApiException) return error.message;
  return '分享失败，请稍后重试';
}
