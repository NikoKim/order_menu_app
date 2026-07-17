import 'package:flutter/material.dart';

import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_notice.dart';
import 'moment_privacy_page.dart';

class ChatSettingsPage extends StatefulWidget {
  const ChatSettingsPage({super.key});

  @override
  State<ChatSettingsPage> createState() => _ChatSettingsPageState();
}

class _ChatSettingsPageState extends State<ChatSettingsPage> {
  bool _saving = false;

  Future<void> _save({
    bool? allowPrivateChat,
    bool? autoJoinFamilyChat,
    bool? showJoinedFamilies,
    bool? showPhone,
  }) async {
    final auth = AppScope.of(context).auth;
    final user = auth.user;
    if (user == null || _saving) return;
    setState(() => _saving = true);
    try {
      await auth.updatePrivacySettings(
        allowPrivateChat: allowPrivateChat ?? user.allowPrivateChat,
        autoJoinFamilyChat: autoJoinFamilyChat ?? user.autoJoinFamilyChat,
        showJoinedFamilies: showJoinedFamilies ?? user.showJoinedFamilies,
        showPhone: showPhone ?? user.showPhone,
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
    if (user == null) {
      return const Scaffold(body: Center(child: Text('请先登录')));
    }
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        title: const Text('隐私设置'),
        backgroundColor: const Color(0xFFF5F5F5),
        surfaceTintColor: Colors.transparent,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const _SectionTitle('聊天隐私设置'),
          _SettingsCard(
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
                subtitle: const Text('关闭后，需要确认家庭群聊邀请。'),
                value: user.autoJoinFamilyChat,
                onChanged: _saving
                    ? null
                    : (value) => _save(autoJoinFamilyChat: value),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const _SectionTitle('个人信息隐私设置'),
          _SettingsCard(
            children: [
              SwitchListTile(
                secondary: const Icon(Icons.home_outlined),
                title: const Text('展示已加入家庭'),
                subtitle: const Text('控制其他人在资料页能否看到你加入的家庭。'),
                value: user.showJoinedFamilies,
                onChanged: _saving
                    ? null
                    : (value) => _save(showJoinedFamilies: value),
              ),
              const Divider(height: 1, indent: 56),
              SwitchListTile(
                secondary: const Icon(Icons.phone_outlined),
                title: const Text('展示手机号'),
                subtitle: const Text('控制私聊和家庭群成员资料中是否显示手机号。'),
                value: user.showPhone,
                onChanged: _saving ? null : (value) => _save(showPhone: value),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const _SectionTitle('动态隐私设置'),
          _SettingsCard(
            children: [
              ListTile(
                leading: const Icon(Icons.dynamic_feed_outlined),
                title: const Text('动态页可见人群'),
                subtitle: const Text('设置互聊好友、家庭成员、公开或私密'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => const MyMomentPrivacyPage(),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(left: 4, bottom: 8),
    child: Text(
      text,
      style: Theme.of(context).textTheme.titleSmall?.copyWith(
        color: const Color(0xFF555555),
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
    ),
    clipBehavior: Clip.antiAlias,
    child: Column(children: children),
  );
}
