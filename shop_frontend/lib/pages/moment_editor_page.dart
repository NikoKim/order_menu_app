import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models/moment_models.dart';
import '../models/shop_api_models.dart';
import '../services/api_config.dart';
import '../services/shop_api_service.dart';
import '../services/moment_image.dart';
import '../widgets/app_notice.dart';

class MomentEditorPage extends StatefulWidget {
  const MomentEditorPage({super.key, this.initial, this.loadTopics = true});
  final MomentItem? initial;
  final bool loadTopics;

  @override
  State<MomentEditorPage> createState() => _MomentEditorPageState();
}

class _ComposerSection extends StatelessWidget {
  const _ComposerSection({
    required this.title,
    required this.icon,
    required this.child,
    this.trailing,
  });
  final String title;
  final IconData icon;
  final Widget child;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: const Color(0xFFEAECF0)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: const Color(0xFFFFF0E2),
                borderRadius: BorderRadius.circular(9),
              ),
              child: Icon(icon, size: 18, color: const Color(0xFFE77910)),
            ),
            const SizedBox(width: 9),
            Text(
              title,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w800,
                color: Color(0xFF202124),
              ),
            ),
            const Spacer(),
            ?trailing,
          ],
        ),
        const SizedBox(height: 14),
        child,
      ],
    ),
  );
}

class _VisibilitySelector extends StatelessWidget {
  const _VisibilitySelector({required this.value, required this.onChanged});
  final String value;
  final ValueChanged<String> onChanged;

  static const _items = [
    ('PUBLIC', '公开', Icons.public_rounded),
    ('RELATED', '相关', Icons.group_outlined),
    ('PRIVATE', '私密', Icons.lock_outline_rounded),
  ];

  @override
  Widget build(BuildContext context) => Row(
    children: [
      for (var i = 0; i < _items.length; i++) ...[
        if (i > 0) const SizedBox(width: 8),
        Expanded(
          child: Material(
            color: value == _items[i].$1
                ? const Color(0xFFFFF0E2)
                : const Color(0xFFF4F5F7),
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              onTap: () => onChanged(_items[i].$1),
              borderRadius: BorderRadius.circular(12),
              child: Container(
                height: 46,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: value == _items[i].$1
                        ? const Color(0xFFFFC68E)
                        : const Color(0xFFE6E8EB),
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      _items[i].$3,
                      size: 17,
                      color: value == _items[i].$1
                          ? const Color(0xFFE77910)
                          : const Color(0xFF777C82),
                    ),
                    const SizedBox(width: 5),
                    Flexible(
                      child: Text(
                        _items[i].$2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: value == _items[i].$1
                              ? const Color(0xFFC8660B)
                              : const Color(0xFF656A70),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    ],
  );
}

class _TopicSuggestionChip extends StatelessWidget {
  const _TopicSuggestionChip({required this.topic, required this.onTap});
  final MomentTopic topic;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ActionChip(
    onPressed: onTap,
    avatar: const Icon(Icons.tag_rounded, size: 15, color: Color(0xFFE77910)),
    label: Text('${topic.name} · ${topic.momentCount}'),
    visualDensity: VisualDensity.compact,
    backgroundColor: const Color(0xFFF7F7F8),
    side: const BorderSide(color: Color(0xFFE6E8EB)),
    labelStyle: const TextStyle(fontSize: 12, color: Color(0xFF666B71)),
  );
}

class _ComposerImageTile extends StatelessWidget {
  const _ComposerImageTile({required this.child, required this.onRemove});
  final Widget child;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) => Stack(
    clipBehavior: Clip.none,
    children: [
      ClipRRect(
        borderRadius: BorderRadius.circular(13),
        child: SizedBox(width: 92, height: 92, child: child),
      ),
      Positioned(
        top: -7,
        right: -7,
        child: Material(
          color: const Color(0xFF32353A),
          shape: const CircleBorder(),
          child: InkWell(
            onTap: onRemove,
            customBorder: const CircleBorder(),
            child: const SizedBox.square(
              dimension: 25,
              child: Icon(Icons.close_rounded, size: 15, color: Colors.white),
            ),
          ),
        ),
      ),
    ],
  );
}

class _MomentEditorPageState extends State<MomentEditorPage> {
  static const _clearDish = 'clear-dish';
  final _title = TextEditingController();
  final _content = TextEditingController();
  final _picker = ImagePicker();
  final _topicSearch = TextEditingController();
  final List<MomentTopic> _topics = [];
  final List<String> _remoteImages = [];
  final List<String> _localImages = [];
  String _visibility = 'PUBLIC';
  DishInfo? _dish;
  bool _saving = false;
  int? _editingId;
  List<MomentTopic> _suggestions = const [];
  List<MomentTopic> _hotTopics = const [];
  int _hotPage = 0;
  int _hotTotal = 0;
  bool _loadingTopics = false;
  Timer? _topicDebounce;

  @override
  void initState() {
    super.initState();
    final initial = widget.initial;
    if (initial != null) {
      _editingId = initial.id;
      _title.text = initial.title;
      _content.text = initial.content;
      _visibility = initial.visibility;
      _topics.addAll(initial.topics);
      _remoteImages.addAll(initial.images);
      _dish = _dishFromMoment(initial);
    }
    if (widget.loadTopics) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadHotTopics());
    }
  }

  DishInfo? _dishFromMoment(MomentItem moment) {
    if (moment.dishId == null) return null;
    return DishInfo(
      id: moment.dishId!,
      shopId: moment.shopId ?? 0,
      categoryId: 0,
      name: moment.dishName ?? '关联菜品',
      description: moment.dishDescription,
      price: moment.dishPrice ?? 0,
      stock: 0,
      salesCount: moment.dishSalesCount ?? 0,
      status: 'ON_SALE',
      sortOrder: 0,
      recommendOrder: 0,
      imageUrl: moment.dishImageUrl,
    );
  }

  Future<void> _restoreDraft() async {
    try {
      final drafts = await shopApi.fetchMomentDrafts();
      if (!mounted) return;
      final draft = await showModalBottomSheet<MomentItem>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: drafts.isEmpty
              ? const Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(child: Text('暂无草稿')),
                )
              : ListView(
                  children: [
                    const ListTile(
                      title: Text(
                        '恢复草稿',
                        style: TextStyle(fontWeight: FontWeight.w800),
                      ),
                      subtitle: Text('未发布草稿将在30天后自动清理'),
                    ),
                    ...drafts.map(
                      (item) => ListTile(
                        leading: const Icon(Icons.drafts_outlined),
                        title: Text(item.title.isEmpty ? '未命名草稿' : item.title),
                        subtitle: Text(
                          item.content,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing: IconButton(
                          tooltip: '删除草稿',
                          icon: const Icon(Icons.delete_outline),
                          onPressed: () async {
                            await shopApi.deleteMoment(item.id);
                            if (context.mounted) Navigator.pop(context);
                          },
                        ),
                        onTap: () => Navigator.pop(context, item),
                      ),
                    ),
                  ],
                ),
        ),
      );
      if (draft == null || !mounted) return;
      setState(() {
        _editingId = draft.id;
        _title.text = draft.title;
        _content.text = draft.content;
        _visibility = draft.visibility;
        _topics
          ..clear()
          ..addAll(draft.topics);
        _remoteImages
          ..clear()
          ..addAll(draft.images);
        _localImages.clear();
        _dish = _dishFromMoment(draft);
      });
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    _topicSearch.dispose();
    _topicDebounce?.cancel();
    super.dispose();
  }

  Future<void> _loadHotTopics({bool next = false}) async {
    if (_loadingTopics) return;
    setState(() => _loadingTopics = true);
    try {
      var page = next ? _hotPage + 1 : 0;
      var result = await shopApi.fetchMomentTopics(page: page);
      if (result.items.isEmpty && page > 0) {
        page = 0;
        result = await shopApi.fetchMomentTopics();
      }
      if (!mounted) return;
      setState(() {
        _hotTopics = result.items.take(10).toList();
        _hotPage = page;
        _hotTotal = result.total;
      });
    } finally {
      if (mounted) setState(() => _loadingTopics = false);
    }
  }

  void _searchTopics(String value) {
    _topicDebounce?.cancel();
    final keyword = value.trim().replaceFirst('#', '');
    if (keyword.isEmpty) {
      setState(() => _suggestions = const []);
      return;
    }
    _topicDebounce = Timer(const Duration(milliseconds: 300), () async {
      if (mounted) setState(() => _loadingTopics = true);
      try {
        final result = await shopApi.fetchMomentTopics(keyword: keyword);
        if (!mounted ||
            _topicSearch.text.trim().replaceFirst('#', '') != keyword) {
          return;
        }
        setState(() => _suggestions = result.items.take(10).toList());
      } finally {
        if (mounted) setState(() => _loadingTopics = false);
      }
    });
  }

  void _selectTopic(MomentTopic topic) {
    if (_topics.length >= 5 || _topics.any((item) => item.name == topic.name)) {
      return;
    }
    setState(() {
      _topics.add(topic);
      _topicSearch.clear();
      _suggestions = const [];
    });
  }

  Future<void> _addTypedTopic() async {
    final name = _topicSearch.text.trim().replaceFirst('#', '');
    if (name.isEmpty) return;
    if (_topics.length >= 5) {
      AppNotice.show(context, '最多添加5个话题');
      return;
    }
    try {
      final matches = _suggestions.where((topic) => topic.name == name);
      final topic = matches.isEmpty
          ? await shopApi.createMomentTopic(name)
          : matches.first;
      if (mounted) _selectTopic(topic);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _pickImages() async {
    final remaining = 9 - _remoteImages.length - _localImages.length;
    if (remaining <= 0) {
      AppNotice.show(context, '每条动态最多9张图片');
      return;
    }
    final files = await _picker.pickMultiImage(
      imageQuality: 85,
      limit: remaining,
    );
    try {
      final paths = <String>[];
      for (final file in files) {
        paths.add(await prepareMomentImage(file.path, comment: false));
      }
      if (mounted) setState(() => _localImages.addAll(paths));
    } on StateError {
      if (mounted) AppNotice.show(context, '图片压缩后仍超过10MB，请选择较小的图片');
    }
  }

  Future<void> _chooseDish() async {
    try {
      final page = await shopApi.fetchDishes(pageSize: 100);
      if (!mounted) return;
      final picked = await showModalBottomSheet<Object>(
        context: context,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ListView(
            children: [
              ListTile(
                leading: const Icon(Icons.link_off),
                title: const Text('不关联菜品'),
                onTap: () => Navigator.pop(context, _clearDish),
              ),
              ...page.records.map(
                (dish) => ListTile(
                  leading: dish.imageUrl?.isNotEmpty == true
                      ? ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: Image.network(
                            ApiConfig.resolveImageUrl(dish.imageUrl!),
                            width: 44,
                            height: 44,
                            fit: BoxFit.cover,
                          ),
                        )
                      : const Icon(Icons.restaurant_menu),
                  title: Text(dish.name),
                  subtitle: Text('¥${dish.price.toStringAsFixed(2)}'),
                  onTap: () => Navigator.pop(context, dish),
                ),
              ),
            ],
          ),
        ),
      );
      if (!mounted || picked == null) return;
      setState(() => _dish = picked == _clearDish ? null : picked as DishInfo);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    }
  }

  Future<void> _submit({required bool draft}) async {
    if (_saving) return;
    if (!draft && _title.text.trim().length < 4) {
      AppNotice.show(context, '标题必须大于3个字');
      return;
    }
    if (!draft && _content.text.trim().isEmpty) {
      AppNotice.show(context, '请填写动态内容');
      return;
    }
    setState(() => _saving = true);
    try {
      final images = [..._remoteImages];
      for (final path in _localImages) {
        images.add(await shopApi.uploadMomentImage(path));
      }
      final result = await shopApi.saveMoment(
        momentId: _editingId,
        title: _title.text,
        content: _content.text,
        visibility: _visibility,
        topics: _topics.map((e) => e.name).toList(),
        imageUrls: images,
        dishId: _dish?.id,
        draft: draft,
      );
      if (!mounted) return;
      AppNotice.show(context, draft ? '草稿已保存，30天后自动清理' : '动态已发布');
      Navigator.pop(context, result);
    } on ApiException catch (error) {
      if (mounted) AppNotice.show(context, error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF6F7F8),
    appBar: AppBar(
      title: Text(
        widget.initial == null ? '发布动态' : '编辑动态',
        style: const TextStyle(fontWeight: FontWeight.w700),
      ),
      backgroundColor: Colors.white,
      foregroundColor: const Color(0xFF202124),
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
      actions: [
        if (widget.initial == null)
          IconButton(
            tooltip: '恢复草稿',
            onPressed: _saving ? null : _restoreDraft,
            icon: const Icon(Icons.drafts_outlined),
          ),
        TextButton(
          onPressed: _saving ? null : () => _submit(draft: true),
          child: const Text('存草稿'),
        ),
        const SizedBox(width: 8),
      ],
    ),
    body: ListView(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 108),
      children: [
        _ComposerSection(
          title: '动态内容',
          icon: Icons.article_outlined,
          child: Column(
            children: [
              TextField(
                controller: _title,
                maxLength: 80,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF202124),
                ),
                decoration: const InputDecoration(
                  hintText: '写一个清晰的标题',
                  hintStyle: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: Color(0xFFB0B4B9),
                  ),
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  counterStyle: TextStyle(color: Color(0xFF9AA0A6)),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const Divider(height: 20, color: Color(0xFFEEF0F2)),
              TextField(
                controller: _content,
                minLines: 8,
                maxLines: 18,
                style: const TextStyle(
                  fontSize: 15,
                  height: 1.6,
                  color: Color(0xFF3C4043),
                ),
                decoration: const InputDecoration(
                  hintText: '分享店里的新鲜事、菜品故事或经营日常…',
                  hintStyle: TextStyle(color: Color(0xFFB0B4B9)),
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _ComposerSection(
          title: '图片',
          icon: Icons.photo_library_outlined,
          trailing: Text(
            '${_remoteImages.length + _localImages.length}/9',
            style: const TextStyle(fontSize: 12, color: Color(0xFF9AA0A6)),
          ),
          child: Wrap(
            spacing: 9,
            runSpacing: 9,
            children: [
              for (final image in _remoteImages)
                _ComposerImageTile(
                  child: Image.network(
                    ApiConfig.resolveImageUrl(image),
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => const ColoredBox(
                      color: Color(0xFFF0F1F3),
                      child: Icon(Icons.broken_image_outlined),
                    ),
                  ),
                  onRemove: () => setState(() => _remoteImages.remove(image)),
                ),
              for (final image in _localImages)
                _ComposerImageTile(
                  child: Image.file(File(image), fit: BoxFit.cover),
                  onRemove: () => setState(() => _localImages.remove(image)),
                ),
              if (_remoteImages.length + _localImages.length < 9)
                InkWell(
                  onTap: _pickImages,
                  borderRadius: BorderRadius.circular(13),
                  child: Ink(
                    width: 92,
                    height: 92,
                    decoration: BoxDecoration(
                      color: const Color(0xFFF4F5F7),
                      borderRadius: BorderRadius.circular(13),
                      border: Border.all(color: const Color(0xFFE0E3E7)),
                    ),
                    child: const Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.add_photo_alternate_outlined,
                          color: Color(0xFFE77910),
                        ),
                        SizedBox(height: 5),
                        Text(
                          '添加图片',
                          style: TextStyle(
                            fontSize: 12,
                            color: Color(0xFF7A7F85),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _ComposerSection(
          title: '添加菜品',
          icon: Icons.restaurant_menu_rounded,
          trailing: _dish == null
              ? null
              : IconButton(
                  tooltip: '清除关联菜品',
                  onPressed: () => setState(() => _dish = null),
                  icon: const Icon(Icons.close_rounded, size: 19),
                ),
          child: Material(
            color: const Color(0xFFFFF6ED),
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              onTap: _chooseDish,
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.all(11),
                child: Row(
                  children: [
                    if (_dish?.imageUrl?.isNotEmpty == true)
                      ClipRRect(
                        borderRadius: BorderRadius.circular(9),
                        child: Image.network(
                          ApiConfig.resolveImageUrl(_dish!.imageUrl!),
                          width: 52,
                          height: 52,
                          fit: BoxFit.cover,
                          errorBuilder: (_, _, _) => const SizedBox.square(
                            dimension: 52,
                            child: Icon(Icons.broken_image_outlined),
                          ),
                        ),
                      )
                    else
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(9),
                        ),
                        child: const Icon(
                          Icons.restaurant_menu_rounded,
                          color: Color(0xFFE77910),
                        ),
                      ),
                    const SizedBox(width: 11),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _dish?.name ?? '选择一个店内菜品',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontWeight: FontWeight.w700,
                              color: Color(0xFF4A4E52),
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            _dish == null
                                ? '可选，发布后展示菜品卡片'
                                : '¥${_dish!.price.toStringAsFixed(2)} · 点击更换',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              color: Color(0xFF8A6A4B),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Icon(
                      Icons.chevron_right_rounded,
                      color: Color(0xFFE77910),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        _ComposerSection(
          title: '话题',
          icon: Icons.tag_rounded,
          trailing: Text(
            '${_topics.length}/5',
            style: const TextStyle(fontSize: 12, color: Color(0xFF9AA0A6)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (_topics.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Wrap(
                    spacing: 7,
                    runSpacing: 7,
                    children: [
                      for (final topic in _topics)
                        InputChip(
                          label: Text('#${topic.name}'),
                          visualDensity: VisualDensity.compact,
                          backgroundColor: const Color(0xFFFFF1E4),
                          side: const BorderSide(color: Color(0xFFFFD9B5)),
                          deleteIconColor: const Color(0xFFE77910),
                          labelStyle: const TextStyle(
                            color: Color(0xFFC8660B),
                            fontWeight: FontWeight.w600,
                          ),
                          onDeleted: () =>
                              setState(() => _topics.remove(topic)),
                        ),
                    ],
                  ),
                ),
              Container(
                decoration: BoxDecoration(
                  color: const Color(0xFFF4F5F7),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _topicSearch,
                        onChanged: (value) {
                          _searchTopics(value);
                          setState(() {});
                        },
                        onSubmitted: (_) => _addTypedTopic(),
                        decoration: const InputDecoration(
                          prefixIcon: Icon(Icons.tag_rounded, size: 20),
                          hintText: '搜索或创建话题',
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(vertical: 12),
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: '添加话题',
                      onPressed: _addTypedTopic,
                      icon: const Icon(Icons.add_circle_rounded),
                      color: const Color(0xFFE77910),
                    ),
                  ],
                ),
              ),
              if (_suggestions.isNotEmpty) ...[
                const SizedBox(height: 10),
                Wrap(
                  spacing: 7,
                  runSpacing: 7,
                  children: [
                    for (final topic in _suggestions)
                      _TopicSuggestionChip(
                        topic: topic,
                        onTap: () => _selectTopic(topic),
                      ),
                  ],
                ),
              ],
              if (_topicSearch.text.trim().isEmpty &&
                  _hotTopics.isNotEmpty) ...[
                const SizedBox(height: 13),
                Row(
                  children: [
                    const Text(
                      '热门话题',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF5F6368),
                      ),
                    ),
                    const Spacer(),
                    if (_hotTotal > 10)
                      TextButton.icon(
                        onPressed: _loadingTopics
                            ? null
                            : () => _loadHotTopics(next: true),
                        icon: const Icon(Icons.refresh_rounded, size: 16),
                        label: const Text('换一换'),
                      ),
                  ],
                ),
                Wrap(
                  spacing: 7,
                  runSpacing: 7,
                  children: [
                    for (final topic in _hotTopics)
                      _TopicSuggestionChip(
                        topic: topic,
                        onTap: () => _selectTopic(topic),
                      ),
                  ],
                ),
              ],
              if (_loadingTopics)
                const Padding(
                  padding: EdgeInsets.only(top: 10),
                  child: LinearProgressIndicator(
                    minHeight: 2,
                    color: Color(0xFFE77910),
                    backgroundColor: Color(0xFFFFE7CF),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _ComposerSection(
          title: '谁可以看到',
          icon: Icons.visibility_outlined,
          child: _VisibilitySelector(
            value: _visibility,
            onChanged: (value) => setState(() => _visibility = value),
          ),
        ),
      ],
    ),
    bottomNavigationBar: SafeArea(
      child: Container(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: Color(0xFFEAECF0))),
        ),
        child: SizedBox(
          height: 48,
          child: FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFFF28C28),
              foregroundColor: Colors.white,
              disabledBackgroundColor: const Color(0xFFFFD7B0),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
              ),
            ),
            onPressed: _saving ? null : () => _submit(draft: false),
            icon: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.send_rounded, size: 19),
            label: Text(
              widget.initial == null ? '发布动态' : '保存修改',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ),
        ),
      ),
    ),
  );
}
