import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../models/moment_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../widgets/app_notice.dart';
import '../widgets/skeleton_box.dart';
import 'moment_pages.dart';

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
    this.conversationId,
    this.initialMuted = false,
  });

  final int userId;
  final String nickname;
  final String? avatarUrl;
  final String? phone;
  final String? statusText;
  final bool canBlock;
  final int? conversationId;
  final bool initialMuted;

  @override
  State<ChatUserDetailPage> createState() => _ChatUserDetailPageState();
}

class _ChatUserDetailPageState extends State<ChatUserDetailPage> {
  ChatBlockState? _block;
  ChatUserProfile? _profile;
  late bool _mutedConversation;
  bool _saving = false;
  MomentPrivacy? _momentPrivacy;
  MomentProfileSummary? _momentSummary;
  bool _profileLoaded = false;

  @override
  void initState() {
    super.initState();
    _mutedConversation = widget.initialMuted;
    _loadProfile();
    _loadMomentPrivacy();
    _loadMomentSummary();
    if (widget.canBlock) _loadBlock();
  }

  Future<void> _loadMomentSummary() async {
    try {
      final value = await shopApi.fetchMomentProfileSummary(widget.userId);
      if (mounted) setState(() => _momentSummary = value);
    } on ApiException catch (_) {}
  }

  Future<void> _loadMomentPrivacy() async {
    try {
      final value = await shopApi.fetchMomentPrivacy(widget.userId);
      if (mounted) setState(() => _momentPrivacy = value);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _updateMomentPrivacy({bool? hideTheirs, bool? hideMine}) async {
    final current = _momentPrivacy ?? const MomentPrivacy();
    setState(() => _saving = true);
    try {
      final value = await shopApi.updateMomentPrivacy(
        widget.userId,
        hideTheirMoments: hideTheirs ?? current.hideTheirMoments,
        hideMineFromThem: hideMine ?? current.hideMineFromThem,
      );
      if (mounted) {
        setState(() => _momentPrivacy = value);
        await _loadMomentSummary();
      }
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _openUserMoments() async {
    final privacy = _momentPrivacy;
    if (privacy?.hideTheirMoments == true) {
      final close = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('无法查看动态'),
          content: const Text('您已设置不看Ta动态。关闭该设置后即可查看。'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('关闭并查看'),
            ),
          ],
        ),
      );
      if (close != true) return;
      await _updateMomentPrivacy(hideTheirs: false);
    }
    if (!mounted) return;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => UserMomentsPage(
          userId: widget.userId,
          nickname: _profile?.nickname ?? widget.nickname,
          blockedReason: _momentSummary?.canView == false
              ? (_momentSummary?.reason ?? '对方的动态页当前不可见')
              : null,
        ),
      ),
    );
  }

  Future<void> _loadProfile() async {
    try {
      final value = await shopApi.fetchChatUserProfile(
        widget.userId,
        conversationId: widget.conversationId,
      );
      if (mounted) setState(() => _profile = value);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _profileLoaded = true);
    }
  }

  Future<void> _toggleMuted(bool value) async {
    final conversationId = widget.conversationId;
    if (conversationId == null) return;
    setState(() => _mutedConversation = value);
    try {
      await shopApi.updateConversationPreferences(conversationId, muted: value);
      if (mounted) AppNotice.show(context, value ? '已开启消息免打扰' : '已关闭消息免打扰');
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _mutedConversation = !value);
      AppNotice.show(context, error.message);
    }
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
    final profile = _profile;
    final rawAvatar = profile?.avatarUrl ?? widget.avatarUrl;
    final avatar = rawAvatar?.isNotEmpty == true
        ? ApiConfig.resolveImageUrl(rawAvatar!)
        : null;
    if (!_profileLoaded) {
      return Scaffold(
        backgroundColor: _pageBackground,
        appBar: AppBar(title: const Text('用户资料')),
        body: const _ChatUserProfileSkeleton(),
      );
    }
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
                  profile?.nickname ?? widget.nickname,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (profile?.isMerchant == true) ...[
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFE8CF),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text(
                      '商家',
                      style: TextStyle(color: Color(0xFFB76512)),
                    ),
                  ),
                ],
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
                  value: (profile?.phone ?? widget.phone)?.isNotEmpty == true
                      ? (profile?.phone ?? widget.phone)!
                      : '未公开',
                ),
                if (profile?.joinedFamilies.isNotEmpty == true)
                  _InfoRow(
                    label: '已加入家庭',
                    value: profile!.joinedFamilies.join('、'),
                  ),
              ],
            ),
          ),
          if (profile?.isMerchant == true) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        backgroundColor: const Color(0xFFFFF4E8),
                        foregroundImage:
                            profile?.shopLogoUrl?.isNotEmpty == true
                            ? NetworkImage(
                                ApiConfig.resolveImageUrl(
                                  profile!.shopLogoUrl!,
                                ),
                              )
                            : null,
                        child: profile?.shopLogoUrl?.isNotEmpty == true
                            ? null
                            : const Icon(Icons.storefront, color: _orange),
                      ),
                      const SizedBox(width: 10),
                      const Text(
                        '店铺信息',
                        style: TextStyle(fontWeight: FontWeight.w800),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  _InfoRow(label: '店铺名称', value: profile?.shopName ?? '未公开'),
                  if (profile?.shopDescription?.isNotEmpty == true)
                    Text(
                      profile!.shopDescription!,
                      style: const TextStyle(color: _muted),
                    ),
                ],
              ),
            ),
          ],
          if (widget.conversationId != null) ...[
            const SizedBox(height: 12),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: SwitchListTile(
                secondary: const Icon(
                  Icons.notifications_off_outlined,
                  color: _orange,
                ),
                title: const Text('消息免打扰'),
                subtitle: const Text('未读消息仍会显示灰色徽标'),
                value: _mutedConversation,
                onChanged: _toggleMuted,
              ),
            ),
          ],
          const SizedBox(height: 12),
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(
                    Icons.dynamic_feed_outlined,
                    color: _orange,
                  ),
                  title: Text('${profile?.nickname ?? widget.nickname}的动态'),
                  subtitle: Text(
                    _momentSummary == null
                        ? '查看该用户发布过的动态'
                        : '共 ${_momentSummary!.momentCount} 条动态',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: _openUserMoments,
                ),
                if (_momentSummary?.preview.isNotEmpty == true)
                  SizedBox(
                    height: 74,
                    child: ListView.separated(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                      scrollDirection: Axis.horizontal,
                      itemCount: _momentSummary!.preview.length.clamp(0, 2),
                      separatorBuilder: (_, _) => const SizedBox(width: 8),
                      itemBuilder: (context, index) {
                        final moment = _momentSummary!.preview[index];
                        return InkWell(
                          onTap: _openUserMoments,
                          child: Container(
                            width: 150,
                            padding: const EdgeInsets.all(9),
                            decoration: BoxDecoration(
                              color: _pageBackground,
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              moment.title,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                const Divider(height: 1),
                SwitchListTile(
                  secondary: const Icon(
                    Icons.visibility_off_outlined,
                    color: _orange,
                  ),
                  title: const Text('不看Ta动态'),
                  subtitle: const Text('推荐和相关动态中都不再展示'),
                  value: _momentPrivacy?.hideTheirMoments ?? false,
                  onChanged: _momentPrivacy == null || _saving
                      ? null
                      : (value) => _updateMomentPrivacy(hideTheirs: value),
                ),
                const Divider(height: 1),
                SwitchListTile(
                  secondary: const Icon(
                    Icons.person_off_outlined,
                    color: _orange,
                  ),
                  title: const Text('不让Ta看我动态'),
                  subtitle: const Text('对方在推荐和相关中都看不到我的动态'),
                  value: _momentPrivacy?.hideMineFromThem ?? false,
                  onChanged: _momentPrivacy == null || _saving
                      ? null
                      : (value) => _updateMomentPrivacy(hideMine: value),
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

class _ChatUserProfileSkeleton extends StatelessWidget {
  const _ChatUserProfileSkeleton();

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: const [
      DecoratedBox(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.all(Radius.circular(8)),
        ),
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Column(
            children: [
              SkeletonBox(
                width: 76,
                height: 76,
                borderRadius: BorderRadius.all(Radius.circular(38)),
              ),
              SizedBox(height: 14),
              SkeletonBox(width: 132, height: 20),
              SizedBox(height: 24),
              SkeletonBox(width: double.infinity, height: 44),
              SizedBox(height: 10),
              SkeletonBox(width: double.infinity, height: 44),
              SizedBox(height: 10),
              SkeletonBox(width: double.infinity, height: 44),
            ],
          ),
        ),
      ),
      SizedBox(height: 12),
      SkeletonBox(width: double.infinity, height: 118),
      SizedBox(height: 12),
      SkeletonBox(width: double.infinity, height: 164),
    ],
  );
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
