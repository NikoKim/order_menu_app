import 'dart:async';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';

const Color _warmOrange = Color(0xFFF28C28);
const Color _softOrange = Color(0xFFFFF4E8);
const Color _pageBgColor = Color(0xFFF5F6F8);
const Color _lineColor = Color(0xFFF0E7DE);
const Color _textColor = Color(0xFF242424);
const Color _mutedColor = Color(0xFF77716B);

class ShopFamilyPage extends StatefulWidget {
  const ShopFamilyPage({super.key});

  @override
  State<ShopFamilyPage> createState() => _ShopFamilyPageState();
}

class _ShopFamilyPageState extends State<ShopFamilyPage> {
  final TextEditingController _familyNameController = TextEditingController();
  final ImagePicker _imagePicker = ImagePicker();

  bool _loading = true;
  bool _saving = false;
  bool _isFamilyShop = false;
  String _familyJoinMode = 'OPEN';
  String? _familyCode;
  String? _familyImageUrl;
  ShopInfo? _shop;
  List<FamilyMemberInfo> _members = const [];
  ChatConversation? _familyGroup;
  String? _error;
  StreamSubscription? _wsSubscription;

  @override
  void initState() {
    super.initState();

    _load();

    _wsSubscription = ShopWebSocketService.instance.messageStream.listen((msg) {
      if (!mounted) return;

      final familyChanged =
          msg.module == 'FAMILY' &&
          {
            'MEMBER_APPLIED',
            'MEMBER_APPROVED',
            'MEMBER_REJECTED',
          }.contains(msg.action);
      if (familyChanged || msg.module == 'CHAT') {
        _load(silent: true);
      }
    });
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    super.dispose();
  }

  Future _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }

    try {
      final shop = await shopApi.fetchMyShop();

      final List<FamilyMemberInfo> members = shop.visibilityType == 'FAMILY'
          ? await shopApi.fetchFamilyMembers()
          : <FamilyMemberInfo>[];
      final conversations = shop.visibilityType == 'FAMILY'
          ? await shopApi.fetchConversations()
          : const <ChatConversation>[];
      ChatConversation? familyGroup;
      for (final conversation in conversations) {
        if (conversation.type == 'FAMILY_GROUP') {
          familyGroup = conversation;
          break;
        }
      }

      if (!mounted) return;

      setState(() {
        _shop = shop;
        _isFamilyShop = shop.visibilityType == 'FAMILY';
        _familyNameController.text = shop.familyName ?? '';
        _familyCode = shop.familyCode;
        _familyJoinMode = shop.familyJoinMode ?? 'OPEN';
        _familyImageUrl = shop.familyImageUrl;
        _members = members;
        _familyGroup = familyGroup;
        _error = null;
      });
    } on ApiException catch (error) {
      if (mounted && !silent) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted && !silent) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final approvedCount = _members.where((item) => item.isApproved).length;
    final pendingCount = _members.where((item) => item.isPending).length;

    return Scaffold(
      backgroundColor: _pageBgColor,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
        title: const Text(
          '家庭管理',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      body: SafeArea(
        child: RefreshIndicator(
          color: _warmOrange,
          backgroundColor: Colors.white,
          onRefresh: _load,
          child: _buildBody(approvedCount, pendingCount),
        ),
      ),
      bottomNavigationBar: _buildBottomBar(),
    );
  }

  Widget _buildBody(int approvedCount, int pendingCount) {
    if (_loading) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          _SkeletonBox(width: double.infinity, height: 110, borderRadius: 12),
          SizedBox(height: 16),
          _SkeletonBox(width: double.infinity, height: 260, borderRadius: 12),
          SizedBox(height: 16),
          _SkeletonBox(width: double.infinity, height: 200, borderRadius: 12),
        ],
      );
    }

    if (_error != null) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [_ErrorPanel(message: _error!, onRetry: _load)],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _buildHeader(approvedCount, pendingCount),
        const SizedBox(height: 16),
        _buildSettings(),
        const SizedBox(height: 16),
        _buildFamilyGroupPanel(approvedCount),
        const SizedBox(height: 16),
        _buildMemberSection(),
      ],
    );
  }

  Widget _buildFamilyGroupPanel(int approvedCount) {
    if (!_isFamilyShop) return const SizedBox.shrink();
    final group = _familyGroup;
    final eligible = FamilyGroupEligibility.fromMembers(
      approvedCount,
    ).canCreate;
    return _BaseCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.groups_outlined, color: _warmOrange),
              SizedBox(width: 8),
              Text(
                '家庭群聊',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (group == null) ...[
            Text(
              eligible ? '已满足创建条件，家庭成员会按其设置自动加入或收到邀请。' : '至少需要两位已审批家庭成员后才能创建。',
              style: const TextStyle(color: _mutedColor, fontSize: 13),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: eligible && !_saving ? _createGroup : null,
              icon: const Icon(Icons.group_add_outlined),
              label: const Text('创建家庭群聊'),
            ),
          ] else ...[
            Text(
              group.title,
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            Text(
              group.isActive ? '群聊进行中' : '群聊已解散，历史消息已保留',
              style: const TextStyle(color: _mutedColor, fontSize: 13),
            ),
            const SizedBox(height: 12),
            if (!group.isActive) ...[
              FilledButton.icon(
                onPressed: eligible && !_saving
                    ? () => _restoreGroup(group.id)
                    : null,
                icon: const Icon(Icons.restore),
                label: const Text('恢复家庭群聊'),
              ),
              const SizedBox(height: 8),
            ],
            OutlinedButton.icon(
              onPressed: () => _manageGroup(group),
              icon: const Icon(Icons.manage_accounts_outlined),
              label: const Text('管理家庭群聊'),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _createGroup() async {
    setState(() => _saving = true);
    try {
      await shopApi.createFamilyGroup();
      if (mounted) {
        _showSnackBar('家庭群聊已创建');
        _load(silent: true);
      }
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _restoreGroup(int conversationId) async {
    setState(() => _saving = true);
    try {
      await shopApi.restoreFamilyGroup(conversationId);
      if (mounted) {
        _showSnackBar('家庭群聊已恢复');
        _load(silent: true);
      }
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _manageGroup(ChatConversation conversation) async {
    FamilyGroup group;
    try {
      group = await shopApi.fetchFamilyGroup(conversation.id);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
      return;
    }
    if (!mounted) return;
    final name = TextEditingController(text: group.name);
    final announcement = TextEditingController(text: group.announcement ?? '');
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 16,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '家庭群聊管理',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: name,
                decoration: const InputDecoration(labelText: '群名称'),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: announcement,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(labelText: '群公告'),
              ),
              const SizedBox(height: 12),
              if (group.canManage) ...[
                FilledButton(
                  onPressed: () async {
                    await shopApi.updateFamilyGroup(
                      group.id,
                      name: name.text.trim(),
                      announcement: announcement.text.trim(),
                    );
                    if (sheetContext.mounted) Navigator.pop(sheetContext);
                    _load(silent: true);
                  },
                  child: const Text('保存群资料'),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: () async {
                    await shopApi.dissolveFamilyGroup(group.id);
                    if (sheetContext.mounted) Navigator.pop(sheetContext);
                    _load(silent: true);
                  },
                  icon: const Icon(Icons.group_off_outlined),
                  label: const Text('解散家庭群聊'),
                ),
              ],
              const SizedBox(height: 16),
              const Text('群成员', style: TextStyle(fontWeight: FontWeight.w800)),
              for (final member in group.members)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(member.nickname),
                  subtitle: Text(
                    member.role == 'OWNER' ? '群主' : member.memberStatus,
                  ),
                  trailing: member.role == 'OWNER' || !group.canManage
                      ? null
                      : IconButton(
                          tooltip: '移出群聊',
                          icon: const Icon(Icons.person_remove_outlined),
                          onPressed: () async {
                            await shopApi.removeFamilyGroupMember(
                              group.id,
                              member.userId,
                            );
                            if (sheetContext.mounted)
                              Navigator.pop(sheetContext);
                            _load(silent: true);
                          },
                        ),
                ),
            ],
          ),
        ),
      ),
    );
    name.dispose();
    announcement.dispose();
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Widget? _buildBottomBar() {
    if (_loading) return null;
    return SafeArea(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 12,
              offset: const Offset(0, -4),
            ),
          ],
        ),
        child: SizedBox(
          height: 48,
          child: FilledButton.icon(
            onPressed: _saving ? null : _save,
            icon: const Icon(Icons.save_outlined),
            label: Text(
              _saving ? '保存中...' : '保存家庭设置',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
            style: FilledButton.styleFrom(
              backgroundColor: _warmOrange,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(int approvedCount, int pendingCount) {
    return _BaseCard(
      child: Row(
        children: [
          GestureDetector(
            onTap: _familyImageUrl?.isNotEmpty == true
                ? () => _previewImage(_familyImageUrl!)
                : null,
            child: Container(
              width: 72,
              height: 72,
              clipBehavior: Clip.antiAlias,
              decoration: BoxDecoration(
                color: _softOrange,
                borderRadius: BorderRadius.circular(12),
              ),
              child: _familyImageUrl?.isNotEmpty == true
                  ? Image.network(
                      _absoluteImageUrl(_familyImageUrl!),
                      fit: BoxFit.cover,
                      loadingBuilder: (context, child, progress) {
                        if (progress == null) return child;
                        return const _SkeletonBox(width: 72, height: 72);
                      },
                    )
                  : const Icon(
                      Icons.home_outlined,
                      color: _warmOrange,
                      size: 36,
                    ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _familyNameController.text.trim().isEmpty
                      ? '未命名家庭'
                      : _familyNameController.text.trim(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: _textColor,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _InfoPill(
                      icon: Icons.tag,
                      text: _isFamilyShop
                          ? '编号 ${_familyCode?.isNotEmpty == true ? _familyCode : '保存后生成'}'
                          : '未开启家庭',
                    ),
                    _InfoPill(
                      icon: Icons.people_alt_outlined,
                      text: '$approvedCount 位成员',
                    ),
                    if (pendingCount > 0)
                      _InfoPill(
                        icon: Icons.hourglass_top,
                        text: '$pendingCount 个申请',
                        isHighlight: true,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSettings() {
    return _BaseCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '基础设置',
            style: TextStyle(
              color: _textColor,
              fontSize: 16,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 16),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _isFamilyShop,
            activeColor: Colors.white,
            activeTrackColor: _warmOrange,
            title: const Text(
              '开启家庭店铺',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            subtitle: const Text(
              '开启后，顾客需要加入家庭才能在专属标签中看到本店',
              style: TextStyle(color: _mutedColor, fontSize: 13),
            ),
            onChanged: (value) => setState(() => _isFamilyShop = value),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Divider(color: _lineColor, height: 1),
          ),
          _LabeledField(
            label: '家庭名称',
            child: TextField(
              controller: _familyNameController,
              onChanged: (_) => setState(() {}),
              decoration: _inputDecoration('请输入家庭名称'),
            ),
          ),
          const SizedBox(height: 16),
          _LabeledField(
            label: '用户加入方式',
            child: DropdownButtonFormField<String>(
              initialValue: _familyJoinMode,
              decoration: _inputDecoration('请选择加入方式'),
              icon: const Icon(Icons.expand_more, color: _mutedColor),
              items: const [
                DropdownMenuItem(value: 'OPEN', child: Text('可加入，不需要审核')),
                DropdownMenuItem(value: 'APPROVAL', child: Text('可加入，需要商家审核')),
                DropdownMenuItem(value: 'CLOSED', child: Text('暂不支持新用户加入')),
              ],
              onChanged: (value) {
                if (value != null) setState(() => _familyJoinMode = value);
              },
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _uploadFamilyImage,
                  icon: const Icon(Icons.image_outlined, size: 18),
                  label: Text(
                    _familyImageUrl?.isNotEmpty == true ? '更换家庭图片' : '上传家庭图片',
                  ),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: _warmOrange,
                    side: const BorderSide(color: _warmOrange),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ),
              if (_familyImageUrl?.isNotEmpty == true) ...[
                const SizedBox(width: 10),
                IconButton.outlined(
                  tooltip: '查看家庭图片',
                  onPressed: () => _previewImage(_familyImageUrl!),
                  icon: const Icon(
                    Icons.visibility_outlined,
                    color: _mutedColor,
                  ),
                  style: IconButton.styleFrom(
                    side: const BorderSide(color: _lineColor),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMemberSection() {
    if (!_isFamilyShop) {
      return const _EmptyMembers(
        icon: Icons.lock_open_outlined,
        title: '当前是公开店铺模式',
        message: '开启家庭店铺并保存后，就可以在这里管理家庭顾客。',
      );
    }

    if (_members.isEmpty) {
      return const _EmptyMembers(
        icon: Icons.people_alt_outlined,
        title: '还没有家庭顾客',
        message: '顾客输入家庭编号加入后，会显示在这里。',
      );
    }

    final sorted = [..._members]
      ..sort((a, b) {
        if (a.isPending == b.isPending) return a.id.compareTo(b.id);
        return a.isPending ? -1 : 1;
      });

    return _BaseCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '家庭顾客',
            style: TextStyle(
              color: _textColor,
              fontSize: 16,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 16),
          for (final member in sorted)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _MemberTile(
                member: member,
                avatarUrl: _resolvedAvatarUrl(member.avatarUrl),
                onInfo: () => _showMemberInfo(member),
                onApprove: member.isPending
                    ? () => _approveMember(member)
                    : null,
                onRemove: () => _removeMember(member),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    final shop = _shop;
    if (shop == null) return;

    setState(() => _saving = true);
    try {
      final updated = await shopApi.updateMyShop(
        name: shop.name,
        description: shop.description,
        coverImageUrl: shop.coverImageUrl,
        address: shop.address,
        phone: shop.phone,
        businessStatus: shop.businessStatus,
        visibilityType: _isFamilyShop ? 'FAMILY' : 'PUBLIC',
        familyName: _familyNameController.text.trim(),
        familyJoinMode: _familyJoinMode,
        familyImageUrl: _familyImageUrl,
      );
      if (mounted) {
        setState(() {
          _shop = updated;
          _isFamilyShop = updated.visibilityType == 'FAMILY';
          _familyCode = updated.familyCode;
          _familyJoinMode = updated.familyJoinMode ?? 'OPEN';
          _familyImageUrl = updated.familyImageUrl;
        });
        _showMessage('家庭设置已保存');
        _load();
      }
    } on ApiException catch (error) {
      if (mounted) _showMessage(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _uploadFamilyImage() async {
    final image = await _imagePicker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (image == null) return;

    setState(() => _saving = true);
    try {
      final url = await shopApi.uploadFamilyImage(image.path);
      if (mounted) {
        setState(() => _familyImageUrl = url);
        _showMessage('家庭图片已上传，记得保存设置');
      }
    } on ApiException catch (error) {
      if (mounted) _showMessage(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _approveMember(FamilyMemberInfo member) async {
    await shopApi.approveFamilyMember(member.id);
    if (mounted) {
      _showMessage('已同意加入');
      _load();
    }
  }

  Future<void> _removeMember(FamilyMemberInfo member) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('移除顾客'),
        content: Text('确定将 ${member.displayName} 移出家庭吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消', style: TextStyle(color: _mutedColor)),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            child: const Text('移除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    await shopApi.rejectFamilyMember(member.id);
    if (mounted) {
      _showMessage('已移除顾客');
      _load();
    }
  }

  void _showMemberInfo(FamilyMemberInfo member) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      backgroundColor: Colors.white,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: _softOrange,
                    foregroundColor: _warmOrange,
                    backgroundImage:
                        _resolvedAvatarUrl(member.avatarUrl) == null
                        ? null
                        : NetworkImage(_resolvedAvatarUrl(member.avatarUrl)!),
                    child: _resolvedAvatarUrl(member.avatarUrl) == null
                        ? Text(
                            member.displayName.characters.first,
                            style: const TextStyle(fontSize: 20),
                          )
                        : null,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          member.displayName,
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          member.statusText,
                          style: const TextStyle(
                            color: _mutedColor,
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              _InfoRow(label: '系统编号', value: '#${member.userId}'),
              _InfoRow(label: '用户昵称', value: member.displayName),
              _InfoRow(
                label: '联系电话',
                value: member.phone?.isNotEmpty == true ? member.phone! : '未填写',
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _previewImage(String imageUrl) {
    showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.all(20),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: InteractiveViewer(
            child: Image.network(
              _absoluteImageUrl(imageUrl),
              fit: BoxFit.contain,
              errorBuilder: (_, __, ___) => Container(
                color: Colors.white,
                padding: const EdgeInsets.all(32),
                child: const Text('图片加载失败'),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
    );
  }

  String _absoluteImageUrl(String url) {
    if (url.startsWith('http')) return url;
    final api = Uri.parse(
      const String.fromEnvironment(
        'SHOP_API_BASE_URL',
        defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
      ),
    );
    return api.replace(path: url).toString();
  }

  String? _resolvedAvatarUrl(String? url) {
    if (url == null || url.isEmpty) return null;
    return _absoluteImageUrl(url);
  }
}

// --------------------------------------------------------------------------
// 视图组件抽取
// --------------------------------------------------------------------------

class _BaseCard extends StatelessWidget {
  const _BaseCard({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.02),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: child,
    );
  }
}

class _LabeledField extends StatelessWidget {
  const _LabeledField({required this.label, required this.child});
  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: _textColor,
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
}

InputDecoration _inputDecoration(String hintText) {
  return InputDecoration(
    hintText: hintText,
    hintStyle: const TextStyle(color: Color(0xFFB8AFA7)),
    filled: true,
    fillColor: const Color(0xFFF9FAFB),
    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: Color(0xFFE8E8E8)),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: _warmOrange),
    ),
  );
}

class _InfoPill extends StatelessWidget {
  const _InfoPill({
    required this.icon,
    required this.text,
    this.isHighlight = false,
  });
  final IconData icon;
  final String text;
  final bool isHighlight;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isHighlight
            ? _warmOrange.withValues(alpha: 0.1)
            : const Color(0xFFF5F6F8),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: isHighlight ? _warmOrange : _mutedColor),
          const SizedBox(width: 4),
          Text(
            text,
            style: TextStyle(
              color: isHighlight ? _warmOrange : _mutedColor,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _MemberTile extends StatelessWidget {
  const _MemberTile({
    required this.member,
    required this.avatarUrl,
    required this.onInfo,
    required this.onRemove,
    this.onApprove,
  });
  final FamilyMemberInfo member;
  final String? avatarUrl;
  final VoidCallback onInfo;
  final VoidCallback onRemove;
  final VoidCallback? onApprove;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _lineColor),
      ),
      child: Row(
        children: [
          CircleAvatar(
            backgroundColor: _softOrange,
            foregroundColor: _warmOrange,
            backgroundImage: avatarUrl == null
                ? null
                : NetworkImage(avatarUrl!),
            child: avatarUrl == null
                ? Text(member.displayName.characters.first)
                : null,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  member.displayName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${member.statusText} · ${member.phone?.isNotEmpty == true ? member.phone : '未填电话'}',
                  style: const TextStyle(color: _mutedColor, fontSize: 13),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          Wrap(
            spacing: 0,
            children: [
              IconButton(
                tooltip: '查看信息',
                onPressed: onInfo,
                icon: const Icon(
                  Icons.info_outline,
                  color: _mutedColor,
                  size: 20,
                ),
              ),
              if (onApprove != null)
                IconButton(
                  tooltip: '同意加入',
                  onPressed: onApprove,
                  icon: const Icon(
                    Icons.check_circle,
                    color: Color(0xFF20A464),
                    size: 22,
                  ),
                ),
              IconButton(
                tooltip: '移除',
                onPressed: onRemove,
                icon: const Icon(
                  Icons.person_remove_outlined,
                  color: Colors.redAccent,
                  size: 20,
                ),
              ),
            ],
          ),
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
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(
              label,
              style: const TextStyle(color: _mutedColor, fontSize: 14),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: _textColor,
                fontWeight: FontWeight.w600,
                fontSize: 15,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyMembers extends StatelessWidget {
  const _EmptyMembers({
    required this.icon,
    required this.title,
    required this.message,
  });
  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return _BaseCard(
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 24),
        child: Column(
          children: [
            Icon(icon, color: const Color(0xFFE0E0E0), size: 48),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: _mutedColor, fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _softOrange,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: _warmOrange),
          const SizedBox(width: 12),
          Expanded(
            child: Text(message, style: const TextStyle(color: _textColor)),
          ),
          TextButton(
            onPressed: onRetry,
            child: const Text('重试', style: TextStyle(color: _warmOrange)),
          ),
        ],
      ),
    );
  }
}

class _SkeletonBox extends StatefulWidget {
  final double width;
  final double height;
  final double borderRadius;

  const _SkeletonBox({
    required this.width,
    required this.height,
    this.borderRadius = 8,
  });

  @override
  State<_SkeletonBox> createState() => _SkeletonBoxState();
}

class _SkeletonBoxState extends State<_SkeletonBox>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<Color?> _colorTween;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat(reverse: true);
    _colorTween = ColorTween(
      begin: const Color(0xFFF0F0F0),
      end: const Color(0xFFE0E0E0),
    ).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _colorTween,
      builder: (context, child) {
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            color: _colorTween.value,
            borderRadius: BorderRadius.circular(widget.borderRadius),
          ),
        );
      },
    );
  }
}

extension on FamilyMemberInfo {
  bool get isPending => status == 'PENDING';
  bool get isApproved => status == 'APPROVED';
  String get displayName =>
      nickname?.trim().isNotEmpty == true ? nickname!.trim() : '顾客$userId';
  String get statusText {
    switch (status) {
      case 'APPROVED':
        return '已加入';
      case 'PENDING':
        return '待审核';
      default:
        return '已拒绝';
    }
  }
}
