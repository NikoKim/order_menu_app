import 'package:flutter/material.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../widgets/app_notice.dart';

class ChatEssencePage extends StatefulWidget {
  const ChatEssencePage({super.key, required this.conversationId, this.api});
  final int conversationId;
  final ShopApiService? api;

  @override
  State<ChatEssencePage> createState() => _ChatEssencePageState();
}

class _ChatEssencePageState extends State<ChatEssencePage> {
  List<ChatMessage>? _items;
  bool _owner = false;
  final Set<int> _pinningIds = {};
  final Map<int, bool> _pinnedOverrides = {};
  int _loadGeneration = 0;
  ShopApiService get _api => widget.api ?? shopApi;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    try {
      final results = await Future.wait([
        _api.fetchChatEssences(widget.conversationId),
        _api.fetchCurrentUser(),
        _api.fetchFamilyGroup(widget.conversationId),
      ]);
      final list = results[0] as ChatEssenceList;
      final user = results[1] as CurrentUser;
      final group = results[2] as FamilyGroup;
      final merge = mergeEssencePinnedOverrides(
        list.items,
        _pinnedOverrides,
        pendingIds: _pinningIds,
      );
      final items = merge.items;
      if (items.isNotEmpty) {
        await _api.markChatEssencesRead(widget.conversationId);
      }
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        for (final id in merge.confirmedIds) {
          _pinnedOverrides.remove(id);
        }
        _items = items;
        _owner = group.members.any(
          (member) => member.userId == user.id && member.role == 'OWNER',
        );
      });
    } catch (error) {
      if (mounted) {
        AppNotice.show(
          context,
          error is ApiException ? error.message : '群精华加载失败，请稍后重试',
        );
      }
    }
  }

  Future<void> _pin(ChatMessage message) async {
    if (_pinningIds.contains(message.id)) return;
    final before = _items;
    final pinned = !message.essencePinned;
    _pinningIds.add(message.id);
    _pinnedOverrides[message.id] = pinned;
    setState(() {
      _items = before
          ?.map(
            (item) =>
                item.id == message.id ? item.withEssencePinned(pinned) : item,
          )
          .toList();
    });
    try {
      await _api.setChatEssencePinned(
        widget.conversationId,
        message.id,
        pinned,
      );
    } catch (error) {
      if (mounted) {
        _pinnedOverrides.remove(message.id);
        setState(() {
          _items = _items
              ?.map(
                (item) => item.id == message.id
                    ? item.withEssencePinned(message.essencePinned)
                    : item,
              )
              .toList();
        });
        AppNotice.show(
          context,
          error is ApiException ? error.message : '置顶操作失败，请稍后重试',
        );
      }
    } finally {
      _pinningIds.remove(message.id);
      if (mounted) setState(() {});
    }
    if (mounted && _pinnedOverrides.containsKey(message.id)) await _load();
  }

  Future<void> _remove(ChatMessage message) async {
    await _api.removeChatEssence(widget.conversationId, message.id);
    await _load();
  }

  Future<void> _like(ChatMessage message) async {
    await _api.setChatEssenceLiked(
      widget.conversationId,
      message.id,
      !message.essenceLikedByMe,
    );
    await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F6F8),
    appBar: AppBar(title: const Text('群精华')),
    body: _items == null
        ? const Center(child: CircularProgressIndicator())
        : _items!.isEmpty
        ? const Center(child: Text('暂无群精华'))
        : RefreshIndicator(
            onRefresh: _load,
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: _items!.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final message = _items![index];
                return Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(
                            Icons.star,
                            color: Color(0xFFF28C28),
                            size: 18,
                          ),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              message.senderNickname ?? '群成员',
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          if (message.essencePinned)
                            const Chip(
                              visualDensity: VisualDensity.compact,
                              label: Text('置顶'),
                            ),
                          if ((message.essenceLabel ?? '').isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(left: 6),
                              child: Chip(
                                visualDensity: VisualDensity.compact,
                                label: Text(message.essenceLabel!),
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        message.content.isEmpty
                            ? _typeLabel(message)
                            : message.content,
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Text(
                            formatChatMessageTime(
                              message.sentAt ?? DateTime.now(),
                            ),
                            style: const TextStyle(
                              fontSize: 11,
                              color: Colors.grey,
                            ),
                          ),
                          const Spacer(),
                          TextButton.icon(
                            onPressed: () => _like(message),
                            icon: Icon(
                              message.essenceLikedByMe
                                  ? Icons.thumb_up
                                  : Icons.thumb_up_outlined,
                              size: 17,
                            ),
                            label: Text('${message.essenceLikes}'),
                          ),
                          if (_owner)
                            IconButton(
                              tooltip: message.essencePinned ? '取消置顶' : '置顶',
                              onPressed: _pinningIds.contains(message.id)
                                  ? null
                                  : () => _pin(message),
                              icon: Icon(
                                message.essencePinned
                                    ? Icons.push_pin
                                    : Icons.push_pin_outlined,
                              ),
                            ),
                          if (_owner)
                            IconButton(
                              tooltip: '移出精华',
                              onPressed: () => _remove(message),
                              icon: const Icon(Icons.delete_outline),
                            ),
                        ],
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
  );
}

String _typeLabel(ChatMessage message) {
  if (message.isImage) return '[图片]';
  if (message.isDishShare) return '[菜品卡片]';
  if (message.isOrderShare) return '[订单卡片]';
  if (message.isShopShare) return '[店铺卡片]';
  return '[消息]';
}
