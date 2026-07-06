import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../app_mock_data.dart';
import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

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
  String? _avatarUrl;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final user = AppScope.of(context).auth.user;
    _nicknameController.text = user?.nickname ?? '';
    _phoneController.text = user?.phone ?? '';
    _avatarUrl ??= user?.avatarUrl;
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
    if (image == null) {
      return;
    }
    if (!mounted) return;
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
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('修改个人信息')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: [
          Center(
            child: InkWell(
              customBorder: const CircleBorder(),
              onTap: _uploadingAvatar ? null : _pickAvatar,
              child: Stack(
                alignment: Alignment.bottomRight,
                children: [
                  Container(
                    width: 92,
                    height: 92,
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
                        : const Icon(Icons.person, color: warmOrange, size: 52),
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
                            Icons.camera_alt,
                            color: Colors.white,
                            size: 17,
                          ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 28),
          TextField(
            controller: _nicknameController,
            maxLength: 50,
            decoration: const InputDecoration(
              labelText: '昵称',
              prefixIcon: Icon(Icons.person_outline),
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
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: Text(_saving ? '保存中...' : '保存'),
          ),
          const AppFooter(),
        ],
      ),
    );
  }
}
