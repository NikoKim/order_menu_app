import 'package:flutter/material.dart';

import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';

class ChatSettingsPage extends StatefulWidget {
  const ChatSettingsPage({super.key});

  @override
  State<ChatSettingsPage> createState() => _ChatSettingsPageState();
}

class _ChatSettingsPageState extends State<ChatSettingsPage> {
  bool _saving = false;

  Future<void> _save({bool? allowPrivateChat, bool? autoJoinFamilyChat}) async {
    final auth = AppScope.of(context).auth;
    final user = auth.user;
    if (user == null || _saving) return;
    setState(() => _saving = true);
    try {
      await auth.updateChatSettings(
        allowPrivateChat: allowPrivateChat ?? user.allowPrivateChat,
        autoJoinFamilyChat: autoJoinFamilyChat ?? user.autoJoinFamilyChat,
      );
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = AppScope.of(context).auth.user;
    if (user == null) return const Scaffold(body: Center(child: Text('请先登录')));
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        title: const Text('聊天设置'),
        backgroundColor: const Color(0xFFF5F5F5),
        surfaceTintColor: Colors.transparent,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
            ),
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [
                SwitchListTile(
                  secondary: const Icon(Icons.chat_bubble_outline),
                  title: const Text('允许私聊'),
                  subtitle: const Text('关闭后，其他用户无法向你发起新的私聊。'),
                  value: user.allowPrivateChat,
                  onChanged: _saving
                      ? null
                      : (value) => _save(allowPrivateChat: value),
                ),
                const Divider(height: 1, indent: 56),
                SwitchListTile(
                  secondary: const Icon(Icons.groups_outlined),
                  title: const Text('自动加入家庭群聊'),
                  subtitle: const Text('关闭后，需要确认加入家庭群聊邀请。'),
                  value: user.autoJoinFamilyChat,
                  onChanged: _saving
                      ? null
                      : (value) => _save(autoJoinFamilyChat: value),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
