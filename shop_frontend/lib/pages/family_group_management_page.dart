import 'dart:async';

import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../widgets/app_notice.dart';
import 'chat_user_detail_page.dart';
import 'chat_essence_page.dart';

const _orange = Color(0xFFF28C28);
const _pageBackground = Color(0xFFF5F6F8);
const _muted = Color(0xFF77716B);

class FamilyGroupManagementPage extends StatefulWidget {
  const FamilyGroupManagementPage({super.key, required this.conversationId});

  final int conversationId;

  @override
  State<FamilyGroupManagementPage> createState() =>
      _FamilyGroupManagementPageState();
}

class _FamilyGroupManagementPageState extends State<FamilyGroupManagementPage> {
  final _nameController = TextEditingController();
  final _announcementController = TextEditingController();
  FamilyGroup? _group;
  String? _error;
  bool _loading = true;
  bool _saving = false;
  StreamSubscription? _subscription;

  @override
  void initState() {
    super.initState();
    _load();
    _subscription = ShopWebSocketService.instance.messageStream.listen((event) {
      if (event.module == 'CHAT' || event.module == 'FAMILY') {
        _load(silent: true);
      }
    });
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _nameController.dispose();
    _announcementController.dispose();
    super.dispose();
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent && mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final group = await shopApi.fetchFamilyGroup(widget.conversationId);
      if (!mounted) return;
      setState(() {
        _group = group;
        _nameController.text = group.name;
        _announcementController.text = group.announcement ?? '';
        _error = null;
      });
    } on ApiException catch (error) {
      if (mounted && !silent) setState(() => _error = error.message);
    } finally {
      if (mounted && !silent) setState(() => _loading = false);
    }
  }

  Future<void> _save({bool? joinApprovalRequired}) async {
    final current = _group;
    if (current == null || _saving) return;
    final nextApproval = joinApprovalRequired ?? current.joinApprovalRequired;
    setState(() {
      _saving = true;
      _group = FamilyGroup(
        id: current.id,
        shopId: current.shopId,
        name: _nameController.text.trim(),
        status: current.status,
        members: current.members,
        coverImageUrl: current.coverImageUrl,
        announcement: _announcementController.text.trim(),
        joinApprovalRequired: nextApproval,
      );
    });
    try {
      final updated = await shopApi.updateFamilyGroup(
        current.id,
        name: _nameController.text.trim(),
        announcement: _announcementController.text.trim(),
        coverImageUrl: current.coverImageUrl,
        joinApprovalRequired: nextApproval,
      );
      if (!mounted) return;
      setState(() => _group = updated);
      AppNotice.show(context, '群聊资料已保存');
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _group = current);
        AppNotice.show(context, error.message);
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _approve(FamilyGroupMember member) async {
    _replaceMember(member, 'ACTIVE');
    try {
      await shopApi.approveFamilyGroupMember(_group!.id, member.userId);
      if (mounted) AppNotice.show(context, '已同意加入群聊');
      await _load(silent: true);
    } on ApiException catch (error) {
      _replaceMember(member, 'PENDING');
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _reject(FamilyGroupMember member) async {
    _removeLocalMember(member.userId);
    try {
      await shopApi.rejectFamilyGroupMember(_group!.id, member.userId);
      if (mounted) AppNotice.show(context, '已拒绝加入群聊');
      await _load(silent: true);
    } on ApiException catch (error) {
      await _load(silent: true);
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _remove(FamilyGroupMember member) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('移出群聊'),
        content: Text('确定将 ${member.nickname} 移出群聊吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('移出'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    _removeLocalMember(member.userId);
    try {
      await shopApi.removeFamilyGroupMember(_group!.id, member.userId);
      if (mounted) AppNotice.show(context, '已移出群成员');
      await _load(silent: true);
    } on ApiException catch (error) {
      await _load(silent: true);
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _dissolve() async {
    final group = _group;
    if (group == null || _saving) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('解散群聊'),
        content: const Text('解散后会保留历史消息，确定继续吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            child: const Text('解散群聊'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _saving = true);
    try {
      await shopApi.dissolveFamilyGroup(group.id);
      if (!mounted) return;
      AppNotice.show(context, '家庭群聊已解散');
      Navigator.pop(context, true);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _replaceMember(FamilyGroupMember member, String status) {
    final group = _group;
    if (group == null || !mounted) return;
    final updatedMember = FamilyGroupMember(
      userId: member.userId,
      nickname: member.nickname,
      role: member.role,
      memberStatus: status,
      muted: member.muted,
      avatarUrl: member.avatarUrl,
    );
    setState(
      () => _group = _copyGroup(
        group,
        members: group.members
            .map((item) => item.userId == member.userId ? updatedMember : item)
            .toList(),
      ),
    );
  }

  void _removeLocalMember(int userId) {
    final group = _group;
    if (group == null || !mounted) return;
    setState(
      () => _group = _copyGroup(
        group,
        members: group.members.where((item) => item.userId != userId).toList(),
      ),
    );
  }

  FamilyGroup _copyGroup(
    FamilyGroup group, {
    required List<FamilyGroupMember> members,
  }) => FamilyGroup(
    id: group.id,
    shopId: group.shopId,
    name: group.name,
    status: group.status,
    members: members,
    coverImageUrl: group.coverImageUrl,
    announcement: group.announcement,
    joinApprovalRequired: group.joinApprovalRequired,
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _pageBackground,
    appBar: AppBar(title: const Text('管理群聊')),
    body: RefreshIndicator(onRefresh: _load, child: _buildBody()),
  );

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator(color: _orange));
    }
    if (_error != null) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _Card(
            child: Column(
              children: [
                Text(_error!, textAlign: TextAlign.center),
                const SizedBox(height: 12),
                TextButton(onPressed: _load, child: const Text('重新加载')),
              ],
            ),
          ),
        ],
      );
    }
    final group = _group!;
    final pending = group.members
        .where((member) => member.memberStatus == 'PENDING')
        .toList();
    final active = group.members
        .where((member) => member.memberStatus != 'PENDING')
        .toList();
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      children: [
        _Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const _Title(icon: Icons.edit_outlined, text: '群聊资料'),
              const SizedBox(height: 16),
              TextField(
                controller: _nameController,
                decoration: const InputDecoration(labelText: '群聊名称'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _announcementController,
                maxLines: 3,
                decoration: const InputDecoration(labelText: '群公告'),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _saving ? null : _save,
                  icon: const Icon(Icons.save_outlined),
                  label: Text(_saving ? '保存中...' : '保存群聊资料'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _Card(
          child: SwitchListTile(
            contentPadding: EdgeInsets.zero,
            secondary: const Icon(Icons.verified_user_outlined, color: _orange),
            title: const Text('新成员加入审核'),
            subtitle: const Text('开启后，新用户需经商家同意才能加入群聊'),
            value: group.joinApprovalRequired,
            onChanged: _saving
                ? null
                : (value) => _save(joinApprovalRequired: value),
          ),
        ),
        const SizedBox(height: 12),
        _Card(
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.star_outline, color: _orange),
            title: const Text('群精华'),
            subtitle: const Text('管理群内的重要消息与多条置顶'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => ChatEssencePage(conversationId: group.id),
              ),
            ),
          ),
        ),
        if (pending.isNotEmpty) ...[
          const SizedBox(height: 12),
          _Card(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Title(
                  icon: Icons.person_add_alt_1_outlined,
                  text: '加入申请（${pending.length}）',
                ),
                const SizedBox(height: 8),
                for (final member in pending)
                  _MemberTile(
                    member: member,
                    onOpen: () => _openMember(member),
                    onApprove: () => _approve(member),
                    onReject: () => _reject(member),
                  ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 12),
        _Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _Title(icon: Icons.group_outlined, text: '群成员（${active.length}）'),
              const SizedBox(height: 8),
              if (active.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(
                    child: Text('暂无群成员', style: TextStyle(color: _muted)),
                  ),
                )
              else
                for (final member in active)
                  _MemberTile(
                    member: member,
                    onOpen: () => _openMember(member),
                    onRemove: member.role == 'OWNER'
                        ? null
                        : () => _remove(member),
                  ),
            ],
          ),
        ),
        if (group.status == 'ACTIVE') ...[
          const SizedBox(height: 12),
          _Card(
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(
                Icons.group_off_outlined,
                color: Colors.redAccent,
              ),
              title: const Text('解散家庭群聊'),
              subtitle: const Text('历史消息会继续保留'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _saving ? null : _dissolve,
            ),
          ),
        ],
      ],
    );
  }

  Future<void> _openMember(FamilyGroupMember member) =>
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => ChatUserDetailPage(
            userId: member.userId,
            nickname: member.nickname,
            avatarUrl: member.avatarUrl,
            conversationId: widget.conversationId,
            statusText: member.role == 'OWNER'
                ? '群主'
                : familyMemberStatusLabel(member.memberStatus),
            canBlock: member.role != 'OWNER',
          ),
        ),
      );
}

class _Card extends StatelessWidget {
  const _Card({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(8),
    ),
    child: child,
  );
}

class _Title extends StatelessWidget {
  const _Title({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Icon(icon, color: _orange, size: 20),
      const SizedBox(width: 8),
      Text(
        text,
        style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
      ),
    ],
  );
}

class _MemberTile extends StatelessWidget {
  const _MemberTile({
    required this.member,
    required this.onOpen,
    this.onApprove,
    this.onReject,
    this.onRemove,
  });

  final FamilyGroupMember member;
  final VoidCallback onOpen;
  final VoidCallback? onApprove;
  final VoidCallback? onReject;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    final avatar = member.avatarUrl?.isNotEmpty == true
        ? ApiConfig.resolveImageUrl(member.avatarUrl!)
        : null;
    return InkWell(
      onTap: onOpen,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: const Color(0xFFFFF4E8),
              foregroundImage: avatar == null ? null : NetworkImage(avatar),
              child: avatar == null
                  ? const Icon(Icons.person_outline, color: _orange)
                  : null,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    member.nickname,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    member.role == 'OWNER'
                        ? '群主'
                        : familyMemberStatusLabel(member.memberStatus),
                    style: const TextStyle(color: _muted, fontSize: 12),
                  ),
                ],
              ),
            ),
            if (onReject != null)
              IconButton(
                tooltip: '拒绝',
                onPressed: onReject,
                icon: const Icon(Icons.close, color: Colors.redAccent),
              ),
            if (onApprove != null)
              IconButton(
                tooltip: '同意',
                onPressed: onApprove,
                icon: const Icon(Icons.check_circle, color: Color(0xFF20A464)),
              ),
            if (onRemove != null)
              IconButton(
                tooltip: '移出群聊',
                onPressed: onRemove,
                icon: const Icon(Icons.person_remove_outlined),
              ),
          ],
        ),
      ),
    );
  }
}
