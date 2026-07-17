class MomentPage<T> {
  const MomentPage({
    required this.items,
    required this.hasMore,
    this.total = 0,
  });

  factory MomentPage.fromJson(
    Object? json,
    T Function(Map<String, dynamic>) convert,
  ) {
    final map = json is Map<String, dynamic> ? json : <String, dynamic>{};
    final raw = json is List
        ? json
        : (map['items'] ?? map['content'] ?? map['records'] ?? const []);
    final items = (raw as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(convert)
        .toList();
    final page = _int(map['page'] ?? map['number']);
    final totalPages = _int(map['totalPages'] ?? map['pages']);
    return MomentPage(
      items: items,
      total: _int(map['total'] ?? map['totalElements']),
      hasMore:
          map['hasMore'] == true ||
          (totalPages > 0 &&
              page + (map.containsKey('number') ? 1 : 0) < totalPages),
    );
  }

  final List<T> items;
  final bool hasMore;
  final int total;
}

class MomentTopic {
  const MomentTopic({
    required this.id,
    required this.name,
    this.userCount = 0,
    this.momentCount = 0,
  });
  factory MomentTopic.fromJson(Map<String, dynamic> json) => MomentTopic(
    id: _int(json['id'] ?? json['topicId']),
    name: '${json['name'] ?? json['topicName'] ?? ''}',
    userCount: _int(
      json['userCount'] ?? json['usageCount'] ?? json['useCount'],
    ),
    momentCount: _int(
      json['momentCount'] ?? json['usageCount'] ?? json['useCount'],
    ),
  );
  final int id;
  final String name;
  final int userCount;
  final int momentCount;

  int get useCount => userCount;
}

class MomentReaction {
  const MomentReaction({
    required this.emoji,
    required this.count,
    this.mine = false,
  });
  factory MomentReaction.fromJson(Map<String, dynamic> json) => MomentReaction(
    emoji: '${json['emoji'] ?? ''}',
    count: _int(json['count']),
    mine: json['mine'] == true || json['reactedByMe'] == true,
  );
  final String emoji;
  final int count;
  final bool mine;
}

class MomentItem {
  const MomentItem({
    required this.id,
    required this.authorId,
    required this.authorName,
    required this.title,
    required this.content,
    required this.createdAt,
    this.authorAvatarUrl,
    this.editedAt,
    this.status = 'PUBLISHED',
    this.visibility = 'PUBLIC',
    this.authorMerchant = false,
    this.mine = false,
    this.liked = false,
    this.likeCount = 0,
    this.commentCount = 0,
    this.images = const [],
    this.topics = const [],
    this.reactions = const [],
    this.dishId,
    this.dishName,
    this.dishImageUrl,
    this.dishPrice,
    this.dishDescription,
    this.dishSalesCount,
    this.shopId,
    this.shopName,
    this.shopImageUrl,
    this.unread = false,
    this.hotComment,
  });

  factory MomentItem.fromJson(Map<String, dynamic> json) {
    final author = json['author'] is Map<String, dynamic>
        ? json['author'] as Map<String, dynamic>
        : const <String, dynamic>{};
    return MomentItem(
      id: _int(json['id'] ?? json['momentId']),
      authorId: _int(json['authorId'] ?? json['userId'] ?? author['id']),
      authorName:
          '${json['authorName'] ?? json['nickname'] ?? author['nickname'] ?? '用户'}',
      authorAvatarUrl:
          (json['authorAvatarUrl'] ?? json['avatarUrl'] ?? author['avatarUrl'])
              ?.toString(),
      authorMerchant:
          json['authorMerchant'] == true ||
          json['merchant'] == true ||
          '${author['role'] ?? json['authorRole']}' == 'SHOP',
      title: '${json['title'] ?? ''}',
      content: '${json['content'] ?? ''}',
      createdAt: '${json['publishedAt'] ?? json['createdAt'] ?? ''}',
      editedAt: _meaningfulEditedAt(json),
      status: '${json['status'] ?? 'PUBLISHED'}',
      visibility: '${json['visibility'] ?? 'PUBLIC'}',
      mine:
          json['editable'] == true ||
          json['mine'] == true ||
          json['ownedByMe'] == true,
      liked: json['liked'] == true || json['likedByMe'] == true,
      likeCount: _int(json['likeCount'] ?? json['likes']),
      commentCount: _int(json['commentCount'] ?? json['comments']),
      images: _strings(json['images'] ?? json['imageUrls']),
      topics: (json['topics'] as List? ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(MomentTopic.fromJson)
          .toList(),
      reactions:
          (json['reactions'] as List? ?? json['emojis'] as List? ?? const [])
              .whereType<Map<String, dynamic>>()
              .map(MomentReaction.fromJson)
              .toList(),
      dishId: _nullableInt(
        json['dishId'] ?? (json['reference'] as Map?)?['dishId'],
      ),
      dishName: (json['dishName'] ?? (json['reference'] as Map?)?['dishName'])
          ?.toString(),
      dishImageUrl:
          (json['dishImageUrl'] ?? (json['reference'] as Map?)?['dishImageUrl'])
              ?.toString(),
      dishPrice: _nullableDouble(
        json['dishPrice'] ?? (json['reference'] as Map?)?['dishPrice'],
      ),
      dishDescription:
          (json['dishDescription'] ??
                  (json['reference'] as Map?)?['dishDescription'])
              ?.toString(),
      dishSalesCount: _nullableInt(
        json['dishSalesCount'] ??
            (json['reference'] as Map?)?['dishSalesCount'],
      ),
      shopId: _nullableInt(
        json['shopId'] ?? (json['reference'] as Map?)?['shopId'],
      ),
      shopName: (json['shopName'] ?? (json['reference'] as Map?)?['shopName'])
          ?.toString(),
      shopImageUrl:
          (json['shopImageUrl'] ??
                  json['shopLogoUrl'] ??
                  (json['reference'] as Map?)?['shopImageUrl'])
              ?.toString(),
      unread: json['readByMe'] == false || json['unread'] == true,
      hotComment: json['hotComment'] is Map<String, dynamic>
          ? MomentComment.fromJson(json['hotComment'] as Map<String, dynamic>)
          : null,
    );
  }

  final int id;
  final int authorId;
  final String authorName;
  final String? authorAvatarUrl;
  final bool authorMerchant;
  final String title;
  final String content;
  final String createdAt;
  final String? editedAt;
  final String status;
  final String visibility;
  final bool mine;
  final bool liked;
  final int likeCount;
  final int commentCount;
  final List<String> images;
  final List<MomentTopic> topics;
  final List<MomentReaction> reactions;
  final int? dishId;
  final String? dishName;
  final String? dishImageUrl;
  final double? dishPrice;
  final String? dishDescription;
  final int? dishSalesCount;
  final int? shopId;
  final String? shopName;
  final String? shopImageUrl;
  final bool unread;
  final MomentComment? hotComment;
}

class MomentComment {
  const MomentComment({
    required this.id,
    required this.userId,
    required this.nickname,
    required this.content,
    required this.createdAt,
    this.avatarUrl,
    this.imageUrl,
    this.merchant = false,
    this.author = false,
    this.mine = false,
    this.liked = false,
    this.likeCount = 0,
    this.replyCount = 0,
    this.replyToName,
    this.parentId,
    this.replies = const [],
  });
  factory MomentComment.fromJson(Map<String, dynamic> json) {
    final authorData = json['author'] is Map<String, dynamic>
        ? json['author'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final imageUrls = _strings(json['imageUrls']);
    return MomentComment(
      id: _int(json['id'] ?? json['commentId']),
      userId: _int(json['userId'] ?? json['authorId'] ?? authorData['id']),
      nickname:
          '${json['nickname'] ?? json['authorName'] ?? authorData['nickname'] ?? '用户'}',
      avatarUrl:
          (json['avatarUrl'] ??
                  json['authorAvatarUrl'] ??
                  authorData['avatarUrl'])
              ?.toString(),
      merchant:
          authorData['merchant'] == true || '${authorData['role']}' == 'SHOP',
      content: '${json['content'] ?? ''}',
      imageUrl: json['imageUrl']?.toString() ?? imageUrls.firstOrNull,
      createdAt: '${json['createdAt'] ?? ''}',
      author: json['authorBadge'] == true || json['momentAuthor'] == true,
      mine: json['mine'] == true || json['ownedByMe'] == true,
      liked: json['liked'] == true || json['likedByMe'] == true,
      likeCount: _int(json['likeCount']),
      replyCount: _int(json['replyCount']),
      replyToName: (json['replyToNickname'] ?? json['replyToName'])?.toString(),
      parentId: _nullableInt(json['parentCommentId']),
      replies: (json['replies'] as List? ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(MomentComment.fromJson)
          .toList(),
    );
  }
  final int id;
  final int userId;
  final String nickname;
  final String? avatarUrl;
  final bool merchant;
  final String content;
  final String? imageUrl;
  final String createdAt;
  final bool author;
  final bool mine;
  final bool liked;
  final int likeCount;
  final int replyCount;
  final String? replyToName;
  final int? parentId;
  final List<MomentComment> replies;
}

class MomentPrivacy {
  const MomentPrivacy({
    this.hideTheirMoments = false,
    this.hideMineFromThem = false,
  });
  factory MomentPrivacy.fromJson(Map<String, dynamic> json) => MomentPrivacy(
    hideTheirMoments:
        json['hideTargetMoments'] == true ||
        json['hideTheirMoments'] == true ||
        json['notSeeThem'] == true,
    hideMineFromThem:
        json['hideMyMoments'] == true ||
        json['hideMineFromThem'] == true ||
        json['notLetThemSeeMe'] == true,
  );
  final bool hideTheirMoments;
  final bool hideMineFromThem;
}

class MomentProfileSummary {
  const MomentProfileSummary({
    required this.userId,
    required this.canView,
    required this.hiddenByMe,
    required this.momentCount,
    this.visibility = 'PUBLIC',
    this.reason,
    this.preview = const [],
  });

  factory MomentProfileSummary.fromJson(Map<String, dynamic> json) =>
      MomentProfileSummary(
        userId: _int(json['userId']),
        canView: json['canView'] != false,
        hiddenByMe: json['hiddenByMe'] == true,
        momentCount: _int(json['momentCount']),
        visibility: '${json['visibility'] ?? 'PUBLIC'}',
        reason: json['reason']?.toString(),
        preview: (json['preview'] as List? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(MomentItem.fromJson)
            .toList(),
      );

  final int userId;
  final bool canView;
  final bool hiddenByMe;
  final int momentCount;
  final String visibility;
  final String? reason;
  final List<MomentItem> preview;
}

int _int(Object? value) =>
    value is num ? value.toInt() : int.tryParse('$value') ?? 0;
int? _nullableInt(Object? value) => value == null ? null : _int(value);
double? _nullableDouble(Object? value) =>
    value is num ? value.toDouble() : double.tryParse('$value');
List<String> _strings(Object? value) => (value as List? ?? const [])
    .map((e) {
      if (e is Map) return '${e['url'] ?? e['imageUrl'] ?? ''}';
      return '$e';
    })
    .where((e) => e.isNotEmpty)
    .toList();

String? _meaningfulEditedAt(Map<String, dynamic> json) {
  final raw = json['editedAt']?.toString();
  if (raw == null || raw.isEmpty) return null;
  final edited = DateTime.tryParse(raw);
  final published = DateTime.tryParse('${json['publishedAt'] ?? ''}');
  if (edited != null &&
      published != null &&
      edited.difference(published).abs() < const Duration(seconds: 2)) {
    return null;
  }
  return raw;
}
