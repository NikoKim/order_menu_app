import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';

class FamilyGroupInfoPage extends StatefulWidget {
  const FamilyGroupInfoPage({
    super.key,
    required this.conversationId,
    this.shopId,
  });
  final int conversationId;
  final int? shopId;
  @override
  State<FamilyGroupInfoPage> createState() => _FamilyGroupInfoPageState();
}

class _FamilyGroupInfoPageState extends State<FamilyGroupInfoPage> {
  late final CustomerApi _api;
  late Future<FamilyGroup> _group;
  bool _working = false;
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _api = CustomerApi(AppScope.of(context).api);
    _group = _load();
  }

  Future<FamilyGroup> _load() => widget.shopId == null
      ? _api.familyGroup(widget.conversationId)
      : _api.familyGroupByShop(widget.shopId!);
  void _reload() => setState(() => _group = _load());
  Future<void> _perform(Future<void> Function() action) async {
    setState(() => _working = true);
    try {
      await action();
      _reload();
    } on ApiException catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('群聊信息')),
    body: FutureBuilder<FamilyGroup>(
      future: _group,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done)
          return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError)
          return Center(
            child: TextButton(
              onPressed: _reload,
              child: const Text('加载失败，点击重试'),
            ),
          );
        final group = snapshot.data!;
        final me = AppScope.of(context).auth.user?.id;
        final ownMember = group.members
            .where((member) => member.userId == me)
            .firstOrNull;
        final active = ownMember?.memberStatus == 'ACTIVE';
        return ListView(
          children: [
            ListTile(
              title: Text(
                group.name,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              subtitle: Text(group.isActive ? '家庭群聊' : '已解散'),
            ),
            if (group.announcement?.isNotEmpty == true)
              ListTile(
                leading: const Icon(Icons.campaign_outlined),
                title: const Text('群公告'),
                subtitle: Text(group.announcement!),
              ),
            if (ownMember != null)
              SwitchListTile(
                title: const Text('消息免打扰'),
                value: ownMember.muted,
                onChanged: _working
                    ? null
                    : (value) => _perform(() async {
                        await _api.setFamilyGroupMuted(group.id, value);
                      }),
              ),
            if (!active && group.isActive)
              ListTile(
                leading: const Icon(Icons.group_add_outlined),
                title: const Text('申请加入家庭群聊'),
                onTap: _working
                    ? null
                    : () => _perform(() async {
                        await _api.joinFamilyGroup(group.id);
                      }),
              ),
            if (active)
              ListTile(
                leading: const Icon(Icons.exit_to_app),
                title: const Text('退出群聊'),
                onTap: _working
                    ? null
                    : () => _perform(() async {
                        await _api.leaveFamilyGroup(group.id);
                      }),
              ),
            const Divider(height: 1),
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Text('群成员', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            for (final member in group.members)
              ListTile(
                leading: const CircleAvatar(child: Icon(Icons.person)),
                title: Text(member.nickname),
                subtitle: Text(
                  member.role == 'OWNER'
                      ? '群主'
                      : member.memberStatus == 'PENDING'
                      ? '待确认'
                      : '成员',
                ),
              ),
          ],
        );
      },
    ),
  );
}
