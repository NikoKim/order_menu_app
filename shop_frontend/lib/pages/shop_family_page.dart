import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';

class ShopFamilyPage extends StatefulWidget {
  const ShopFamilyPage({super.key});

  @override
  State<ShopFamilyPage> createState() => _ShopFamilyPageState();
}

class _ShopFamilyPageState extends State<ShopFamilyPage> {
  static const Color _warmOrange = Color(0xFFF28C28);
  static const Color _softOrange = Color(0xFFFFF4E8);
  static const Color _lineColor = Color(0xFFF0E7DE);
  static const Color _textColor = Color(0xFF242424);
  static const Color _mutedColor = Color(0xFF77716B);

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
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _familyNameController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final shop = await shopApi.fetchMyShop();
      final members = shop.visibilityType == 'FAMILY'
          ? await shopApi.fetchFamilyMembers()
          : <FamilyMemberInfo>[];
      if (!mounted) {
        return;
      }
      setState(() {
        _shop = shop;
        _isFamilyShop = shop.visibilityType == 'FAMILY';
        _familyNameController.text = shop.familyName ?? '';
        _familyCode = shop.familyCode;
        _familyJoinMode = shop.familyJoinMode ?? 'OPEN';
        _familyImageUrl = shop.familyImageUrl;
        _members = members;
      });
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final approvedCount = _members.where((item) => item.isApproved).length;
    final pendingCount = _members.where((item) => item.isPending).length;
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
        title: const Text('家庭管理'),
      ),
      body: SafeArea(
        child: RefreshIndicator(
          color: _warmOrange,
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (_loading)
                const LinearProgressIndicator(color: _warmOrange)
              else if (_error != null)
                _buildErrorBanner(),
              _buildHeader(approvedCount, pendingCount),
              const SizedBox(height: 12),
              _buildSettings(),
              const SizedBox(height: 16),
              _buildMemberSection(),
            ],
          ),
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: SizedBox(
            height: 48,
            child: FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: const Icon(Icons.save_outlined),
              label: Text(_saving ? '保存中...' : '保存家庭设置'),
              style: FilledButton.styleFrom(
                backgroundColor: _warmOrange,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(int approvedCount, int pendingCount) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _softOrange,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _lineColor),
      ),
      child: Row(
        children: [
          GestureDetector(
            onTap: _familyImageUrl?.isNotEmpty == true
                ? () => _previewImage(_familyImageUrl!)
                : null,
            child: Container(
              width: 76,
              height: 76,
              clipBehavior: Clip.antiAlias,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: _lineColor),
              ),
              child: _familyImageUrl?.isNotEmpty == true
                  ? Image.network(
                      _absoluteImageUrl(_familyImageUrl!),
                      fit: BoxFit.cover,
                    )
                  : const Icon(
                      Icons.home_outlined,
                      color: _warmOrange,
                      size: 36,
                    ),
            ),
          ),
          const SizedBox(width: 14),
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
                          ? '家庭编号 ${_familyCode?.isNotEmpty == true ? _familyCode : '保存后生成'}'
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
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _lineColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _isFamilyShop,
            activeThumbColor: _warmOrange,
            title: const Text(
              '开启家庭店铺',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            subtitle: const Text('开启后，顾客需要加入家庭才能在家庭标签中看到店铺'),
            onChanged: (value) => setState(() => _isFamilyShop = value),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _familyNameController,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              labelText: '家庭名称',
              prefixIcon: Icon(Icons.home_outlined, color: _warmOrange),
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _familyJoinMode,
            decoration: const InputDecoration(
              labelText: '用户加入方式',
              prefixIcon: Icon(Icons.person_add_alt_1, color: _warmOrange),
            ),
            items: const [
              DropdownMenuItem(value: 'OPEN', child: Text('可加入，不需要审核')),
              DropdownMenuItem(value: 'APPROVAL', child: Text('可加入，需要商家审核')),
              DropdownMenuItem(value: 'CLOSED', child: Text('暂不支持新用户加入')),
            ],
            onChanged: (value) {
              if (value != null) {
                setState(() => _familyJoinMode = value);
              }
            },
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _uploadFamilyImage,
                  icon: const Icon(Icons.image_outlined),
                  label: Text(
                    _familyImageUrl?.isNotEmpty == true ? '更换家庭图片' : '上传家庭图片',
                  ),
                ),
              ),
              if (_familyImageUrl?.isNotEmpty == true) ...[
                const SizedBox(width: 10),
                IconButton.outlined(
                  tooltip: '查看家庭图片',
                  onPressed: () => _previewImage(_familyImageUrl!),
                  icon: const Icon(Icons.visibility_outlined),
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
      return _EmptyMembers(
        icon: Icons.lock_open_outlined,
        title: '当前是公开店铺',
        message: '开启家庭店铺并保存后，就可以在这里查看家庭顾客。',
      );
    }
    if (_loading) {
      return const SizedBox.shrink();
    }
    if (_members.isEmpty) {
      return const _EmptyMembers(
        icon: Icons.people_alt_outlined,
        title: '还没有家庭顾客',
        message: '顾客输入家庭编号加入后，会显示在这里。',
      );
    }
    final sorted = [..._members]..sort((a, b) {
        if (a.isPending == b.isPending) {
          return a.id.compareTo(b.id);
        }
        return a.isPending ? -1 : 1;
      });
    return Column(
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
        const SizedBox(height: 10),
        for (final member in sorted)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _MemberTile(
              member: member,
              avatarUrl: _resolvedAvatarUrl(member.avatarUrl),
              onInfo: () => _showMemberInfo(member),
              onApprove: member.isPending ? () => _approveMember(member) : null,
              onRemove: () => _removeMember(member),
            ),
          ),
      ],
    );
  }

  Widget _buildErrorBanner() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: _softOrange,
        borderRadius: BorderRadius.circular(8),
        child: ListTile(
          leading: const Icon(Icons.info_outline, color: _warmOrange),
          title: Text(_error!, style: const TextStyle(color: _textColor)),
          trailing: TextButton(onPressed: _load, child: const Text('重试')),
        ),
      ),
    );
  }

  Future<void> _save() async {
    final shop = _shop;
    if (shop == null) {
      return;
    }
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
      if (mounted) {
        _showMessage(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _uploadFamilyImage() async {
    final image = await _imagePicker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (image == null) {
      return;
    }

    setState(() => _saving = true);
    try {
      final url = await shopApi.uploadFamilyImage(image.path);
      if (mounted) {
        setState(() => _familyImageUrl = url);
        _showMessage('家庭图片已上传，记得保存家庭设置');
      }
    } on ApiException catch (error) {
      if (mounted) {
        _showMessage(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
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
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('移除'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
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
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 26,
                    backgroundColor: _softOrange,
                    foregroundColor: _warmOrange,
                    backgroundImage: _resolvedAvatarUrl(member.avatarUrl) == null
                        ? null
                        : NetworkImage(_resolvedAvatarUrl(member.avatarUrl)!),
                    child: _resolvedAvatarUrl(member.avatarUrl) == null
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
              const SizedBox(height: 18),
              _InfoRow(label: '用户编号', value: '${member.userId}'),
              _InfoRow(label: '昵称', value: member.displayName),
              _InfoRow(label: '电话', value: member.phone?.isNotEmpty == true ? member.phone! : '未填写'),
              const SizedBox(height: 8),
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
        insetPadding: const EdgeInsets.all(20),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: InteractiveViewer(
            child: Image.network(
              _absoluteImageUrl(imageUrl),
              fit: BoxFit.contain,
              errorBuilder: (_, _, _) => const Padding(
                padding: EdgeInsets.all(32),
                child: Text('图片加载失败'),
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
    if (url.startsWith('http')) {
      return url;
    }
    final api = Uri.parse(
      const String.fromEnvironment(
        'SHOP_API_BASE_URL',
        defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
      ),
    );
    return api.replace(path: url).toString();
  }

  String? _resolvedAvatarUrl(String? url) {
    if (url == null || url.isEmpty) {
      return null;
    }
    return _absoluteImageUrl(url);
  }
}

class _InfoPill extends StatelessWidget {
  const _InfoPill({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: const Color(0xFFF28C28)),
          const SizedBox(width: 4),
          Text(
            text,
            style: const TextStyle(
              color: Color(0xFF242424),
              fontSize: 12,
              fontWeight: FontWeight.w700,
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
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: Color(0xFFF0E7DE)),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        leading: CircleAvatar(
          backgroundColor: const Color(0xFFFFF4E8),
          foregroundColor: const Color(0xFFF28C28),
          backgroundImage: avatarUrl == null ? null : NetworkImage(avatarUrl!),
          child: avatarUrl == null
              ? Text(member.displayName.characters.first)
              : null,
        ),
        title: Text(
          member.displayName,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        subtitle: Text(
          '${member.statusText} · ${member.phone?.isNotEmpty == true ? member.phone : '未填写电话'}',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: Wrap(
          spacing: 4,
          children: [
            IconButton(
              tooltip: '查看信息',
              onPressed: onInfo,
              icon: const Icon(Icons.info_outline),
            ),
            if (onApprove != null)
              IconButton(
                tooltip: '同意加入',
                onPressed: onApprove,
                icon: const Icon(Icons.check_circle_outline),
              ),
            IconButton(
              tooltip: '移除',
              onPressed: onRemove,
              icon: const Icon(Icons.person_remove_outlined),
            ),
          ],
        ),
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
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          SizedBox(
            width: 76,
            child: Text(
              label,
              style: const TextStyle(color: Color(0xFF77716B), fontSize: 13),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: Color(0xFF242424),
                fontWeight: FontWeight.w600,
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
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFF0E7DE)),
      ),
      child: Column(
        children: [
          Icon(icon, color: const Color(0xFFF28C28), size: 38),
          const SizedBox(height: 10),
          Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Color(0xFF77716B), fontSize: 13),
          ),
        ],
      ),
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
