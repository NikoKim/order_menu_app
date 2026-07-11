import 'package:flutter/material.dart';

import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import 'auth_page.dart';
import 'edit_profile_page.dart';
import 'feedback_page.dart';
import 'my_families_page.dart';
import 'orders_page.dart';
import 'chat_settings_page.dart';
import 'message_center_page.dart';

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key, required this.api});

  final CustomerApi api;

  static const Color _warmOrange = Color(0xFFF28C28);
  static const Color _softOrange = Color(0xFFFFF4E8);
  static const Color _borderColor = Color(0xFFF0E7DE);
  static const Color _textDark = Color(0xFF242424);
  static const Color _textMuted = Color(0xFF77716B);
  static const Color _bgColor = Color(0xFFF7F7F7); // 统一的背景底色

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    return AnimatedBuilder(
      animation: services.auth,
      builder: (context, _) {
        final loggedIn = services.auth.isLoggedIn;
        return Scaffold(
          backgroundColor: _bgColor,
          appBar: AppBar(
            title: const Text('我的', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 20)),
            backgroundColor: _bgColor,
            surfaceTintColor: Colors.transparent,
            elevation: 0,
            centerTitle: false,
          ),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
            children: [
              if (loggedIn)
                _UserInfoCard(auth: services.auth)
              else
                _GuestCard(
                  onLogin: () {
                    Navigator.of(
                      context,
                    ).push(MaterialPageRoute(builder: (_) => const AuthPage()));
                  },
                ),
              const SizedBox(height: 16),
              _MenuSection(
                children: [
                  _ProfileMenuTile(
                    icon: Icons.notifications_active_outlined,
                    title: '消息通知',
                    subtitle: '查看订单、家庭和反馈处理提醒',
                    enabled: loggedIn,
                    destination: const MessageCenterPage(),
                  ),
                  const _SectionDivider(),
                  _ProfileMenuTile(
                    icon: Icons.lock_outline,
                    title: '聊天设置',
                    subtitle: '管理私聊和家庭群聊权限',
                    enabled: loggedIn,
                    destination: const ChatSettingsPage(),
                  ),
                  const _SectionDivider(),
                  _ProfileMenuTile(
                    icon: Icons.receipt_long_outlined,
                    title: '我的订单',
                    subtitle: '查看点餐记录与订单状态',
                    enabled: loggedIn,
                    destination: OrdersPage(api: api),
                  ),
                  const _SectionDivider(),
                  _ProfileMenuTile(
                    icon: Icons.edit_note_outlined,
                    title: '修改个人信息',
                    subtitle: '更新昵称、手机号等资料',
                    enabled: loggedIn,
                    destination: const EditProfilePage(),
                  ),
                  const _SectionDivider(),
                  _ProfileMenuTile(
                    icon: Icons.chat_bubble_outline,
                    title: '留言建议',
                    subtitle: '告诉商家或平台你的用餐体验',
                    enabled: loggedIn,
                    destination: FeedbackPage(api: api),
                  ),
                  const _SectionDivider(),
                  _ProfileMenuTile(
                    icon: Icons.home_outlined,
                    title: '我的家庭',
                    subtitle: '输入家庭号，加入或退出家庭',
                    enabled: loggedIn,
                    destination: MyFamiliesPage(api: api),
                  ),
                ],
              ),
              const AppFooter(),
            ],
          ),
        );
      },
    );
  }
}

class _GuestCard extends StatelessWidget {
  const _GuestCard({required this.onLogin});

  final VoidCallback onLogin;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 68,
            height: 68,
            decoration: const BoxDecoration(
              color: ProfilePage._softOrange,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.person_outline,
              color: ProfilePage._warmOrange,
              size: 36,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '未登录',
                  style: TextStyle(
                    color: ProfilePage._textDark,
                    fontSize: 20,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  '登录后可查看订单、保存个人信息',
                  style: TextStyle(color: ProfilePage._textMuted, fontSize: 13),
                ),
                const SizedBox(height: 12),
                FilledButton(
                  style: FilledButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  onPressed: onLogin,
                  child: const Text('登录 / 注册', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _UserInfoCard extends StatelessWidget {
  const _UserInfoCard({required this.auth});

  final AuthController auth;

  @override
  Widget build(BuildContext context) {
    final user = auth.user;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 68,
            height: 68,
            clipBehavior: Clip.antiAlias,
            decoration: const BoxDecoration(
              color: ProfilePage._softOrange,
              shape: BoxShape.circle,
            ),
            child: user?.avatarUrl?.isNotEmpty == true
                ? Image.network(
              resolveCustomerImageUrl(user!.avatarUrl!),
              fit: BoxFit.cover,
              loadingBuilder: (context, child, progress) {
                if (progress == null) {
                  return TweenAnimationBuilder<double>(
                    tween: Tween(begin: 0.0, end: 1.0),
                    duration: const Duration(milliseconds: 300),
                    builder: (context, value, child) =>
                        Opacity(opacity: value, child: child),
                    child: child,
                  );
                }
                return const Center(
                  child: CircularProgressIndicator(
                    color: ProfilePage._warmOrange,
                    strokeWidth: 2,
                  ),
                );
              },
              errorBuilder: (_, _, _) =>
              const Icon(Icons.person, color: ProfilePage._warmOrange, size: 36),
            )
                : const Icon(Icons.person, color: ProfilePage._warmOrange, size: 36),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  user?.nickname ?? '已登录用户',
                  style: const TextStyle(
                    color: ProfilePage._textDark,
                    fontSize: 20,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  user?.phone?.isNotEmpty == true
                      ? '手机号：${user!.phone}'
                      : '暂未填写手机号',
                  style: const TextStyle(
                    color: ProfilePage._textMuted,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    const _LoginStatusBadge(),
                    const Spacer(),
                    InkWell(
                      borderRadius: BorderRadius.circular(4),
                      onTap: auth.logout,
                      child: const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                        child: Row(
                          children: [
                            Icon(Icons.logout, size: 14, color: ProfilePage._textMuted),
                            SizedBox(width: 4),
                            Text('退出', style: TextStyle(fontSize: 12, color: ProfilePage._textMuted)),
                          ],
                        ),
                      ),
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
}

class _LoginStatusBadge extends StatelessWidget {
  const _LoginStatusBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: ProfilePage._softOrange,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: const Color(0xFFFFD7AA)),
      ),
      child: const Text(
        '已登录',
        style: TextStyle(
          color: ProfilePage._warmOrange,
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _MenuSection extends StatelessWidget {
  const _MenuSection({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(children: children),
    );
  }
}

class _ProfileMenuTile extends StatelessWidget {
  const _ProfileMenuTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.destination,
    required this.enabled,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final Widget destination;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      enabled: enabled,
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
      leading: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: ProfilePage._softOrange,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: ProfilePage._warmOrange, size: 22),
      ),
      title: Text(
        title,
        style: const TextStyle(
          color: ProfilePage._textDark,
          fontSize: 16,
          fontWeight: FontWeight.bold,
        ),
      ),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Text(
          enabled ? subtitle : '请先登录',
          style: const TextStyle(color: ProfilePage._textMuted, fontSize: 13),
        ),
      ),
      trailing: const Icon(Icons.arrow_forward_ios, color: Color(0xFFD4CDC7), size: 16),
      onTap: enabled
          ? () {
        Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => destination));
      }
          : null,
    );
  }
}

class _SectionDivider extends StatelessWidget {
  const _SectionDivider();

  @override
  Widget build(BuildContext context) {
    return const Divider(
      height: 1,
      thickness: 1,
      indent: 20 + 44 + 16, // 精确对齐文本起始位置
      endIndent: 20,
      color: ProfilePage._borderColor,
    );
  }
}
