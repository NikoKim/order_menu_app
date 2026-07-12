import 'dart:async';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../services/websocket_service.dart';
import '../widgets/app_notice.dart';
import 'feedback_manage_page.dart';
import 'shop_family_page.dart';
import 'message_center_page.dart';
import 'chat_settings_page.dart';

const Color _warmOrange = Color(0xFFF28C28);
const Color _softOrange = Color(0xFFFFF4E8);
const Color _pageBgColor = Color(0xFFF5F6F8);
const Color _lineColor = Color(0xFFF0E7DE);
const Color _textColor = Color(0xFF242424);
const Color _mutedColor = Color(0xFF77716B);

class ShopProfilePage extends StatefulWidget {
  const ShopProfilePage({super.key, this.onLogout, this.onBadgeChanged});

  final Future<void> Function()? onLogout;
  final VoidCallback? onBadgeChanged;

  @override
  State<ShopProfilePage> createState() => _ShopProfilePageState();
}

class _ShopProfilePageState extends State<ShopProfilePage>
    with WidgetsBindingObserver {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _descriptionController = TextEditingController();
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _addressController = TextEditingController();

  bool _isOpen = true;
  bool _allowPrivateChat = true;
  bool _loading = true;
  bool _saving = false;
  String? _coverImageUrl;
  String? _familyName;
  String? _familyCode;
  String? _familyJoinMode;
  String? _familyImageUrl;
  String? _visibilityType;

  // 用于存储通知徽标数量
  int _pendingFamilyMembers = 0;
  int _pendingFeedback = 0;
  int _unreadMessageNotifications = 0;

  String? _error;
  StreamSubscription? _wsSubscription;
  StreamSubscription<bool>? _connectionSubscription;
  final ImagePicker _imagePicker = ImagePicker();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _load();

    _wsSubscription = ShopWebSocketService.instance.messageStream.listen((msg) {
      if (!mounted) return;

      if (msg.module == 'NOTIFICATION') {
        final unread = int.tryParse('${msg.data['unreadCount'] ?? ''}');
        if (unread != null) {
          setState(() => _unreadMessageNotifications = unread);
          widget.onBadgeChanged?.call();
        }
      }

      final shouldRefresh = switch (msg.module) {
        'FEEDBACK' => {'CREATED', 'UPDATED', 'REPLIED'}.contains(msg.action),
        'FAMILY' => {
          'MEMBER_APPLIED',
          'MEMBER_APPROVED',
          'MEMBER_REJECTED',
        }.contains(msg.action),
        'NOTIFICATION' => true,
        'SHOP' => true,
        _ => false,
      };

      if (shouldRefresh) {
        _load(silent: true);
      }
    });
    _connectionSubscription = ShopWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) => _load(silent: true));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load(silent: true);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();

    _nameController.dispose();
    _descriptionController.dispose();
    _phoneController.dispose();
    _addressController.dispose();

    super.dispose();
  }

  Future _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _error = null;
      });
      widget.onBadgeChanged?.call();
      widget.onBadgeChanged?.call();
    }

    try {
      final results = await Future.wait([
        shopApi.fetchMyShop(),
        shopApi.fetchCurrentUser(),
        shopApi.fetchNotifications(),
        shopApi.fetchFeedback(status: 'PENDING', pageSize: 1),
        shopApi.fetchUnreadMessageNotificationCount(),
      ]);

      if (!mounted) return;

      final shop = results[0] as ShopInfo;
      final currentUser = results[1] as CurrentUser;
      final notifications = results[2] as ShopNotificationInfo;
      final pendingFeedbackData = results[3] as PageResult<FeedbackInfo>;

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
        _pendingFeedback = pendingFeedbackData.total;
        _unreadMessageNotifications = results[4] as int;

        _isOpen = shop.isOpen;
        _allowPrivateChat = currentUser.allowPrivateChat;
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
    return Scaffold(
      backgroundColor: _pageBgColor,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
        title: const Text(
          '店铺资料',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      body: RefreshIndicator(
        color: _warmOrange,
        backgroundColor: Colors.white,
        onRefresh: _load,
        child: _buildBody(),
      ),
      bottomNavigationBar: _buildBottomBar(),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          _SkeletonBox(width: double.infinity, height: 180, borderRadius: 12),
          SizedBox(height: 16),
          _SkeletonBox(width: double.infinity, height: 320, borderRadius: 12),
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
        _buildImagePicker(),
        const SizedBox(height: 16),

        // 模块 1：基本信息
        _BaseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const _SectionTitle(title: '基本信息', icon: Icons.storefront),
              const SizedBox(height: 16),
              _LabeledField(
                label: '店铺名称',
                child: TextFormField(
                  controller: _nameController,
                  decoration: _inputDecoration('请输入店名'),
                ),
              ),
              const SizedBox(height: 16),
              _LabeledField(
                label: '联系电话',
                child: TextFormField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  decoration: _inputDecoration('请输入联系电话'),
                ),
              ),
              const SizedBox(height: 16),
              _LabeledField(
                label: '店铺地址',
                child: TextFormField(
                  controller: _addressController,
                  maxLines: 2,
                  decoration: _inputDecoration('请输入详细地址'),
                ),
              ),
              const SizedBox(height: 16),
              _LabeledField(
                label: '店铺简介',
                child: TextFormField(
                  controller: _descriptionController,
                  maxLines: 3,
                  decoration: _inputDecoration('用一段话介绍一下你的店铺吧...'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // 模块 2：经营设置与拓展功能
        _BaseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const _SectionTitle(
                title: '经营与管理',
                icon: Icons.settings_outlined,
              ),
              const SizedBox(height: 16),
              _buildOpenStatusSwitch(),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Divider(color: _lineColor, height: 1),
              ),
              _buildPrivateChatSwitch(),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Divider(color: _lineColor, height: 1),
              ),
              _buildFamilyEntry(),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Divider(color: _lineColor, height: 1),
              ),
              _buildFeedbackEntry(),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Divider(color: _lineColor, height: 1),
              ),
              _buildMessageNotificationEntry(),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildBottomBar() {
    if (_loading) return const SizedBox.shrink();

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
        child: Row(
          children: [
            Expanded(
              flex: 1,
              child: OutlinedButton(
                onPressed: widget.onLogout,
                style: OutlinedButton.styleFrom(
                  foregroundColor: _mutedColor,
                  side: const BorderSide(color: Color(0xFFE8E8E8)),
                  backgroundColor: const Color(0xFFF9FAFB),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: const Text(
                  '退出登录',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              flex: 2,
              child: FilledButton(
                onPressed: _saving ? null : _save,
                style: FilledButton.styleFrom(
                  backgroundColor: _warmOrange,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: Text(
                  _saving ? '保存中...' : '保存资料',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImagePicker() {
    final imageUrl = _coverImageUrl;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: _uploadCover,
      child: Container(
        height: 180,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: imageUrl != null ? Colors.transparent : _lineColor,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: imageUrl != null && imageUrl.isNotEmpty
            ? Image.network(
                _absoluteImageUrl(imageUrl),
                fit: BoxFit.cover,
                loadingBuilder: (context, child, loadingProgress) {
                  if (loadingProgress == null) return child;
                  return const _SkeletonBox(
                    width: double.infinity,
                    height: double.infinity,
                    borderRadius: 12,
                  );
                },
                errorBuilder: (_, __, ___) => _buildImagePlaceholder(),
              )
            : _buildImagePlaceholder(),
      ),
    );
  }

  Widget _buildImagePlaceholder() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: const BoxDecoration(
            color: _softOrange,
            shape: BoxShape.circle,
          ),
          child: const Icon(
            Icons.add_photo_alternate_outlined,
            color: _warmOrange,
            size: 36,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          '点击上传店铺封面图',
          style: TextStyle(
            color: _mutedColor,
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }

  Widget _buildOpenStatusSwitch() {
    return Row(
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: _softOrange,
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.schedule, color: _warmOrange, size: 20),
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
                  fontWeight: FontWeight.w700,
                  fontSize: 15,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _isOpen ? '顾客可正常点单' : '店铺休息中，顾客无法下单',
                style: const TextStyle(color: _mutedColor, fontSize: 13),
              ),
            ],
          ),
        ),
        Switch(
          value: _isOpen,
          activeColor: Colors.white,
          activeTrackColor: _warmOrange,
          onChanged: _saving ? null : _updateBusinessStatus,
        ),
      ],
    );
  }

  Widget _buildPrivateChatSwitch() {
    return InkWell(
      onTap: () async {
        await Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => const ChatSettingsPage()));
        if (mounted) _load(silent: true);
      },
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: _softOrange,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.chat_outlined,
                color: _warmOrange,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '聊天设置',
                    style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                  ),
                  SizedBox(height: 4),
                  Text(
                    '管理允许私聊等会话权限',
                    style: TextStyle(color: _mutedColor, fontSize: 13),
                  ),
                ],
              ),
            ),
            Text(
              _allowPrivateChat ? '已开启' : '已关闭',
              style: const TextStyle(color: _mutedColor),
            ),
            const SizedBox(width: 8),
            const Icon(Icons.chevron_right, color: _mutedColor),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageNotificationEntry() => InkWell(
    borderRadius: BorderRadius.circular(8),
    onTap: () async {
      await Navigator.of(
        context,
      ).push(MaterialPageRoute(builder: (_) => const MessageCenterPage()));
      if (mounted) _load(silent: true);
    },
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: _softOrange,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(
              Icons.notifications_outlined,
              color: _warmOrange,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '消息通知',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                ),
                SizedBox(height: 4),
                Text(
                  '查看群聊申请和系统提醒',
                  style: TextStyle(color: _mutedColor, fontSize: 13),
                ),
              ],
            ),
          ),
          if (_unreadMessageNotifications > 0) ...[
            Badge(
              label: Text(
                _unreadMessageNotifications > 99
                    ? '99+'
                    : '$_unreadMessageNotifications',
              ),
            ),
            const SizedBox(width: 10),
          ],
          const Icon(Icons.chevron_right, color: _mutedColor),
        ],
      ),
    ),
  );

  Widget _buildFamilyEntry() {
    final isFamilyShop = _visibilityType == 'FAMILY';
    return InkWell(
      onTap: () async {
        await Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => const ShopFamilyPage()));
        if (mounted) _load();
      },
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: _softOrange,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.home_outlined,
                color: _warmOrange,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '家庭管理',
                    style: TextStyle(
                      color: _textColor,
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    isFamilyShop
                        ? '${_familyName?.isNotEmpty == true ? _familyName : '未命名家庭'} · 编号 ${_familyCode ?? '待生成'}'
                        : '公开店铺模式，点击可开启家庭',
                    style: const TextStyle(color: _mutedColor, fontSize: 13),
                  ),
                ],
              ),
            ),
            // 【新增】家庭管理红点徽标，超过9显示...
            if (_pendingFamilyMembers > 0) ...[
              Badge(
                label: Text(
                  _pendingFamilyMembers > 9 ? '...' : '$_pendingFamilyMembers',
                ),
                backgroundColor: Colors.redAccent,
              ),
              const SizedBox(width: 10),
            ],
            const Icon(Icons.chevron_right, color: Color(0xFFD9D9D9)),
          ],
        ),
      ),
    );
  }

  Widget _buildFeedbackEntry() {
    return InkWell(
      onTap: () async {
        await Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => const FeedbackManagePage()));

        if (mounted) {
          _load(silent: true);
        }
      },
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: _softOrange,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.chat_bubble_outline,
                color: _warmOrange,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text(
                    '留言建议',
                    style: TextStyle(
                      color: _textColor,
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    '查看并回复顾客对店铺的反馈',
                    style: TextStyle(color: _mutedColor, fontSize: 13),
                  ),
                ],
              ),
            ),
            if (_pendingFeedback > 0) ...[
              Badge(
                label: Text(_pendingFeedback > 9 ? '...' : '$_pendingFeedback'),
                backgroundColor: Colors.redAccent,
              ),
              const SizedBox(width: 10),
            ],
            const Icon(Icons.chevron_right, color: Color(0xFFD9D9D9)),
          ],
        ),
      ),
    );
  }

  Future<void> _uploadCover() async {
    final image = await _imagePicker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (image == null) return;

    setState(() => _saving = true);
    try {
      final url = await shopApi.uploadShopImage(image.path);
      if (mounted) {
        setState(() => _coverImageUrl = url);
        _showSnackBar('封面已上传，记得保存资料');
      }
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _updateBusinessStatus(bool value) async {
    final oldValue = _isOpen;

    setState(() {
      _isOpen = value;
      _saving = true;
    });

    try {
      final updated = await shopApi.updateMyShop(
        name: _nameController.text.trim(),
        description: _descriptionController.text.trim(),
        coverImageUrl: _coverImageUrl,
        address: _addressController.text.trim(),
        phone: _phoneController.text.trim(),
        businessStatus: value ? 'OPEN' : 'CLOSED',
        visibilityType: _visibilityType,
        familyName: _familyName,
        familyJoinMode: _familyJoinMode,
        familyImageUrl: _familyImageUrl,
      );

      if (!mounted) return;

      setState(() {
        _isOpen = updated.isOpen;
        _error = null;
      });

      _showSnackBar(updated.isOpen ? '店铺已切换为营业中' : '店铺已切换为休息中');

      // 兜底刷新，后端 WS 到达前也能保证当前页数据正确
      _load(silent: true);
    } on ApiException catch (error) {
      if (!mounted) return;

      setState(() {
        _isOpen = oldValue;
      });

      _showSnackBar(error.message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _save() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      _showSnackBar('请填写店名');
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
        _showSnackBar(_isOpen ? '店铺资料已保存，当前营业中' : '店铺资料已保存，当前休息中');
        _load();
      }
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showSnackBar(String message) {
    AppNotice.show(context, message);
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

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, required this.icon});
  final String title;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 20, color: _warmOrange),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            color: _textColor,
            fontSize: 16,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
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

// --------------------------------------------------------------------------
// 骨架屏组件
// --------------------------------------------------------------------------
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
