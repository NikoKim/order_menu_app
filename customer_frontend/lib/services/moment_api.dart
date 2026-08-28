import '../models.dart';
import '../moment_models.dart';
import 'api_client.dart';

extension CustomerMomentApi on CustomerApi {
  Future<int> relatedMomentUnreadCount() => api.get(
    '/moments/unread-count',
    (json) => json is Map
        ? (json['relatedUnreadCount'] is num
              ? (json['relatedUnreadCount'] as num).toInt()
              : int.tryParse('${json['relatedUnreadCount']}') ?? 0)
        : 0,
  );

  Future<void> markMomentRead(int id) => markMomentsRead([id]);

  Future<void> markMomentsRead(Iterable<int> ids) =>
      api.put('/moments/read', (_) {}, body: {'momentIds': ids.toList()});

  Future<MomentPage<Moment>> moments({
    String feed = 'RECOMMENDED',
    int page = 1,
    int pageSize = 10,
    String? keyword,
    int? authorId,
    int? topicId,
    bool consumeExposure = true,
  }) => api.get(
    '/moments',
    (json) => MomentPage.fromJson(json, Moment.fromJson),
    query: {
      'feed': feed,
      'page': '$page',
      'pageSize': '$pageSize',
      'keyword': keyword,
      'authorId': authorId?.toString(),
      'topicId': topicId?.toString(),
      'consumeExposure': '$consumeExposure',
    },
  );

  Future<List<Moment>> hotMoments({int limit = 10}) => api.get(
    '/moments/hot',
    (json) => (json as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(Moment.fromJson)
        .toList(),
    query: {'limit': '$limit'},
  );

  Future<Moment> moment(int id) => api.get(
    '/moments/$id',
    (json) => Moment.fromJson(json as Map<String, dynamic>),
  );

  Future<String?> momentUnavailableReason(int id) => api.get(
    '/moments/$id/availability',
    (json) => json is Map ? json['reason']?.toString() : null,
  );

  Future<Moment> publishMoment({
    int? id,
    required String title,
    required String content,
    required String visibility,
    List<String> topics = const [],
    List<String> imageUrls = const [],
    int? shopId,
    int? dishId,
    bool draft = false,
    int? quotedMomentId,
    int? quotedCommentId,
    List<int> mentionedUserIds = const [],
  }) {
    final body = {
      'title': title.trim(),
      'content': content.trim(),
      'visibility': visibility,
      'topics': topics,
      'imageUrls': imageUrls,
      'shopId': shopId,
      'dishId': dishId,
      'status': draft ? 'DRAFT' : 'PUBLISHED',
      'quotedMomentId': quotedMomentId,
      'quotedCommentId': quotedCommentId,
      'mentionedUserIds': mentionedUserIds,
    }..removeWhere((_, value) => value == null);
    if (id == null) {
      return api.post(
        '/moments',
        (json) => Moment.fromJson(json as Map<String, dynamic>),
        body: body,
      );
    }
    return api.put(
      '/moments/$id',
      (json) => Moment.fromJson(json as Map<String, dynamic>),
      body: body,
    );
  }

  Future<void> deleteMoment(int id) => api.delete('/moments/$id', (_) {});

  Future<String> uploadMomentImage(String filePath) => api.upload(
    '/moments/images',
    (json) => json is Map
        ? (json['url'] ?? json['imageUrl'] ?? '').toString()
        : json.toString(),
    field: 'image',
    filePath: filePath,
  );

  Future<Moment> setMomentLiked(int id, bool liked) => api.put(
    '/moments/$id/like',
    (json) => Moment.fromJson(json as Map<String, dynamic>),
    body: {'active': liked},
  );

  Future<Moment> setMomentReaction(int id, String emoji, bool selected) =>
      api.put(
        '/moments/$id/reactions',
        (json) => Moment.fromJson(json as Map<String, dynamic>),
        body: {'emoji': emoji, 'active': selected},
      );

  Future<MomentPage<MomentComment>> momentComments(
    int id, {
    int page = 1,
    int pageSize = 10,
  }) => api.get(
    '/moments/$id/comments',
    (json) => MomentPage.fromJson(json, MomentComment.fromJson),
    query: {'page': '$page', 'pageSize': '$pageSize'},
  );

  Future<MomentPage<MomentComment>> commentReplies(
    int momentId,
    int commentId, {
    int page = 1,
    int pageSize = 10,
  }) => api.get(
    '/moments/$momentId/comments/$commentId/replies',
    (json) => MomentPage.fromJson(json, MomentComment.fromJson),
    query: {'page': '$page', 'pageSize': '$pageSize'},
  );

  Future<MomentComment> momentComment(int momentId, int commentId) => api.get(
    '/moments/$momentId/comments/$commentId',
    (json) => MomentComment.fromJson(json as Map<String, dynamic>),
  );

  Future<String> uploadMomentCommentImage(String filePath) => api.upload(
    '/moments/comment-images',
    (json) => json is Map
        ? (json['url'] ?? json['imageUrl'] ?? '').toString()
        : json.toString(),
    field: 'image',
    filePath: filePath,
  );

  Future<MomentComment> addMomentComment(
    int momentId, {
    required String content,
    String? imageUrl,
    List<String> imageUrls = const [],
    int? parentId,
    int? replyToCommentId,
    List<int> mentionedUserIds = const [],
  }) => api.post(
    '/moments/$momentId/comments',
    (json) => MomentComment.fromJson(json as Map<String, dynamic>),
    body: {
      'content': content.trim(),
      'imageUrls': imageUrls.isNotEmpty
          ? imageUrls
          : imageUrl == null
          ? <String>[]
          : [imageUrl],
      'parentCommentId': parentId,
      'replyToCommentId': replyToCommentId,
      'mentionedUserIds': mentionedUserIds,
    }..removeWhere((_, value) => value == null),
  );

  Future<void> deleteMomentComment(int momentId, int commentId) =>
      api.delete('/moments/$momentId/comments/$commentId', (_) {});

  Future<MomentComment> setCommentLiked(
    int momentId,
    int commentId,
    bool liked,
  ) => api.put(
    '/moments/$momentId/comments/$commentId/like',
    (json) => MomentComment.fromJson(json as Map<String, dynamic>),
    body: {'active': liked},
  );

  Future<List<MomentTopic>> searchMomentTopics(String keyword) => api.get(
    '/moments/topics/suggestions',
    (json) => (json as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(MomentTopic.fromJson)
        .toList(),
    query: {'query': keyword.trim()},
  );

  Future<List<MomentUser>> searchMomentMentionUsers(String keyword) => api.get(
    '/moments/mention-users',
    (json) => (json as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(MomentUser.fromJson)
        .toList(),
    query: {'query': keyword.trim()},
  );

  Future<MomentUserCard> momentUserCard(int userId) => api.get(
    '/moments/users/$userId/card',
    (json) => MomentUserCard.fromJson(json as Map<String, dynamic>),
  );

  Future<MomentPage<MomentTopic>> momentTopics({
    String query = '',
    int page = 1,
    int pageSize = 10,
  }) => api.get(
    '/moments/topics',
    (json) => MomentPage.fromJson(json, MomentTopic.fromJson),
    query: {'query': query.trim(), 'page': '$page', 'pageSize': '$pageSize'},
  );

  Future<MomentTopic> createMomentTopic(String name) => api.post(
    '/moments/topics',
    (json) => MomentTopic.fromJson(json as Map<String, dynamic>),
    body: {'name': name.trim()},
  );

  Future<List<Moment>> momentDrafts() => api.get(
    '/moments/drafts',
    (json) => MomentPage.fromJson(json, Moment.fromJson).items,
  );

  Future<MomentPrivacy> momentPrivacy(int userId) => api.get(
    '/moments/users/$userId/privacy',
    (json) => MomentPrivacy.fromJson(json as Map<String, dynamic>),
  );

  Future<MomentProfileSummary> momentProfileSummary(int userId) => api.get(
    '/moments/users/$userId/summary',
    (json) => MomentProfileSummary.fromJson(json as Map<String, dynamic>),
  );

  Future<MomentPrivacy> updateMomentPrivacyForUser(
    int userId, {
    bool? hideTheirMoments,
    bool? hideMineFromThem,
  }) => api.put(
    '/moments/users/$userId/privacy',
    (json) => MomentPrivacy.fromJson(json as Map<String, dynamic>),
    body: {
      'hideTargetMoments': hideTheirMoments,
      'hideMyMoments': hideMineFromThem,
    }..removeWhere((_, value) => value == null),
  );

  Future<void> updateMyMomentPrivacy(String visibility) => api.put(
    '/moments/privacy/profile',
    (_) {},
    body: {'visibility': visibility},
  );

  Future<MomentProfileSummary> myMomentProfileSummary() => api.get(
    '/moments/privacy/profile',
    (json) => MomentProfileSummary.fromJson(json as Map<String, dynamic>),
  );

  Future<ChatMessage> shareMoment({
    required int conversationId,
    required int momentId,
    int? commentId,
  }) => api.post(
    '/moments/$momentId/share',
    (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
    body: {'conversationId': conversationId, 'commentId': commentId},
  );

  Future<Map<String, int>> reportExternalMomentShare(
    int momentId, {
    int? commentId,
  }) => api.post(
    '/moments/$momentId/external-shares',
    (json) {
      final map = json as Map<String, dynamic>;
      int value(String key) => map[key] is num
          ? (map[key] as num).toInt()
          : int.tryParse('${map[key]}') ?? 0;
      return {
        'shareCount': value('shareCount'),
        'appShareCount': value('appShareCount'),
        'externalShareCount': value('externalShareCount'),
      };
    },
    body: {'commentId': commentId}..removeWhere((_, value) => value == null),
  );
}
