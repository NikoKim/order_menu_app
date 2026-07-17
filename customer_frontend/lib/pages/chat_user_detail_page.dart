import 'package:flutter/material.dart';

import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';
import '../widgets/ui_utils.dart';
import 'chat_detail_page.dart';
import 'moment_privacy_page.dart';
import '../moment_models.dart';
import '../services/moment_api.dart';

class ChatUserDetailPage extends StatefulWidget {
  const ChatUserDetailPage({
    super.key,
    required this.userId,
    required this.nickname,
    this.avatarUrl,
    this.shopId,
    this.conversationId,
  });

  final int userId;
  final String nickname;
  final String? avatarUrl;
  final int? shopId;
  final int? conversationId;

  @override
  State<ChatUserDetailPage> createState() => _ChatUserDetailPageState();
}

class _ChatUserDetailPageState extends State<ChatUserDetailPage> {
  late CustomerApi _api;
  late Future<
    (
      ChatBlock,
      ChatUserProfile,
      ConversationPreferences?,
      MomentPrivacy,
      MomentProfileSummary?,
    )
  >
  _data;
  bool _working = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _api = CustomerApi(AppScope.of(context).api);
    _data = _load();
  }

  Future<
    (
      ChatBlock,
      ChatUserProfile,
      ConversationPreferences?,
      MomentPrivacy,
      MomentProfileSummary?,
    )
  >
  _load() async {
    final relationship = await _api.chatBlock(widget.userId);
    ChatUserProfile profile;
    try {
      profile = await _api.chatUserProfile(
        widget.userId,
        conversationId: widget.conversationId,
      );
    } catch (_) {
      profile = ChatUserProfile(
        id: widget.userId,
        nickname: widget.nickname,
        role: widget.shopId == null ? 'CUSTOMER' : 'SHOP',
        avatarUrl: widget.avatarUrl,
        shopId: widget.shopId,
      );
    }
    ConversationPreferences? preferences;
    if (widget.conversationId != null) {
      try {
        preferences = await _api.conversationPreferences(
          widget.conversationId!,
        );
      } catch (_) {}
    }
    MomentPrivacy privacy = const MomentPrivacy();
    MomentProfileSummary? momentSummary;
    try {
      privacy = await _api.momentPrivacy(widget.userId);
    } catch (_) {}
    try {
      momentSummary = await _api.momentProfileSummary(widget.userId);
    } catch (_) {}
    return (relationship, profile, preferences, privacy, momentSummary);
  }

  void _reload() {
    setState(() {
      _data = _load();
    });
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

  Future<void> _toggleMute(ConversationPreferences preferences) async {
    if (_working) return;
    setState(() => _working = true);
    try {
      await _api.setConversationMuted(
        preferences.conversationId,
        !preferences.muted,
      );
      if (mounted) _reload();
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<void> _startChat(ChatUserProfile profile) async {
    if (_working) return;
    setState(() => _working = true);
    try {
      final conversation = profile.shopId == null
          ? await _api.startChatWithCustomer(widget.userId)
          : await _api.startChatWithShop(profile.shopId!);
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

  Future<void> _updateMomentPrivacy(
    MomentPrivacy privacy, {
    bool? hideTheirMoments,
    bool? hideMineFromThem,
  }) async {
    if (_working) return;
    setState(() => _working = true);
    try {
      await _api.updateMomentPrivacyForUser(
        widget.userId,
        hideTheirMoments: hideTheirMoments,
        hideMineFromThem: hideMineFromThem,
      );
      if (mounted) _reload();
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
    body:
        FutureBuilder<
          (
            ChatBlock,
            ChatUserProfile,
            ConversationPreferences?,
            MomentPrivacy,
            MomentProfileSummary?,
          )
        >(
          future: _data,
          builder: (context, snapshot) {
            if (!snapshot.hasData) {
              if (snapshot.hasError) {
                return Center(
                  child: TextButton(
                    onPressed: _reload,
                    child: const Text('加载失败，点击重试'),
                  ),
                );
              }
              return ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Container(
                        padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
                        decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
                        child: Column(
                            children: const [
                              ShimmerBox(width: 84, height: 84, borderRadius: 42),
                              SizedBox(height: 14),
                              ShimmerBox(width: 120, height: 24, borderRadius: 4),
                              SizedBox(height: 10),
                              ShimmerBox(width: 200, height: 16, borderRadius: 4),
                            ]
                        )
                    ),
                    const SizedBox(height: 12),
                    const ShimmerBox(width: double.infinity, height: 80, borderRadius: 12),
                    const SizedBox(height: 12),
                    const ShimmerBox(width: double.infinity, height: 200, borderRadius: 12),
                  ]
              );
            }
            final (
              relationship,
              profile,
              preferences,
              momentPrivacy,
              momentSummary,
            ) = snapshot.data!;
            final avatar = profile.avatarUrl ?? widget.avatarUrl;
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    vertical: 28,
                    horizontal: 20,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    children: [
                      CircleAvatar(
                        radius: 42,
                        backgroundImage: avatar?.isNotEmpty == true
                            ? NetworkImage(resolveCustomerImageUrl(avatar!))
                            : null,
                        child: avatar?.isNotEmpty == true
                            ? null
                            : const Icon(Icons.person_outline, size: 42),
                      ),
                      const SizedBox(height: 14),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Flexible(
                            child: Text(
                              profile.nickname,
                              style: Theme.of(context).textTheme.titleLarge
                                  ?.copyWith(fontWeight: FontWeight.w600),
                            ),
                          ),
                          if (profile.isMerchant) ...[
                            const SizedBox(width: 8),
                            const _MerchantBadge(),
                          ],
                        ],
                      ),
                      if (profile.phone?.isNotEmpty == true) ...[
                        const SizedBox(height: 10),
                        Text('手机号 ${profile.phone}'),
                      ],
                      if (profile.joinedFamilies.isNotEmpty) ...[
                        const SizedBox(height: 10),
                        Text('已加入家庭：${profile.joinedFamilies.join('、')}'),
                      ],
                      if (relationship.blockedMe) ...[
                        const SizedBox(height: 8),
                        const Text(
                          '对方已将您拉黑',
                          style: TextStyle(color: Colors.grey),
                        ),
                      ],
                    ],
                  ),
                ),
                if (profile.isMerchant && profile.shopName != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        CircleAvatar(
                          backgroundImage:
                              profile.shopLogoUrl?.isNotEmpty == true
                              ? NetworkImage(
                                  resolveCustomerImageUrl(profile.shopLogoUrl!),
                                )
                              : null,
                          child: profile.shopLogoUrl?.isNotEmpty == true
                              ? null
                              : const Icon(Icons.storefront_outlined),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                profile.shopName!,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              if (profile.shopDescription?.isNotEmpty == true)
                                Text(
                                  profile.shopDescription!,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    leading: SizedBox(
                      width: 62,
                      child: Stack(
                        children: [
                          for (
                            var i = 0;
                            i <
                                (momentSummary?.preview.length ?? 0).clamp(
                                  0,
                                  3,
                                );
                            i++
                          )
                            Positioned(
                              left: i * 15,
                              child: CircleAvatar(
                                radius: 18,
                                backgroundImage:
                                    momentSummary!.preview[i].images.isNotEmpty
                                    ? NetworkImage(
                                        resolveCustomerImageUrl(
                                          momentSummary.preview[i].images.first,
                                        ),
                                      )
                                    : null,
                                child: momentSummary.preview[i].images.isEmpty
                                    ? const Icon(Icons.dynamic_feed, size: 16)
                                    : null,
                              ),
                            ),
                          if ((momentSummary?.preview.isEmpty ?? true))
                            const CircleAvatar(
                              radius: 18,
                              child: Icon(
                                Icons.dynamic_feed_outlined,
                                size: 18,
                              ),
                            ),
                        ],
                      ),
                    ),
                    title: const Text('Ta发布的动态'),
                    subtitle: momentSummary == null
                        ? const Text('点击查看')
                        : Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              for (final moment in momentSummary.preview.take(
                                2,
                              ))
                                Text(
                                  moment.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              if (momentSummary.preview.isEmpty)
                                Text('${momentSummary.momentCount}条动态'),
                            ],
                          ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => UserMomentsPage(
                          userId: widget.userId,
                          nickname: profile.nickname,
                        ),
                      ),
                    ),
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
                        onTap: _working ? null : () => _startChat(profile),
                      ),
                      if (preferences != null) ...[
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          secondary: const Icon(
                            Icons.notifications_off_outlined,
                          ),
                          title: const Text('消息免打扰'),
                          value: preferences.muted,
                          onChanged: _working
                              ? null
                              : (_) => _toggleMute(preferences),
                        ),
                      ],
                      const Divider(height: 1, indent: 56),
                      SwitchListTile(
                        secondary: const Icon(Icons.visibility_off_outlined),
                        title: const Text('不看Ta动态'),
                        value: momentPrivacy.hideTheirMoments,
                        onChanged: _working
                            ? null
                            : (value) => _updateMomentPrivacy(
                                momentPrivacy,
                                hideTheirMoments: value,
                              ),
                      ),
                      const Divider(height: 1, indent: 56),
                      SwitchListTile(
                        secondary: const Icon(Icons.person_off_outlined),
                        title: const Text('不让Ta看我动态'),
                        value: momentPrivacy.hideMineFromThem,
                        onChanged: _working
                            ? null
                            : (value) => _updateMomentPrivacy(
                                momentPrivacy,
                                hideMineFromThem: value,
                              ),
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
                          relationship.blockedByMe ? '取消拉黑' : '拉黑用户',
                          style: TextStyle(
                            color: relationship.blockedByMe ? null : Colors.red,
                          ),
                        ),
                        onTap: _working
                            ? null
                            : () => _toggleBlock(relationship),
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

class _MerchantBadge extends StatelessWidget {
  const _MerchantBadge();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
    decoration: BoxDecoration(
      color: const Color(0xFFFFE7CD),
      borderRadius: BorderRadius.circular(5),
    ),
    child: const Text(
      '商家',
      style: TextStyle(fontSize: 11, color: Color(0xFFB75E0A)),
    ),
  );
}

String chatBlockActionLabel(ChatBlock relationship) =>
    relationship.blockedByMe ? '取消拉黑' : '拉黑用户';
