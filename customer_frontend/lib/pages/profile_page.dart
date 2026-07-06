import 'package:flutter/material.dart';

import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import 'auth_page.dart';
import 'edit_profile_page.dart';
import 'feedback_page.dart';
import 'my_families_page.dart';
import 'orders_page.dart';

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key, required this.api});

  final CustomerApi api;

  static const Color _warmOrange = Color(0xFFF28C28);
  static const Color _softOrange = Color(0xFFFFF4E8);
  static const Color _borderColor = Color(0xFFF0E7DE);
  static const Color _textDark = Color(0xFF242424);
  static const Color _textMuted = Color(0xFF77716B);

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    return AnimatedBuilder(
      animation: services.auth,
      builder: (context, _) {
        final loggedIn = services.auth.isLoggedIn;
        return Scaffold(
          backgroundColor: Colors.white,
          appBar: AppBar(title: const Text('我的')),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
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
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: ProfilePage._softOrange,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: ProfilePage._borderColor),
      ),
      child: Row(
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: const BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.person_outline,
              color: ProfilePage._warmOrange,
              size: 36,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '未登录',
                  style: TextStyle(
                    color: ProfilePage._textDark,
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  '登录后可查看订单、保存个人信息',
                  style: TextStyle(color: ProfilePage._textMuted, fontSize: 14),
                ),
                const SizedBox(height: 12),
                FilledButton(onPressed: onLogin, child: const Text('登录/注册')),
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
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: ProfilePage._softOrange,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: ProfilePage._borderColor),
      ),
      child: Row(
        children: [
          Container(
            width: 64,
            height: 64,
            clipBehavior: Clip.antiAlias,
            decoration: const BoxDecoration(
              color: ProfilePage._warmOrange,
              shape: BoxShape.circle,
            ),
            child: user?.avatarUrl?.isNotEmpty == true
                ? Image.network(
                    resolveCustomerImageUrl(user!.avatarUrl!),
                    fit: BoxFit.cover,
                    loadingBuilder: (context, child, progress) =>
                        progress == null
                        ? child
                        : const Center(
                            child: CircularProgressIndicator(
                              color: Colors.white,
                              strokeWidth: 2,
                            ),
                          ),
                    errorBuilder: (_, _, _) =>
                        const Icon(Icons.person, color: Colors.white, size: 36),
                  )
                : const Icon(Icons.person, color: Colors.white, size: 36),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  user?.nickname ?? '已登录用户',
                  style: const TextStyle(
                    color: ProfilePage._textDark,
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  user?.phone?.isNotEmpty == true
                      ? '手机号：${user!.phone}'
                      : '暂未填写手机号',
                  style: const TextStyle(
                    color: ProfilePage._textMuted,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    const _LoginStatusBadge(),
                    const SizedBox(width: 8),
                    TextButton.icon(
                      onPressed: auth.logout,
                      icon: const Icon(Icons.logout, size: 18),
                      label: const Text('退出登录'),
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
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFFFD7AA)),
      ),
      child: const Padding(
        padding: EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        child: Text(
          '当前为已登录状态',
          style: TextStyle(
            color: ProfilePage._warmOrange,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
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
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: ProfilePage._borderColor),
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
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      leading: Container(
        width: 42,
        height: 42,
        decoration: BoxDecoration(
          color: ProfilePage._softOrange,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(icon, color: ProfilePage._warmOrange),
      ),
      title: Text(
        title,
        style: const TextStyle(
          color: ProfilePage._textDark,
          fontSize: 16,
          fontWeight: FontWeight.w600,
        ),
      ),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Text(
          enabled ? subtitle : '请先登录',
          style: const TextStyle(color: ProfilePage._textMuted, fontSize: 13),
        ),
      ),
      trailing: const Icon(Icons.chevron_right, color: Color(0xFFB8AFA7)),
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
      indent: 16,
      endIndent: 16,
      color: ProfilePage._borderColor,
    );
  }
}
