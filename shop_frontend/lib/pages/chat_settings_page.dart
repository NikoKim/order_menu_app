import 'package:flutter/material.dart';

import '../services/shop_api_service.dart';
import '../widgets/app_notice.dart';

class ChatSettingsPage extends StatefulWidget {
  const ChatSettingsPage({super.key});

  @override
  State<ChatSettingsPage> createState() => _ChatSettingsPageState();
}

class _ChatSettingsPageState extends State<ChatSettingsPage> {
  bool? _allowPrivateChat;
  bool _showPhone = true;
  bool _showJoinedFamilies = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    shopApi
        .fetchCurrentUser()
        .then((user) {
          if (mounted) {
            setState(() {
              _allowPrivateChat = user.allowPrivateChat;
              _showPhone = user.showPhone;
              _showJoinedFamilies = user.showJoinedFamilies;
            });
          }
        })
        .catchError((_) {
          if (mounted) AppNotice.show(context, '聊天设置加载失败');
        });
  }

  Future<void> _update({
    bool? allowPrivateChat,
    bool? showPhone,
    bool? showJoinedFamilies,
  }) async {
    final previousPrivate = _allowPrivateChat!;
    final previousPhone = _showPhone;
    final previousFamilies = _showJoinedFamilies;
    setState(() {
      _allowPrivateChat = allowPrivateChat ?? _allowPrivateChat;
      _showPhone = showPhone ?? _showPhone;
      _showJoinedFamilies = showJoinedFamilies ?? _showJoinedFamilies;
      _saving = true;
    });
    try {
      await shopApi.updatePrivateChatSetting(
        allowPrivateChat: _allowPrivateChat!,
        showPhone: _showPhone,
        showJoinedFamilies: _showJoinedFamilies,
      );
      if (mounted) AppNotice.show(context, '聊天设置已保存');
    } catch (error) {
      if (mounted) {
        setState(() {
          _allowPrivateChat = previousPrivate;
          _showPhone = previousPhone;
          _showJoinedFamilies = previousFamilies;
        });
        AppNotice.show(
          context,
          error is ApiException ? error.message : '保存失败，请重试',
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F6F8),
    appBar: AppBar(title: const Text('聊天设置')),
    body: _allowPrivateChat == null
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: SwitchListTile(
                  secondary: const Icon(
                    Icons.chat_outlined,
                    color: Color(0xFFF28C28),
                  ),
                  title: const Text('允许私聊'),
                  subtitle: const Text('开启后顾客可向店铺发起私聊'),
                  value: _allowPrivateChat!,
                  onChanged: _saving
                      ? null
                      : (value) => _update(allowPrivateChat: value),
                ),
              ),
              const SizedBox(height: 12),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  children: [
                    SwitchListTile(
                      secondary: const Icon(
                        Icons.phone_outlined,
                        color: Color(0xFFF28C28),
                      ),
                      title: const Text('公开联系电话'),
                      subtitle: const Text('关闭后，私聊和群成员资料均不显示手机号'),
                      value: _showPhone,
                      onChanged: _saving
                          ? null
                          : (value) => _update(showPhone: value),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      secondary: const Icon(
                        Icons.family_restroom,
                        color: Color(0xFFF28C28),
                      ),
                      title: const Text('公开已加入家庭'),
                      subtitle: const Text('关闭后，私聊和群成员资料均不展示家庭列表'),
                      value: _showJoinedFamilies,
                      onChanged: _saving
                          ? null
                          : (value) => _update(showJoinedFamilies: value),
                    ),
                  ],
                ),
              ),
            ],
          ),
  );
}
