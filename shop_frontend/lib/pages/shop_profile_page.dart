import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';
import 'feedback_manage_page.dart';
import 'shop_family_page.dart';

class ShopProfilePage extends StatefulWidget {
  const ShopProfilePage({super.key, this.onLogout});

  final Future<void> Function()? onLogout;

  @override
  State<ShopProfilePage> createState() => _ShopProfilePageState();
}

class _ShopProfilePageState extends State<ShopProfilePage> {
  static const Color _warmOrange = Color(0xFFF28C28);
  static const Color _softOrange = Color(0xFFFFF4E8);
  static const Color _lineColor = Color(0xFFF0E7DE);
  static const Color _textColor = Color(0xFF242424);
  static const Color _mutedColor = Color(0xFF77716B);

  final TextEditingController _nameController = TextEditingController(
    text: shopName,
  );
  final TextEditingController _descriptionController = TextEditingController();
  final TextEditingController _phoneController = TextEditingController(
    text: '021-8888 6666',
  );
  final TextEditingController _addressController = TextEditingController();

  bool _isOpen = true;
  bool _loading = true;
  bool _saving = false;
  String? _coverImageUrl;
  String? _familyName;
  String? _familyCode;
  String? _familyJoinMode;
  String? _familyImageUrl;
  String? _visibilityType;
  int _pendingFamilyMembers = 0;
  String? _error;
  final ImagePicker _imagePicker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _descriptionController.dispose();
    _phoneController.dispose();
    _addressController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final shop = await shopApi.fetchMyShop();
      final notifications = await shopApi.fetchNotifications();
      if (!mounted) {
        return;
      }
      setState(() {
        _nameController.text = shop.name;
        _descriptionController.text = shop.description ?? '';
        _phoneController.text = shop.phone ?? '';
        _addressController.text = shop.address ?? '';
        _coverImageUrl = shop.coverImageUrl;
        _familyName = shop.familyName;
        _familyCode = shop.familyCode;
        _familyJoinMode = shop.familyJoinMode;
        _familyImageUrl = shop.familyImageUrl;
        _visibilityType = shop.visibilityType;
        _pendingFamilyMembers = notifications.pendingFamilyMembers;
        _isOpen = shop.isOpen;
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
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
        title: const Text('店铺资料'),
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
              _buildImagePicker(),
              const SizedBox(height: 16),
              _buildInputField(
                controller: _nameController,
                label: '店名',
                icon: Icons.storefront,
              ),
              const SizedBox(height: 12),
              _buildInputField(
                controller: _descriptionController,
                label: '简介',
                icon: Icons.notes_outlined,
                maxLines: 3,
              ),
              const SizedBox(height: 12),
              _buildInputField(
                controller: _phoneController,
                label: '电话',
                icon: Icons.phone,
                keyboardType: TextInputType.phone,
              ),
              const SizedBox(height: 12),
              _buildInputField(
                controller: _addressController,
                label: '地址',
                icon: Icons.location_on_outlined,
                maxLines: 2,
              ),
              const SizedBox(height: 12),
              _buildOpenStatusSwitch(),
              const SizedBox(height: 12),
              _buildFamilyEntry(),
              const SizedBox(height: 12),
              _buildFeedbackEntry(),
              const SizedBox(height: 24),
              SizedBox(
                height: 48,
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: _warmOrange,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  onPressed: _saving ? null : _save,
                  icon: const Icon(Icons.save_outlined),
                  label: Text(_saving ? '保存中...' : '保存'),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                height: 48,
                child: OutlinedButton.icon(
                  onPressed: widget.onLogout,
                  icon: const Icon(Icons.logout),
                  label: const Text('退出登录'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildImagePicker() {
    final imageUrl = _coverImageUrl;
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: _uploadCover,
      child: Container(
        height: 160,
        decoration: BoxDecoration(
          color: _softOrange,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: _lineColor),
          image: imageUrl == null || imageUrl.isEmpty
              ? null
              : DecorationImage(
                  image: NetworkImage(_absoluteImageUrl(imageUrl)),
                  fit: BoxFit.cover,
                ),
        ),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: imageUrl == null || imageUrl.isEmpty
                ? Colors.transparent
                : Colors.black.withValues(alpha: 0.18),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.image_outlined, color: _warmOrange, size: 44),
                SizedBox(height: 8),
                Text(
                  '点击上传店铺图片',
                  style: TextStyle(color: _mutedColor, fontSize: 14),
                ),
              ],
            ),
          ),
        ),
      ),
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

  Widget _buildInputField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    TextInputType? keyboardType,
    int maxLines = 1,
  }) {
    return TextField(
      controller: controller,
      keyboardType: keyboardType,
      maxLines: maxLines,
      decoration: InputDecoration(
        filled: true,
        fillColor: Colors.white,
        labelText: label,
        prefixIcon: Icon(icon, color: _warmOrange),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _lineColor),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _warmOrange, width: 1.4),
        ),
      ),
    );
  }

  Widget _buildOpenStatusSwitch() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _lineColor),
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: _softOrange,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.schedule, color: _warmOrange),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '营业状态',
                  style: TextStyle(
                    color: _textColor,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  _isOpen ? '营业中' : '休息中',
                  style: const TextStyle(color: _mutedColor, fontSize: 13),
                ),
              ],
            ),
          ),
          Switch(
            value: _isOpen,
            activeThumbColor: _warmOrange,
            onChanged: (value) {
              setState(() {
                _isOpen = value;
              });
            },
          ),
        ],
      ),
    );
  }

  Widget _buildFamilyEntry() {
    final isFamilyShop = _visibilityType == 'FAMILY';
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: _lineColor),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: _softOrange,
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.home_outlined, color: _warmOrange),
        ),
        title: const Text(
          '家庭管理',
          style: TextStyle(color: _textColor, fontWeight: FontWeight.w600),
        ),
        subtitle: Text(
          isFamilyShop
              ? '${_familyName?.isNotEmpty == true ? _familyName : '未命名家庭'} · 家庭编号 ${_familyCode?.isNotEmpty == true ? _familyCode : '保存后生成'}'
              : '公开店铺，进入后可开启家庭',
          style: const TextStyle(color: _mutedColor, fontSize: 13),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_pendingFamilyMembers > 0) ...[
              Badge(label: Text('$_pendingFamilyMembers')),
              const SizedBox(width: 10),
            ],
            const Icon(Icons.chevron_right, color: Color(0xFFB8AFA7)),
          ],
        ),
        onTap: () async {
          await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const ShopFamilyPage()),
          );
          if (mounted) {
            _load();
          }
        },
      ),
    );
  }

  Widget _buildFeedbackEntry() {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: _lineColor),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: _softOrange,
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.chat_bubble_outline, color: _warmOrange),
        ),
        title: const Text(
          '留言建议',
          style: TextStyle(color: _textColor, fontWeight: FontWeight.w600),
        ),
        subtitle: const Text(
          '查看顾客反馈并回复',
          style: TextStyle(color: _mutedColor, fontSize: 13),
        ),
        trailing: const Icon(Icons.chevron_right, color: Color(0xFFB8AFA7)),
        onTap: () {
          Navigator.of(
            context,
          ).push(MaterialPageRoute(builder: (_) => const FeedbackManagePage()));
        },
      ),
    );
  }

  Future<void> _uploadCover() async {
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
      final url = await shopApi.uploadShopImage(image.path);
      if (mounted) {
        setState(() => _coverImageUrl = url);
        _showMessage('图片已上传，记得保存店铺资料');
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

  Future<void> _save() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      _showMessage('请填写店名');
      return;
    }

    setState(() => _saving = true);
    try {
      await shopApi.updateMyShop(
        name: name,
        description: _descriptionController.text.trim(),
        coverImageUrl: _coverImageUrl,
        address: _addressController.text.trim(),
        phone: _phoneController.text.trim(),
        businessStatus: _isOpen ? 'OPEN' : 'CLOSED',
        visibilityType: _visibilityType,
        familyName: _familyName,
        familyJoinMode: _familyJoinMode,
        familyImageUrl: _familyImageUrl,
      );
      if (mounted) {
        _showMessage(_isOpen ? '店铺资料已保存，当前营业中' : '店铺资料已保存，当前休息中');
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
}
