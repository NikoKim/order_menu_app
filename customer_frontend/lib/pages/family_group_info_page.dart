import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';
import 'chat_user_detail_page.dart';

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
      if (mounted) AppNotice.show(context, e.message);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(
      title: const Text('群聊信息'),
      backgroundColor: const Color(0xFFF5F5F5),
      surfaceTintColor: Colors.transparent,
    ),
    body: FutureBuilder<FamilyGroup>(
      future: _group,
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
        final group = snapshot.data!;
        final me = AppScope.of(context).auth.user?.id;
        final ownMember = group.members
            .where((member) => member.userId == me)
            .firstOrNull;
        final active = ownMember?.memberStatus == 'ACTIVE';
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _GroupCard(
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                leading: CircleAvatar(
                  radius: 28,
                  backgroundImage: group.coverImageUrl?.isNotEmpty == true
                      ? NetworkImage(
                          resolveCustomerImageUrl(group.coverImageUrl!),
                        )
                      : null,
                  child: group.coverImageUrl?.isNotEmpty == true
                      ? null
                      : const Icon(Icons.groups_outlined),
                ),
                title: Text(
                  group.name,
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                subtitle: Text(group.isActive ? '家庭群聊' : '已解散'),
              ),
            ),
            const SizedBox(height: 12),
            _GroupCard(
              child: Column(
                children: [
                  if (group.announcement?.isNotEmpty == true)
                    ListTile(
                      leading: const Icon(Icons.campaign_outlined),
                      title: const Text('群公告'),
                      subtitle: Text(group.announcement!),
                    ),
                  if (group.announcement?.isNotEmpty == true &&
                      ownMember != null)
                    const Divider(height: 1, indent: 56),
                  if (ownMember != null)
                    SwitchListTile(
                      secondary: const Icon(Icons.notifications_off_outlined),
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
                      title: const Text('加入家庭群聊'),
                      onTap: _working
                          ? null
                          : () => _perform(() async {
                              await _api.joinFamilyGroup(group.id);
                            }),
                    ),
                  if (active)
                    ListTile(
                      leading: const Icon(Icons.exit_to_app, color: Colors.red),
                      title: const Text(
                        '退出群聊',
                        style: TextStyle(color: Colors.red),
                      ),
                      onTap: _working
                          ? null
                          : () => _perform(() async {
                              await _api.leaveFamilyGroup(group.id);
                            }),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            const Padding(
              padding: EdgeInsets.fromLTRB(4, 0, 4, 8),
              child: Text('群成员', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            _GroupCard(
              child: Column(
                children: [
                  for (
                    var index = 0;
                    index < group.members.length;
                    index++
                  ) ...[
                    ListTile(
                      leading: CircleAvatar(
                        backgroundImage:
                            group.members[index].avatarUrl?.isNotEmpty == true
                            ? NetworkImage(
                                resolveCustomerImageUrl(
                                  group.members[index].avatarUrl!,
                                ),
                              )
                            : null,
                        child:
                            group.members[index].avatarUrl?.isNotEmpty == true
                            ? null
                            : const Icon(Icons.person),
                      ),
                      title: Text(group.members[index].nickname),
                      subtitle: Text(
                        group.members[index].role == 'OWNER'
                            ? '群主'
                            : familyMemberStatusLabel(
                                group.members[index].memberStatus,
                              ),
                      ),
                      trailing: group.members[index].userId == me
                          ? null
                          : const Icon(Icons.chevron_right),
                      onTap: group.members[index].userId == me
                          ? null
                          : () => Navigator.of(context).push(
                              MaterialPageRoute(
                                builder: (_) => ChatUserDetailPage(
                                  userId: group.members[index].userId,
                                  nickname: group.members[index].nickname,
                                  avatarUrl: group.members[index].avatarUrl,
                                  shopId: group.members[index].role == 'OWNER'
                                      ? group.shopId
                                      : null,
                                ),
                              ),
                            ),
                    ),
                    if (index != group.members.length - 1)
                      const Divider(height: 1, indent: 72),
                  ],
                ],
              ),
            ),
          ],
        );
      },
    ),
  );
}

class _GroupCard extends StatelessWidget {
  const _GroupCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
    ),
    clipBehavior: Clip.antiAlias,
    child: child,
  );
}
