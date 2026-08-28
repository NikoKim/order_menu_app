int _momentInt(Object? value) =>
    value is num ? value.toInt() : int.tryParse('$value') ?? 0;

class MomentPage<T> {
  const MomentPage({
    required this.items,
    required this.page,
    required this.hasMore,
    this.total = 0,
  });
  final List<T> items;
  final int page;
  final bool hasMore;
  final int total;

  factory MomentPage.fromJson(
    Object? json,
    T Function(Map<String, dynamic>) decode,
  ) {
    final map = json is Map<String, dynamic> ? json : <String, dynamic>{};
    final raw =
        map['records'] ??
        map['items'] ??
        map['content'] ??
        (json is List ? json : const []);
    final items = raw is List
        ? raw.whereType<Map<String, dynamic>>().map(decode).toList()
        : <T>[];
    final page = _momentInt(map['page'] ?? map['number'] ?? 1);
    final total = _momentInt(map['total'] ?? map['totalElements']);
    final pageSize = _momentInt(map['pageSize'] ?? map['size'] ?? items.length);
    return MomentPage(
      items: items,
      page: page == 0 ? 1 : page,
      hasMore:
          map['hasMore'] == true ||
          map['last'] == false ||
          (total > 0 && page * pageSize < total),
      total: total,
    );
  }
}

class MomentTopic {
  const MomentTopic({
    required this.id,
    required this.name,
    this.usageCount = 0,
    this.userCount = 0,
    this.momentCount = 0,
  });
  final int id;
  final String name;
  final int usageCount;
  final int userCount;
  final int momentCount;
  factory MomentTopic.fromJson(Map<String, dynamic> json) => MomentTopic(
    id: _momentInt(json['id'] ?? json['topicId']),
    name: (json['name'] ?? json['topicName'] ?? '').toString(),
    usageCount: _momentInt(
      json['usageCount'] ?? json['userCount'] ?? json['momentCount'],
    ),
    userCount: _momentInt(json['userCount'] ?? json['usageCount']),
    momentCount: _momentInt(json['momentCount'] ?? json['usageCount']),
  );
}

class MomentReaction {
  const MomentReaction({
    required this.emoji,
    required this.count,
    this.selected = false,
  });
  final String emoji;
  final int count;
  final bool selected;
  factory MomentReaction.fromJson(Map<String, dynamic> json) => MomentReaction(
    emoji: (json['emoji'] ?? json['code'] ?? '').toString(),
    count: _momentInt(json['count']),
    selected: json['selected'] == true || json['reactedByMe'] == true,
  );
}

class MomentUser {
  const MomentUser({
    required this.id,
    required this.nickname,
    this.avatarUrl,
    this.role = 'CUSTOMER',
    this.merchant = false,
  });

  final int id;
  final String nickname;
  final String? avatarUrl;
  final String role;
  final bool merchant;

  factory MomentUser.fromJson(Map<String, dynamic> json) => MomentUser(
    id: _momentInt(json['id'] ?? json['userId']),
    nickname: (json['nickname'] ?? '用户').toString(),
    avatarUrl: json['avatarUrl']?.toString(),
    role: (json['role'] ?? 'CUSTOMER').toString(),
    merchant: json['merchant'] == true || json['role'] == 'SHOP',
  );
}

class MomentUserCard extends MomentUser {
  const MomentUserCard({
    required super.id,
    required super.nickname,
    super.avatarUrl,
    super.role,
    super.merchant,
    this.phone,
    this.phoneVisible = false,
    this.self = false,
  });

  final String? phone;
  final bool phoneVisible;
  final bool self;

  factory MomentUserCard.fromJson(Map<String, dynamic> json) => MomentUserCard(
    id: _momentInt(json['id'] ?? json['userId']),
    nickname: (json['nickname'] ?? '用户').toString(),
    avatarUrl: json['avatarUrl']?.toString(),
    role: (json['role'] ?? 'CUSTOMER').toString(),
    merchant: json['merchant'] == true || json['role'] == 'SHOP',
    phone: json['phone']?.toString(),
    phoneVisible: json['phoneVisible'] == true,
    self: json['self'] == true,
  );
}

class MomentQuote {
  const MomentQuote({
    required this.type,
    required this.momentId,
    required this.content,
    required this.createdAt,
    this.commentId,
    this.author,
    this.momentTitle = '',
    this.available = true,
    this.unavailableReason,
  });

  final String type;
  final int momentId;
  final int? commentId;
  final MomentUser? author;
  final String momentTitle;
  final String content;
  final String createdAt;
  final bool available;
  final String? unavailableReason;

  factory MomentQuote.fromJson(Map<String, dynamic> json) => MomentQuote(
    type: (json['type'] ?? 'MOMENT').toString(),
    momentId: _momentInt(json['momentId']),
    commentId: json['commentId'] == null ? null : _momentInt(json['commentId']),
    author: json['author'] is Map<String, dynamic>
        ? MomentUser.fromJson(json['author'] as Map<String, dynamic>)
        : null,
    momentTitle: (json['momentTitle'] ?? '').toString(),
    content: (json['content'] ?? '').toString(),
    createdAt: (json['createdAt'] ?? '').toString(),
    available: json['available'] != false,
    unavailableReason: json['unavailableReason']?.toString(),
  );
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
    this.imageUrls = const [],
    this.parentId,
    this.replyToUserId,
    this.replyToNickname,
    this.merchant = false,
    this.author = false,
    this.mine = false,
    this.liked = false,
    this.likeCount = 0,
    this.replyCount = 0,
    this.replies = const [],
    this.mentions = const [],
  });
  final int id;
  final int userId;
  final String nickname;
  final String content;
  final String createdAt;
  final String? avatarUrl;
  final String? imageUrl;
  final List<String> imageUrls;
  final int? parentId;
  final int? replyToUserId;
  final String? replyToNickname;
  final bool merchant;
  final bool author;
  final bool mine;
  final bool liked;
  final int likeCount;
  final int replyCount;
  final List<MomentComment> replies;
  final List<MomentUser> mentions;

  factory MomentComment.fromJson(Map<String, dynamic> json) {
    final replyData = json['replies'] ?? json['replyPreview'];
    final authorData = json['author'] is Map<String, dynamic>
        ? json['author'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final rawImages = json['imageUrls'];
    final replies = replyData is List
        ? replyData
              .whereType<Map<String, dynamic>>()
              .map(MomentComment.fromJson)
              .toList()
        : <MomentComment>[];
    replies.sort((a, b) {
      final byTime = a.createdAt.compareTo(b.createdAt);
      return byTime != 0 ? byTime : a.id.compareTo(b.id);
    });
    return MomentComment(
      id: _momentInt(json['id']),
      userId: _momentInt(
        authorData['id'] ?? json['userId'] ?? json['authorId'],
      ),
      nickname:
          (authorData['nickname'] ??
                  json['nickname'] ??
                  json['authorNickname'] ??
                  '用户')
              .toString(),
      content: (json['content'] ?? '').toString(),
      createdAt: (json['createdAt'] ?? '').toString(),
      avatarUrl:
          (authorData['avatarUrl'] ??
                  json['avatarUrl'] ??
                  json['authorAvatarUrl'])
              ?.toString(),
      imageUrl: rawImages is List && rawImages.isNotEmpty
          ? rawImages.first.toString()
          : json['imageUrl']?.toString(),
      imageUrls: rawImages is List
          ? rawImages
                .map((item) => item.toString())
                .where((e) => e.isNotEmpty)
                .toList()
          : json['imageUrl']?.toString().isNotEmpty == true
          ? [json['imageUrl'].toString()]
          : const [],
      parentId: (json['parentCommentId'] ?? json['parentId']) == null
          ? null
          : _momentInt(json['parentCommentId'] ?? json['parentId']),
      replyToUserId: json['replyToUserId'] == null
          ? null
          : _momentInt(json['replyToUserId']),
      replyToNickname: json['replyToNickname']?.toString(),
      merchant:
          authorData['merchant'] == true ||
          authorData['role'] == 'SHOP' ||
          json['merchant'] == true,
      author: json['authorBadge'] == true || json['momentAuthor'] == true,
      mine: json['mine'] == true || json['ownedByMe'] == true,
      liked: json['liked'] == true || json['likedByMe'] == true,
      likeCount: _momentInt(json['likeCount']),
      replyCount: _momentInt(json['replyCount']),
      replies: replies,
      mentions: json['mentions'] is List
          ? (json['mentions'] as List)
                .whereType<Map<String, dynamic>>()
                .map(MomentUser.fromJson)
                .toList()
          : const [],
    );
  }
}

class Moment {
  const Moment({
    required this.id,
    required this.authorId,
    required this.authorNickname,
    required this.title,
    required this.content,
    required this.visibility,
    required this.createdAt,
    this.authorAvatarUrl,
    this.merchant = false,
    this.mine = false,
    this.editedAt,
    this.images = const [],
    this.topics = const [],
    this.liked = false,
    this.likeCount = 0,
    this.commentCount = 0,
    this.reactions = const [],
    this.shopId,
    this.shopName,
    this.dishId,
    this.dishName,
    this.comments = const [],
    this.unread = false,
    this.status = 'PUBLISHED',
    this.dishImageUrl,
    this.publishedAt,
    this.shopImageUrl,
    this.dishPrice,
    this.dishDescription,
    this.dishSalesCount,
    this.hotComment,
    this.quote,
    this.mentions = const [],
    this.rootCommentCount = 0,
    this.shareCount = 0,
    this.appShareCount = 0,
    this.externalShareCount = 0,
  });
  final int id;
  final int authorId;
  final String authorNickname;
  final String? authorAvatarUrl;
  final bool merchant;
  final bool mine;
  final String title;
  final String content;
  final String visibility;
  final String createdAt;
  final String? publishedAt;
  final String? editedAt;
  final List<String> images;
  final List<MomentTopic> topics;
  final bool liked;
  final int likeCount;
  final int commentCount;
  final List<MomentReaction> reactions;
  final int? shopId;
  final String? shopName;
  final int? dishId;
  final String? dishName;
  final List<MomentComment> comments;
  final bool unread;
  final String status;
  final String? dishImageUrl;
  final String? shopImageUrl;
  final double? dishPrice;
  final String? dishDescription;
  final int? dishSalesCount;
  final MomentComment? hotComment;
  final MomentQuote? quote;
  final List<MomentUser> mentions;
  final int rootCommentCount;
  final int shareCount;
  final int appShareCount;
  final int externalShareCount;

  factory Moment.fromJson(Map<String, dynamic> json) {
    final authorData = json['author'] is Map<String, dynamic>
        ? json['author'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final reference = json['reference'] is Map<String, dynamic>
        ? json['reference'] as Map<String, dynamic>
        : const <String, dynamic>{};
    List<String> imageList = const [];
    final rawImages = json['images'] ?? json['imageUrls'];
    if (rawImages is List) {
      imageList = rawImages
          .map(
            (item) =>
                item is Map ? item['url']?.toString() ?? '' : item.toString(),
          )
          .where((e) => e.isNotEmpty)
          .toList();
    }
    return Moment(
      id: _momentInt(json['id']),
      authorId: _momentInt(
        authorData['id'] ?? json['authorId'] ?? json['userId'],
      ),
      authorNickname:
          (authorData['nickname'] ??
                  json['authorNickname'] ??
                  json['nickname'] ??
                  '用户')
              .toString(),
      authorAvatarUrl:
          (authorData['avatarUrl'] ??
                  json['authorAvatarUrl'] ??
                  json['avatarUrl'])
              ?.toString(),
      merchant:
          authorData['merchant'] == true ||
          authorData['role'] == 'SHOP' ||
          json['merchant'] == true,
      mine:
          json['editable'] == true ||
          json['mine'] == true ||
          json['ownedByMe'] == true,
      title: (json['title'] ?? '').toString(),
      content: (json['content'] ?? '').toString(),
      visibility: (json['visibility'] ?? 'PUBLIC').toString(),
      createdAt: (json['createdAt'] ?? '').toString(),
      publishedAt: json['publishedAt']?.toString(),
      editedAt: json['editedAt']?.toString(),
      images: imageList,
      topics: (json['topics'] is List)
          ? (json['topics'] as List)
                .whereType<Map<String, dynamic>>()
                .map(MomentTopic.fromJson)
                .toList()
          : const [],
      liked: json['liked'] == true || json['likedByMe'] == true,
      likeCount: _momentInt(json['likeCount']),
      commentCount: _momentInt(json['commentCount']),
      rootCommentCount: _momentInt(json['rootCommentCount']),
      shareCount: _momentInt(json['shareCount']),
      appShareCount: _momentInt(json['appShareCount']),
      externalShareCount: _momentInt(json['externalShareCount']),
      reactions: (json['reactions'] is List)
          ? (json['reactions'] as List)
                .whereType<Map<String, dynamic>>()
                .map(MomentReaction.fromJson)
                .toList()
          : const [],
      shopId: (reference['shopId'] ?? json['shopId']) == null
          ? null
          : _momentInt(reference['shopId'] ?? json['shopId']),
      shopName: (reference['shopName'] ?? json['shopName'])?.toString(),
      dishId: (reference['dishId'] ?? json['dishId']) == null
          ? null
          : _momentInt(reference['dishId'] ?? json['dishId']),
      dishName: (reference['dishName'] ?? json['dishName'])?.toString(),
      comments: (json['comments'] is List)
          ? (json['comments'] as List)
                .whereType<Map<String, dynamic>>()
                .map(MomentComment.fromJson)
                .toList()
          : const [],
      unread:
          json['unread'] == true ||
          json['readByMe'] == false ||
          json['read'] == false,
      status: (json['status'] ?? 'PUBLISHED').toString(),
      dishImageUrl: (reference['dishImageUrl'] ?? json['dishImageUrl'])
          ?.toString(),
      shopImageUrl: (reference['shopImageUrl'] ?? json['shopImageUrl'])
          ?.toString(),
      dishPrice: (reference['dishPrice'] is num)
          ? (reference['dishPrice'] as num).toDouble()
          : double.tryParse('${reference['dishPrice'] ?? ''}'),
      dishDescription: reference['dishDescription']?.toString(),
      dishSalesCount: reference['dishSalesCount'] == null
          ? null
          : _momentInt(reference['dishSalesCount']),
      hotComment: json['hotComment'] is Map<String, dynamic>
          ? MomentComment.fromJson(json['hotComment'] as Map<String, dynamic>)
          : null,
      quote: json['quote'] is Map<String, dynamic>
          ? MomentQuote.fromJson(json['quote'] as Map<String, dynamic>)
          : null,
      mentions: json['mentions'] is List
          ? (json['mentions'] as List)
                .whereType<Map<String, dynamic>>()
                .map(MomentUser.fromJson)
                .toList()
          : const [],
    );
  }
}

class MomentPrivacy {
  const MomentPrivacy({
    this.hideTheirMoments = false,
    this.hideMineFromThem = false,
    this.profileVisibility = 'PUBLIC',
  });
  final bool hideTheirMoments;
  final bool hideMineFromThem;
  final String profileVisibility;
  factory MomentPrivacy.fromJson(Map<String, dynamic> json) => MomentPrivacy(
    hideTheirMoments:
        json['hideTargetMoments'] == true ||
        json['hideTheirMoments'] == true ||
        json['notSeeTheirMoments'] == true,
    hideMineFromThem:
        json['hideMyMoments'] == true ||
        json['hideMineFromThem'] == true ||
        json['notAllowThemSeeMine'] == true,
    profileVisibility:
        (json['profileVisibility'] ?? json['momentPageVisibility'] ?? 'PUBLIC')
            .toString(),
  );
}

class MomentProfileSummary {
  const MomentProfileSummary({
    required this.userId,
    required this.visibility,
    required this.canView,
    required this.hiddenByMe,
    required this.momentCount,
    this.reason,
    this.preview = const [],
  });
  final int userId;
  final String visibility;
  final bool canView;
  final bool hiddenByMe;
  final String? reason;
  final int momentCount;
  final List<Moment> preview;
  factory MomentProfileSummary.fromJson(Map<String, dynamic> json) =>
      MomentProfileSummary(
        userId: _momentInt(json['userId']),
        visibility: (json['visibility'] ?? 'PUBLIC').toString(),
        canView: json['canView'] == true,
        hiddenByMe: json['hiddenByMe'] == true,
        reason: json['reason']?.toString(),
        momentCount: _momentInt(json['momentCount']),
        preview: json['preview'] is List
            ? (json['preview'] as List)
                  .whereType<Map<String, dynamic>>()
                  .map(Moment.fromJson)
                  .toList()
            : const [],
      );
}
