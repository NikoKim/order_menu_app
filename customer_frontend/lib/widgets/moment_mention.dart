import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';
import '../moment_models.dart';
import '../pages/moment_privacy_page.dart';
import '../services/api_client.dart';
import '../services/moment_api.dart';

List<int> activeMomentMentionIds(String text, Iterable<MomentUser> users) =>
    users
        .where((user) => text.contains('@${user.nickname}'))
        .map((user) => user.id)
        .toSet()
        .toList();

class MomentMentionText extends StatelessWidget {
  const MomentMentionText({
    super.key,
    required this.content,
    required this.mentions,
    this.style,
    this.maxLines,
    this.overflow = TextOverflow.clip,
  });

  final String content;
  final List<MomentUser> mentions;
  final TextStyle? style;
  final int? maxLines;
  final TextOverflow overflow;

  @override
  Widget build(BuildContext context) {
    final byName = {for (final user in mentions) user.nickname: user};
    if (byName.isEmpty) {
      return Text(
        content,
        style: style,
        maxLines: maxLines,
        overflow: overflow,
      );
    }
    final names = byName.keys.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    final pattern = RegExp('@(${names.map(RegExp.escape).join('|')})');
    final spans = <InlineSpan>[];
    var cursor = 0;
    for (final match in pattern.allMatches(content)) {
      if (match.start > cursor)
        spans.add(TextSpan(text: content.substring(cursor, match.start)));
      final user = byName[match.group(1)]!;
      spans.add(
        WidgetSpan(
          alignment: PlaceholderAlignment.baseline,
          baseline: TextBaseline.alphabetic,
          child: GestureDetector(
            onTap: () => _showMomentUserCard(context, user),
            onLongPress: () => _showMomentUserCard(context, user),
            child: Text(
              match.group(0)!,
              style: (style ?? const TextStyle()).copyWith(
                color: const Color(0xFFD96F0A),
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      );
      cursor = match.end;
    }
    if (cursor < content.length)
      spans.add(TextSpan(text: content.substring(cursor)));
    return Text.rich(
      TextSpan(style: style, children: spans),
      maxLines: maxLines,
      overflow: overflow,
    );
  }
}

Future<void> _showMomentUserCard(BuildContext context, MomentUser user) async {
  final currentUserId = AppScope.of(context).auth.user?.id;
  final cardFuture = CustomerApi(
    AppScope.of(context).api,
  ).momentUserCard(user.id);
  await showDialog<void>(
    context: context,
    barrierColor: const Color(0x66000000),
    builder: (dialogContext) => _MomentUserCardDialog(
      user: user,
      cardFuture: cardFuture,
      currentUserId: currentUserId,
      hostContext: context,
    ),
  );
}

class _MomentUserCardDialog extends StatelessWidget {
  const _MomentUserCardDialog({
    required this.user,
    required this.cardFuture,
    required this.currentUserId,
    required this.hostContext,
  });

  final MomentUser user;
  final Future<MomentUserCard> cardFuture;
  final int? currentUserId;
  final BuildContext hostContext;

  @override
  Widget build(BuildContext context) => Dialog(
    insetPadding: const EdgeInsets.symmetric(horizontal: 36),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 330),
      child: FutureBuilder<MomentUserCard>(
        future: cardFuture,
        builder: (_, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SizedBox(
              height: 220,
              child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
            );
          }
          final card = snapshot.data;
          final avatarUrl = card?.avatarUrl ?? user.avatarUrl;
          final nickname = card?.nickname ?? user.nickname;
          final merchant = card?.merchant ?? user.merchant;
          final isSelf = card?.self == true || currentUserId == user.id;
          return Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Align(
                  alignment: Alignment.centerRight,
                  child: IconButton(
                    tooltip: '关闭',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ),
                CircleAvatar(
                  radius: 32,
                  backgroundColor: const Color(0xFFF0F1F3),
                  backgroundImage: avatarUrl?.isNotEmpty == true
                      ? NetworkImage(resolveCustomerImageUrl(avatarUrl!))
                      : null,
                  child: avatarUrl?.isNotEmpty == true
                      ? null
                      : const Icon(Icons.person_outline_rounded, size: 30),
                ),
                const SizedBox(height: 11),
                Text(
                  nickname,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF202124),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isSelf ? '自己' : (merchant ? '商家用户' : '顾客用户'),
                  style: const TextStyle(
                    fontSize: 13,
                    color: Color(0xFF6F747B),
                  ),
                ),
                if (card?.phoneVisible == true &&
                    card?.phone?.isNotEmpty == true) ...[
                  const SizedBox(height: 9),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(
                        Icons.phone_outlined,
                        size: 15,
                        color: Color(0xFF777B82),
                      ),
                      const SizedBox(width: 5),
                      Text(
                        card!.phone!,
                        style: const TextStyle(
                          fontSize: 13,
                          color: Color(0xFF555A61),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 17),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: () {
                      Navigator.pop(context);
                      if (!hostContext.mounted) return;
                      Navigator.of(hostContext).push(
                        MaterialPageRoute(
                          builder: (_) => isSelf
                              ? MyMomentsPage(userId: user.id)
                              : UserMomentsPage(
                                  userId: user.id,
                                  nickname: nickname,
                                ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.person_outline_rounded),
                    label: const Text('查看详情'),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    ),
  );
}

Future<List<MomentUser>?> showMomentMentionPicker(
  BuildContext context, {
  List<MomentUser> selected = const [],
}) => showModalBottomSheet<List<MomentUser>>(
  context: context,
  isScrollControlled: true,
  showDragHandle: true,
  builder: (_) => _MomentMentionPicker(initial: selected),
);

class _MomentMentionPicker extends StatefulWidget {
  const _MomentMentionPicker({required this.initial});
  final List<MomentUser> initial;
  @override
  State<_MomentMentionPicker> createState() => _MomentMentionPickerState();
}

class _MomentMentionPickerState extends State<_MomentMentionPicker> {
  final _search = TextEditingController();
  final _selected = <int, MomentUser>{};
  List<MomentUser> _recent = const [];
  List<MomentUser> _results = const [];
  bool _loading = true;
  Timer? _debounce;
  int _sequence = 0;

  @override
  void initState() {
    super.initState();
    for (final user in widget.initial) _selected[user.id] = user;
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadRecent());
  }

  @override
  void dispose() {
    _search.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _loadRecent() async {
    try {
      final conversations = await CustomerApi(
        AppScope.of(context).api,
      ).conversations();
      final seen = <int>{};
      final users = conversations
          .where(
            (item) => item.directUserId != null && seen.add(item.directUserId!),
          )
          .map(
            (item) => MomentUser(
              id: item.directUserId!,
              nickname: item.directUserNickname ?? item.title,
              avatarUrl: item.directUserAvatarUrl,
            ),
          )
          .toList();
      if (mounted)
        setState(() {
          _recent = users;
          _results = users;
          _loading = false;
        });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _runSearch(String value) {
    _debounce?.cancel();
    final keyword = value.trim();
    final current = ++_sequence;
    if (keyword.isEmpty) {
      setState(() {
        _results = _recent;
        _loading = false;
      });
      return;
    }
    setState(() => _loading = true);
    _debounce = Timer(const Duration(milliseconds: 250), () async {
      try {
        final users = await CustomerApi(
          AppScope.of(context).api,
        ).searchMomentMentionUsers(keyword);
        if (mounted && current == _sequence) setState(() => _results = users);
      } catch (_) {
        if (mounted && current == _sequence)
          setState(() => _results = const []);
      } finally {
        if (mounted && current == _sequence) setState(() => _loading = false);
      }
    });
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: SizedBox(
      height: MediaQuery.sizeOf(context).height * .72,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 8, 10),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    '提及用户',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                ),
                TextButton(
                  onPressed: () =>
                      Navigator.pop(context, _selected.values.toList()),
                  child: const Text('完成'),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _search,
              autofocus: true,
              onChanged: _runSearch,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search_rounded),
                hintText: '搜索用户昵称',
              ),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: _loading
                ? ListView.builder(
                    itemCount: 6,
                    itemBuilder: (_, __) => const ListTile(
                      leading: CircleAvatar(),
                      title: LinearProgressIndicator(),
                    ),
                  )
                : _results.isEmpty
                ? const Center(child: Text('没有找到用户'))
                : ListView.builder(
                    itemCount: _results.length,
                    itemBuilder: (_, index) {
                      final user = _results[index];
                      final chosen = _selected.containsKey(user.id);
                      return CheckboxListTile(
                        value: chosen,
                        onChanged: (_) => setState(() {
                          if (chosen) {
                            _selected.remove(user.id);
                          } else {
                            _selected[user.id] = user;
                          }
                        }),
                        secondary: CircleAvatar(
                          backgroundImage: user.avatarUrl?.isNotEmpty == true
                              ? NetworkImage(
                                  resolveCustomerImageUrl(user.avatarUrl!),
                                )
                              : null,
                          child: user.avatarUrl?.isNotEmpty == true
                              ? null
                              : const Icon(Icons.person_outline_rounded),
                        ),
                        title: Text(user.nickname),
                        subtitle: Text(user.merchant ? '商家' : '顾客'),
                      );
                    },
                  ),
          ),
        ],
      ),
    ),
  );
}
