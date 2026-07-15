import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';

List<ChatConversation> eligibleShopShareTargets(
  Iterable<ChatConversation> conversations, {
  required bool familyShop,
  required int? familyConversationId,
  required Set<int> familyMemberIds,
}) => conversations.where((conversation) {
  if (!conversation.isActive) return false;
  if (!conversation.isDirect) {
    return familyConversationId != null &&
        conversation.id == familyConversationId;
  }
  return !familyShop || familyMemberIds.contains(conversation.directUserId);
}).toList();

class ShopShareTarget {
  const ShopShareTarget({
    required this.title,
    this.avatarUrl,
    this.conversation,
    this.userId,
  }) : assert(conversation != null || userId != null);

  final String title;
  final String? avatarUrl;
  final ChatConversation? conversation;
  final int? userId;

  Object get key => conversation?.id ?? 'user:$userId';
  bool get isDirect => conversation?.isDirect ?? true;
}

List<ShopShareTarget> buildShopShareTargets(
  Iterable<ChatConversation> conversations, {
  required bool familyShop,
  required FamilyGroup? familyGroup,
  required int? currentUserId,
}) {
  final active = conversations.where((item) => item.isActive).toList();
  if (!familyShop) {
    return active
        .where((item) => item.isDirect)
        .map(
          (item) => ShopShareTarget(
            title: item.title,
            avatarUrl: item.imageUrl,
            conversation: item,
          ),
        )
        .toList();
  }
  if (familyGroup == null) return const [];
  final targets = <ShopShareTarget>[];
  final groupConversation = active
      .where((item) => !item.isDirect && item.id == familyGroup.id)
      .firstOrNull;
  if (groupConversation != null) {
    targets.add(
      ShopShareTarget(
        title: groupConversation.title,
        avatarUrl: groupConversation.imageUrl,
        conversation: groupConversation,
      ),
    );
  }
  final directByUserId = <int, ChatConversation>{
    for (final item in active.where((item) => item.isDirect))
      if (item.directUserId != null) item.directUserId!: item,
  };
  for (final member in familyGroup.members) {
    if (member.memberStatus != 'ACTIVE' || member.userId == currentUserId) {
      continue;
    }
    final conversation = directByUserId[member.userId];
    targets.add(
      ShopShareTarget(
        title: member.nickname,
        avatarUrl: member.avatarUrl,
        conversation: conversation,
        userId: member.userId,
      ),
    );
  }
  return targets;
}

Future<ChatConversation> resolveShopShareConversation(
  CustomerApi api,
  ShopShareTarget target,
) => target.conversation != null
    ? Future.value(target.conversation!)
    : api.startChatWithCustomer(target.userId!);

class ShopSharePage extends StatefulWidget {
  const ShopSharePage({super.key, required this.shop});
  final Shop shop;

  @override
  State<ShopSharePage> createState() => _ShopSharePageState();
}

class _ShopSharePageState extends State<ShopSharePage> {
  late CustomerApi _api;
  late Future<List<ShopShareTarget>> _future;
  Object? _sendingKey;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _api = CustomerApi(AppScope.of(context).api);
    _future = _loadEligibleGroups();
  }

  Future<List<ShopShareTarget>> _loadEligibleGroups() async {
    final currentUserId = AppScope.of(context).auth.user?.id;
    final conversations = await _api.conversations();
    final familyShop = widget.shop.visibilityType == 'FAMILY';
    FamilyGroup? familyGroup;
    if (familyShop) {
      try {
        familyGroup = await _api.familyGroupByShop(widget.shop.id);
      } catch (_) {}
    }
    return buildShopShareTargets(
      conversations,
      familyShop: familyShop,
      familyGroup: familyGroup,
      currentUserId: currentUserId,
    );
  }

  Future<void> _share(ShopShareTarget target) async {
    if (_sendingKey != null) return;
    setState(() => _sendingKey = target.key);
    try {
      final conversation = await resolveShopShareConversation(_api, target);
      await _api.shareShop(
        conversationId: conversation.id,
        shopId: widget.shop.id,
      );
      if (!mounted) return;
      AppNotice.show(context, '已分享店铺');
      Navigator.pop(context, true);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _sendingKey = null);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(
      title: const Text('分享店铺'),
      backgroundColor: const Color(0xFFF5F5F5),
      surfaceTintColor: Colors.transparent,
    ),
    body: FutureBuilder<List<ShopShareTarget>>(
      future: _future,
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          if (snapshot.hasError) return const Center(child: Text('会话加载失败'));
          return const Center(child: CircularProgressIndicator());
        }
        final conversations = snapshot.data!;
        if (conversations.isEmpty) {
          return const Center(child: Text('该店铺暂无可分享会话'));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: conversations.length,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (_, index) {
            final target = conversations[index];
            return Material(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              clipBehavior: Clip.antiAlias,
              child: ListTile(
                leading: CircleAvatar(
                  backgroundImage: target.avatarUrl?.isNotEmpty == true
                      ? NetworkImage(resolveCustomerImageUrl(target.avatarUrl!))
                      : null,
                  child: target.avatarUrl?.isNotEmpty == true
                      ? null
                      : Icon(
                          target.isDirect
                              ? Icons.person_outline
                              : Icons.groups_outlined,
                        ),
                ),
                title: Text(target.title),
                subtitle: Text(widget.shop.name),
                trailing: _sendingKey == target.key
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send_outlined),
                onTap: () => _share(target),
              ),
            );
          },
        );
      },
    ),
  );
}
