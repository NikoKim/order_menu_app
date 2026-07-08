import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../main.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

class MyFamiliesPage extends StatefulWidget {
  const MyFamiliesPage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<MyFamiliesPage> createState() => _MyFamiliesPageState();
}

class _MyFamiliesPageState extends State<MyFamiliesPage> {
  final _codeController = TextEditingController();
  late Future<List<FamilyInfo>> _familiesFuture;
  bool _joining = false;

  @override
  void initState() {
    super.initState();
    _familiesFuture = widget.api.myFamilies();
  }

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  void _reload() {
    setState(() {
      _familiesFuture = widget.api.myFamilies();
    });
  }

  Future<void> _join() async {
    final code = _codeController.text.trim().toUpperCase();
    if (code.length != 6) {
      _show('请输入6位家庭号');
      return;
    }
    setState(() => _joining = true);
    try {
      final family = await widget.api.joinFamily(code);
      _codeController.clear();
      _show(family.isApproved ? '已加入家庭' : '申请已提交，等待商家审核');
      _reload();
    } catch (error) {
      _show(error.toString());
    } finally {
      if (mounted) setState(() => _joining = false);
    }
  }

  Future<void> _leave(FamilyInfo family) async {
    await widget.api.leaveFamily(family.shopId);
    _show('已退出家庭');
    _reload();
  }

  void _show(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          behavior: SnackBarBehavior.floating,
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F7F7),
      appBar: AppBar(
        title: const Text('我的家庭'),
        backgroundColor: const Color(0xFFF7F7F7),
        foregroundColor: pageTextColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async => _reload(),
          color: warmOrange,
          backgroundColor: Colors.white,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            children: [
              const _PageHeaderCard(
                icon: Icons.home_outlined,
                title: '加入家庭商家',
                message: '输入商家提供的家庭号，通过审核后就能看到家庭专属商家和菜单。',
              ),
              const SizedBox(height: 12),
              _WhiteCard(
                child: TextField(
                  controller: _codeController,
                  textCapitalization: TextCapitalization.characters,
                  maxLength: 6,
                  decoration: InputDecoration(
                    labelText: '家庭号',
                    hintText: '请输入6位数字或大写字母',
                    prefixIcon: const Icon(Icons.key_outlined),
                    suffixIcon: IconButton(
                      onPressed: _joining ? null : _join,
                      icon: _joining
                          ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: warmOrange,
                        ),
                      )
                          : const Icon(Icons.login_rounded),
                    ),
                  ),
                  onSubmitted: (_) {
                    if (!_joining) _join();
                  },
                ),
              ),
              const SizedBox(height: 12),
              FutureBuilder<List<FamilyInfo>>(
                future: _familiesFuture,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const _FamiliesSkeleton();
                  }
                  if (snapshot.hasError) {
                    return _StateMessage(
                      icon: Icons.wifi_off_outlined,
                      title: '家庭加载失败',
                      message: snapshot.error.toString(),
                      actionText: '重试',
                      onPressed: _reload,
                    );
                  }
                  final families = snapshot.data ?? const [];
                  if (families.isEmpty) {
                    return _StateMessage(
                      icon: Icons.home_work_outlined,
                      title: '还没有加入家庭',
                      message: '输入商家给你的家庭号，申请通过后会显示在这里。',
                      actionText: '刷新',
                      onPressed: _reload,
                    );
                  }
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (final family in families) ...[
                        _FamilyCard(family: family, onLeave: () => _leave(family)),
                        const SizedBox(height: 12),
                      ],
                    ],
                  );
                },
              ),
              const AppFooter(),
            ],
          ),
        ),
      ),
    );
  }
}

class _FamilyCard extends StatelessWidget {
  const _FamilyCard({required this.family, required this.onLeave});

  final FamilyInfo family;
  final VoidCallback onLeave;

  @override
  Widget build(BuildContext context) {
    // 不使用 Ink / Card / ListTile 绘制白色背景。
    // Ink 的装饰会绘制到上层 Material 上，在列表顶部/底部回弹拉伸时，
    // 容易出现“内容被拉伸，但白色背景像粘住不动”的视觉问题。
    // 用 Container 自己绘制背景后，背景和内部元素会作为同一个子树一起参与拉伸变形。
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            width: 56,
            height: 56,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              color: softOrange,
              borderRadius: BorderRadius.circular(8),
            ),
            child: family.familyImageUrl?.isNotEmpty == true
                ? Image.network(
              resolveCustomerImageUrl(family.familyImageUrl!),
              fit: BoxFit.cover,
              loadingBuilder: (context, child, progress) {
                if (progress == null) {
                  return TweenAnimationBuilder<double>(
                    tween: Tween(begin: 0.0, end: 1.0),
                    duration: const Duration(milliseconds: 300),
                    builder: (context, value, child) => Opacity(opacity: value, child: child),
                    child: child,
                  );
                }
                return const Center(
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: warmOrange,
                  ),
                );
              },
              errorBuilder: (_, _, _) => const Icon(
                Icons.home_outlined,
                color: warmOrange,
                size: 30,
              ),
            )
                : const Icon(Icons.home_outlined, color: warmOrange, size: 30),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        family.familyName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: pageTextColor,
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    _StatusBadge(
                      text: family.isApproved ? '已加入' : '待审核',
                      approved: family.isApproved,
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    const Icon(Icons.confirmation_number_outlined, color: warmOrange, size: 14),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        family.familyCode,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: pageMutedColor, fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          TextButton(
            onPressed: onLeave,
            child: const Text('退出'),
          ),
        ],
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.text, required this.approved});

  final String text;
  final bool approved;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: approved ? softOrange : const Color(0xFFF7F7F7),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: approved ? warmOrange : pageMutedColor,
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _FamiliesSkeleton extends StatelessWidget {
  const _FamiliesSkeleton();

  @override
  Widget build(BuildContext context) {
    const skeletonColor = Color(0xFFFFF4E8);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: List.generate(3, (index) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: skeletonColor,
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(height: 18, width: double.infinity, color: skeletonColor),
                      const SizedBox(height: 10),
                      Container(height: 14, width: 120, color: skeletonColor),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Container(
                  width: 46,
                  height: 28,
                  decoration: BoxDecoration(
                    color: skeletonColor,
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ],
            ),
          ),
        );
      }),
    );
  }
}

class _StateMessage extends StatelessWidget {
  const _StateMessage({
    required this.icon,
    required this.title,
    required this.message,
    required this.actionText,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionText;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 36),
      child: _WhiteCard(
        child: Column(
          children: [
            Icon(icon, color: Colors.grey[400], size: 56),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: pageTextColor,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: pageMutedColor, fontSize: 13),
            ),
            const SizedBox(height: 20),
            FilledButton.tonal(onPressed: onPressed, child: Text(actionText)),
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
