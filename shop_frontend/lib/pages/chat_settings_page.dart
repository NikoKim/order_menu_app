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
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    shopApi
        .fetchCurrentUser()
        .then((user) {
          if (mounted) {
            setState(() => _allowPrivateChat = user.allowPrivateChat);
          }
        })
        .catchError((_) {
          if (mounted) AppNotice.show(context, '聊天设置加载失败');
        });
  }

  Future<void> _update(bool value) async {
    setState(() {
      _allowPrivateChat = value;
      _saving = true;
    });
    try {
      await shopApi.updatePrivateChatSetting(allowPrivateChat: value);
      if (mounted) AppNotice.show(context, '聊天设置已保存');
    } catch (error) {
      if (mounted) {
        setState(() => _allowPrivateChat = !value);
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
                  onChanged: _saving ? null : _update,
                ),
              ),
            ],
          ),
  );
}
