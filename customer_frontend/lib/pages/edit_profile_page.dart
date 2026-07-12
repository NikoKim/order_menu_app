import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../app_mock_data.dart';
import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import '../widgets/app_notice.dart';

class EditProfilePage extends StatefulWidget {
  const EditProfilePage({super.key});

  @override
  State<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends State<EditProfilePage> {
  final _nicknameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _picker = ImagePicker();
  bool _saving = false;
  bool _uploadingAvatar = false;
  bool _initialized = false;
  String? _avatarUrl;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    final user = AppScope.of(context).auth.user;
    _nicknameController.text = user?.nickname ?? '';
    _phoneController.text = user?.phone ?? '';
    _avatarUrl = user?.avatarUrl;
    _initialized = true;
  }

  @override
  void dispose() {
    _nicknameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final nickname = _nicknameController.text.trim();
    if (nickname.isEmpty) {
      _showSnack('请填写昵称');
      return;
    }
    setState(() => _saving = true);
    try {
      await AppScope.of(context).auth.updateMe(
        nickname: nickname,
        phone: _phoneController.text.trim(),
        avatarUrl: _avatarUrl,
      );
      if (!mounted) return;
      _showSnack('个人信息已保存');
      Navigator.of(context).pop();
    } catch (error) {
      _showSnack(error.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _pickAvatar() async {
    final image = await _picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1024,
      imageQuality: 88,
    );
    if (image == null || !mounted) return;

    final auth = AppScope.of(context).auth;
    setState(() => _uploadingAvatar = true);
    try {
      final url = await auth.uploadAvatar(image.path);
      if (!mounted) return;
      setState(() => _avatarUrl = url);
      _showSnack('头像已更新');
    } catch (error) {
      _showSnack(error.toString());
    } finally {
      if (mounted) setState(() => _uploadingAvatar = false);
    }
  }

  void _showSnack(String message) {
    AppNotice.show(context, message);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      appBar: AppBar(
        title: const Text('修改个人信息'),
        backgroundColor: const Color(0xFFF7F7F7),
        foregroundColor: pageTextColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          children: [
            const _PageHeaderCard(
              icon: Icons.person_outline_rounded,
              title: '完善个人资料',
              message: '更新头像、昵称和手机号，让商家更容易识别你的订单。',
            ),
            const SizedBox(height: 12),
            _WhiteCard(
              child: Column(
                children: [
                  InkWell(
                    customBorder: const CircleBorder(),
                    onTap: _uploadingAvatar ? null : _pickAvatar,
                    child: Stack(
                      alignment: Alignment.bottomRight,
                      children: [
                        Container(
                          width: 96,
                          height: 96,
                          clipBehavior: Clip.antiAlias,
                          decoration: const BoxDecoration(
                            color: softOrange,
                            shape: BoxShape.circle,
                          ),
                          child: _avatarUrl?.isNotEmpty == true
                              ? Image.network(
                                  resolveCustomerImageUrl(_avatarUrl!),
                                  fit: BoxFit.cover,
                                  loadingBuilder: (context, child, progress) =>
                                      progress == null
                                      ? child
                                      : const Center(
                                          child: CircularProgressIndicator(
                                            color: warmOrange,
                                            strokeWidth: 2,
                                          ),
                                        ),
                                  errorBuilder: (_, _, _) => const Icon(
                                    Icons.person,
                                    color: warmOrange,
                                    size: 52,
                                  ),
                                )
                              : const Icon(
                                  Icons.person,
                                  color: warmOrange,
                                  size: 52,
                                ),
                        ),
                        Container(
                          width: 34,
                          height: 34,
                          decoration: BoxDecoration(
                            color: warmOrange,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 3),
                          ),
                          child: _uploadingAvatar
                              ? const Padding(
                                  padding: EdgeInsets.all(8),
                                  child: CircularProgressIndicator(
                                    color: Colors.white,
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(
                                  Icons.camera_alt_rounded,
                                  color: Colors.white,
                                  size: 17,
                                ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '点击更换头像',
                    style: TextStyle(
                      color: pageMutedColor,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            _WhiteCard(
              child: Column(
                children: [
                  TextField(
                    controller: _nicknameController,
                    maxLength: 50,
                    decoration: const InputDecoration(
                      labelText: '昵称',
                      prefixIcon: Icon(Icons.person_outline_rounded),
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _phoneController,
                    maxLength: 30,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(
                      labelText: '手机号',
                      prefixIcon: Icon(Icons.phone_outlined),
                    ),
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: _saving ? null : _save,
                      child: Text(_saving ? '保存中...' : '保存'),
                    ),
                  ),
                ],
              ),
            ),
            const AppFooter(),
          ],
        ),
      ),
    );
  }
}

class _PageHeaderCard extends StatelessWidget {
  const _PageHeaderCard({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return _WhiteCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: softOrange,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: warmOrange, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: pageTextColor,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  message,
                  style: const TextStyle(
                    color: pageMutedColor,
                    fontSize: 13,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _WhiteCard extends StatelessWidget {
  const _WhiteCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: child,
    );
  }
}
