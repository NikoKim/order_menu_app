import 'package:flutter/material.dart';

import '../main.dart';
import '../moment_models.dart';
import '../services/api_client.dart';
import '../services/moment_api.dart';
import '../widgets/app_notice.dart';
import '../widgets/ui_utils.dart';
import 'moment_feed_page.dart';

class MyMomentsPage extends StatelessWidget {
  const MyMomentsPage({super.key, required this.userId});
  final int userId;
  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(title: const Text('我的动态')),
    body: ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.public),
                title: const Text('已发布动态'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) =>
                        MomentFeedPage(authorId: userId, title: '我发布的动态'),
                  ),
                ),
              ),
              const Divider(height: 1, indent: 56),
              ListTile(
                leading: const Icon(Icons.drafts_outlined),
                title: const Text('草稿箱'),
                subtitle: const Text('未发布草稿保留30天'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const MomentDraftsPage()),
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class UserMomentsPage extends StatefulWidget {
  const UserMomentsPage({
    super.key,
    required this.userId,
    required this.nickname,
  });
  final int userId;
  final String nickname;
  @override
  State<UserMomentsPage> createState() => _UserMomentsPageState();
}

class _UserMomentsPageState extends State<UserMomentsPage> {
  late Future<MomentProfileSummary> _future;

  late CustomerApi _api;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_initialized) {
      _initialized = true;
      _api = CustomerApi(AppScope.of(context).api);
      _future = _api.momentProfileSummary(widget.userId);
    }
  }

  Future<void> _unhide() async {
    try {
      await _api.updateMomentPrivacyForUser(
        widget.userId,
        hideTheirMoments: false,
      );
      if (mounted)
        setState(() => _future = _api.momentProfileSummary(widget.userId));
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(title: Text('${widget.nickname}的动态')),
    body: FutureBuilder<MomentProfileSummary>(
      future: _future,
      builder: (_, snapshot) {
        if (!snapshot.hasData) return const MomentFeedSkeleton(itemCount: 3);
        final summary = snapshot.data!;
        if (!summary.canView) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.visibility_off_outlined,
                    size: 46,
                    color: Colors.grey,
                  ),
                  const SizedBox(height: 14),
                  Text(
                    summary.hiddenByMe
                        ? '您已设置不看Ta动态'
                        : (summary.reason ?? '无法查看动态'),
                    textAlign: TextAlign.center,
                  ),
                  if (summary.hiddenByMe) ...[
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: _unhide,
                      child: const Text('关闭设置并查看动态'),
                    ),
                  ],
                ],
              ),
            ),
          );
        }
        return MomentFeedPage(
          embedded: true,
          authorId: widget.userId,
          title: '${widget.nickname}的动态',
        );
      },
    ),
  );
}

class MyMomentPrivacyPage extends StatefulWidget {
  const MyMomentPrivacyPage({super.key});
  @override
  State<MyMomentPrivacyPage> createState() => _MyMomentPrivacyPageState();
}

class _MyMomentPrivacyPageState extends State<MyMomentPrivacyPage> {
  String _value = 'PUBLIC';
  bool _saving = false;
  static const _items = {
    'CHAT_FRIENDS': ('仅互聊好友可见', '只有建立聊天关系的用户可进入您的动态页'),
    'FAMILY': ('仅家庭成员可见', '同一家庭的成员可以进入您的动态页'),
    'PUBLIC': ('公开', '所有用户可以进入您的动态页'),
    'PRIVATE': ('私密', '仅自己可以进入动态页'),
  };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        final value = await CustomerApi(
          AppScope.of(context).api,
        ).myMomentProfileSummary();
        if (mounted) setState(() => _value = value.visibility);
      } catch (_) {}
    });
  }

  Future<void> _save(String value) async {
    if (_saving) return;
    setState(() {
      _saving = true;
      _value = value;
    });
    try {
      await CustomerApi(AppScope.of(context).api).updateMyMomentPrivacy(value);
      if (mounted) AppNotice.show(context, '动态页可见人群已更新');
    } on ApiException catch (e) {
      if (mounted) AppNotice.show(context, e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F5F5),
    appBar: AppBar(title: const Text('动态页可见人群')),
    body: ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Column(
            children: [
              for (final entry in _items.entries)
                RadioListTile<String>(
                  value: entry.key,
                  groupValue: _value,
                  onChanged: _saving
                      ? null
                      : (value) {
                          if (value != null) _save(value);
                        },
                  title: Text(entry.value.$1),
                  subtitle: Text(entry.value.$2),
                ),
            ],
          ),
        ),
      ],
    ),
  );
}
