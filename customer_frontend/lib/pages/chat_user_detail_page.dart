import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';
import 'chat_detail_page.dart';

class ChatUserDetailPage extends StatefulWidget {
  const ChatUserDetailPage({
    super.key,
    required this.userId,
    required this.nickname,
    this.avatarUrl,
    this.shopId,
  });

  final int userId;
  final String nickname;
  final String? avatarUrl;
  final int? shopId;

  @override
  State<ChatUserDetailPage> createState() => _ChatUserDetailPageState();
}

class _ChatUserDetailPageState extends State<ChatUserDetailPage> {
  late final CustomerApi _api;
  late Future<ChatBlock> _relationship;
  bool _working = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _api = CustomerApi(AppScope.of(context).api);
    _relationship = _api.chatBlock(widget.userId);
  }

  void _reload() {
    setState(() => _relationship = _api.chatBlock(widget.userId));
  }

  Future<void> _toggleBlock(ChatBlock relationship) async {
    if (_working) return;
    setState(() => _working = true);
    try {
      if (relationship.blockedByMe) {
        await _api.unblockChatUser(widget.userId);
        if (mounted) AppNotice.show(context, '已取消拉黑');
      } else {
        await _api.blockChatUser(widget.userId);
        if (mounted) AppNotice.show(context, '已拉黑该用户');
      }
      if (mounted) _reload();
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<void> _startChat() async {
    if (_working) return;
    setState(() => _working = true);
    try {
      final conversation = widget.shopId == null
          ? await _api.startChatWithCustomer(widget.userId)
          : await _api.startChatWithShop(widget.shopId!);
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ChatDetailPage(conversation: conversation),
        ),
      );
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(
      title: const Text('用户资料'),
      backgroundColor: const Color(0xFFF5F5F5),
      surfaceTintColor: Colors.transparent,
    ),
    body: FutureBuilder<ChatBlock>(
      future: _relationship,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(
            child: TextButton(
              onPressed: _reload,
              child: const Text('加载失败，点击重试'),
            ),
          );
        }
        final relationship = snapshot.data!;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Container(
              padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 42,
                    backgroundImage: widget.avatarUrl?.isNotEmpty == true
                        ? NetworkImage(
                            resolveCustomerImageUrl(widget.avatarUrl!),
                          )
                        : null,
                    child: widget.avatarUrl?.isNotEmpty == true
                        ? null
                        : const Icon(Icons.person_outline, size: 42),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    widget.nickname,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (relationship.blockedMe) ...[
                    const SizedBox(height: 8),
                    const Text('对方已将您拉黑', style: TextStyle(color: Colors.grey)),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 12),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
              ),
              clipBehavior: Clip.antiAlias,
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.chat_bubble_outline),
                    title: const Text('发送消息'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: _working ? null : _startChat,
                  ),
                  const Divider(height: 1, indent: 56),
                  ListTile(
                    leading: Icon(
                      relationship.blockedByMe
                          ? Icons.person_add_alt_outlined
                          : Icons.block_outlined,
                      color: relationship.blockedByMe ? null : Colors.red,
                    ),
                    title: Text(
                      chatBlockActionLabel(relationship),
                      style: TextStyle(
                        color: relationship.blockedByMe ? null : Colors.red,
                      ),
                    ),
                    trailing: _working
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.chevron_right),
                    onTap: _working ? null : () => _toggleBlock(relationship),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    ),
  );
}

String chatBlockActionLabel(ChatBlock relationship) =>
    relationship.blockedByMe ? '取消拉黑' : '拉黑用户';
