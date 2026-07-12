import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../widgets/app_notice.dart';

const _orange = Color(0xFFF28C28);
const _pageBackground = Color(0xFFF5F6F8);
const _muted = Color(0xFF77716B);

class ChatUserDetailPage extends StatefulWidget {
  const ChatUserDetailPage({
    super.key,
    required this.userId,
    required this.nickname,
    this.avatarUrl,
    this.phone,
    this.statusText,
    this.canBlock = true,
  });

  final int userId;
  final String nickname;
  final String? avatarUrl;
  final String? phone;
  final String? statusText;
  final bool canBlock;

  @override
  State<ChatUserDetailPage> createState() => _ChatUserDetailPageState();
}

class _ChatUserDetailPageState extends State<ChatUserDetailPage> {
  ChatBlockState? _block;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    if (widget.canBlock) _loadBlock();
  }

  Future<void> _loadBlock() async {
    try {
      final value = await shopApi.fetchChatBlock(widget.userId);
      if (mounted) setState(() => _block = value);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _toggleBlock() async {
    final current = _block;
    if (current == null || _saving) return;
    final willBlock = !current.blockedByMe;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(willBlock ? '拉黑用户' : '取消拉黑'),
        content: Text(
          willBlock ? '拉黑后双方将无法继续聊天，但不会影响正常点餐。' : '取消拉黑后，双方可以恢复聊天。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(willBlock ? '确认拉黑' : '恢复聊天'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _saving = true);
    try {
      if (willBlock) {
        await shopApi.blockChatUser(widget.userId);
      } else {
        await shopApi.unblockChatUser(widget.userId);
      }
      if (!mounted) return;
      setState(
        () => _block = ChatBlockState(
          userId: widget.userId,
          blockedByMe: willBlock,
          blockedMe: current.blockedMe,
        ),
      );
      AppNotice.show(context, willBlock ? '已拉黑该用户' : '已取消拉黑');
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final avatar = widget.avatarUrl?.isNotEmpty == true
        ? ApiConfig.resolveImageUrl(widget.avatarUrl!)
        : null;
    return Scaffold(
      backgroundColor: _pageBackground,
      appBar: AppBar(title: const Text('用户资料')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              children: [
                CircleAvatar(
                  radius: 38,
                  backgroundColor: const Color(0xFFFFF4E8),
                  foregroundImage: avatar == null ? null : NetworkImage(avatar),
                  child: avatar == null
                      ? const Icon(
                          Icons.person_outline,
                          color: _orange,
                          size: 34,
                        )
                      : null,
                ),
                const SizedBox(height: 12),
                Text(
                  widget.nickname,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (widget.statusText?.isNotEmpty == true) ...[
                  const SizedBox(height: 4),
                  Text(
                    widget.statusText!,
                    style: const TextStyle(color: _muted),
                  ),
                ],
                const SizedBox(height: 20),
                _InfoRow(label: '用户编号', value: '#${widget.userId}'),
                _InfoRow(
                  label: '联系电话',
                  value: widget.phone?.isNotEmpty == true
                      ? widget.phone!
                      : '未填写',
                ),
              ],
            ),
          ),
          if (widget.canBlock) ...[
            const SizedBox(height: 12),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: ListTile(
                leading: Icon(
                  _block?.blockedByMe == true
                      ? Icons.person_add_alt_outlined
                      : Icons.block_outlined,
                  color: _block?.blockedByMe == true
                      ? _orange
                      : Colors.redAccent,
                ),
                title: Text(_block?.blockedByMe == true ? '取消拉黑' : '拉黑用户'),
                subtitle: const Text('只限制聊天，不影响其他店铺功能'),
                trailing: _block == null || _saving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.chevron_right),
                onTap: _block == null || _saving ? null : _toggleBlock,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(
      children: [
        SizedBox(
          width: 88,
          child: Text(label, style: const TextStyle(color: _muted)),
        ),
        Expanded(child: Text(value, textAlign: TextAlign.right)),
      ],
    ),
  );
}
