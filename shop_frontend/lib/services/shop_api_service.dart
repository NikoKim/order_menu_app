import 'dart:convert';
import 'dart:async';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../models/shop_api_models.dart';
import '../models/moment_models.dart';
import 'api_config.dart';
import 'auth_session.dart';

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class ShopApiService {
  ShopApiService({http.Client? client, String? baseUrl, String? token})
    : _client = client ?? http.Client(),
      _baseUrl = Uri.parse(baseUrl ?? ApiConfig.baseUrl),
      // ignore: prefer_initializing_formals
      _token = token;

  final http.Client _client;
  final Uri _baseUrl;
  String? _token;

  String? get token => _token;

  Future<void> loadSavedToken() async {
    _token = await AuthSession.loadToken();
  }

  void setToken(String token) {
    _token = token;
  }

  Future<Map<String, dynamic>> checkUpdate(
    String appCode,
    int currentVersionCode,
  ) async {
    try {
      // 注意：authenticated 设为 false，因为检查更新不需要也不应该被 Token 拦截
      final data = await _request<Map<String, dynamic>>(
        'GET',
        '/version/check',
        query: {
          'appCode': appCode,
          'currentVersionCode': currentVersionCode.toString(),
        },
        authenticated: false,
      );
      return data;
    } catch (e) {
      // 忽略网络异常，防止断网时影响APP正常打开
      return {'hasUpdate': false};
    }
  }

  Future<LoginResult> login({
    required String nickname,
    required String password,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/auth/login',
      body: {'nickname': nickname, 'password': password, 'role': 'SHOP'},
      authenticated: false,
    );
    final result = LoginResult.fromJson(data);
    _token = result.token;
    await AuthSession.save(token: result.token, nickname: result.user.nickname);
    return result;
  }

  Future<void> logout() async {
    _token = null;
    await AuthSession.clear();
  }

  Future<CurrentUser> fetchCurrentUser() async {
    final data = await _request<Map<String, dynamic>>('GET', '/users/me');
    return CurrentUser.fromJson(data);
  }

  Future<CurrentUser> updatePrivateChatSetting({
    required bool allowPrivateChat,
    bool? showPhone,
    bool? showJoinedFamilies,
  }) async {
    final current = await fetchCurrentUser();
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/users/me',
      body: {
        'nickname': current.nickname,
        'phone': current.phone,
        'avatarUrl': current.avatarUrl,
        'allowPrivateChat': allowPrivateChat,
        'showPhone': showPhone ?? current.showPhone,
        'showJoinedFamilies': showJoinedFamilies ?? current.showJoinedFamilies,
        'momentProfileVisibility': current.momentProfileVisibility,
      },
    );
    return CurrentUser.fromJson(data);
  }

  Future<List<ChatConversation>> fetchConversations() async {
    final data = await _request<List<dynamic>>(
      'GET',
      '/messages/conversations',
    );
    return data
        .whereType<Map<String, dynamic>>()
        .map(ChatConversation.fromJson)
        .toList();
  }

  Future<ChatMessages> fetchChatMessages(int conversationId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/messages/conversations/$conversationId/messages',
    );
    return ChatMessages.fromJson(data);
  }

  Future<ChatMessage> sendChatMessage(
    int conversationId,
    String content, {
    int? quotedMessageId,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/messages/conversations/$conversationId/messages',
      body: {'content': content.trim(), 'quotedMessageId': quotedMessageId},
    );
    return ChatMessage.fromJson(data);
  }

  Future<ChatMessage> sendChatImage(
    int conversationId,
    String filePath, {
    String? content,
    int? quotedMessageId,
  }) async {
    final request = http.MultipartRequest(
      'POST',
      _uri('/messages/conversations/$conversationId/images'),
    );
    request.headers.addAll(await _headers(jsonContent: false));
    if (content?.trim().isNotEmpty == true) {
      request.fields['content'] = content!.trim();
    }
    if (quotedMessageId != null) {
      request.fields['quotedMessageId'] = '$quotedMessageId';
    }
    request.files.add(await http.MultipartFile.fromPath('image', filePath));
    final response = await http.Response.fromStream(
      await _client.send(request),
    );
    final decoded = _decode(response);
    if (decoded is Map<String, dynamic> && decoded['code'] == 0) {
      return ChatMessage.fromJson(
        decoded['data'] as Map<String, dynamic>? ?? const {},
      );
    }
    throw ApiException(
      decoded is Map<String, dynamic>
          ? decoded['message']?.toString() ?? '图片发送失败'
          : '图片发送失败',
    );
  }

  Future<ChatConversation> updateConversationPreferences(
    int conversationId, {
    bool? muted,
    bool? hidden,
    bool? pinned,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/messages/conversations/$conversationId/preferences',
      body: {'muted': muted, 'hidden': hidden, 'pinned': pinned},
    );
    return ChatConversation.fromJson(data);
  }

  Future<ConversationHideResult> deleteConversation(
    int conversationId, {
    int? expectedMessageVersion,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'DELETE',
      '/messages/conversations/$conversationId',
      query: {
        'guarded': 'true',
        if (expectedMessageVersion != null)
          'expectedMessageVersion': '$expectedMessageVersion',
      },
    );
    return ConversationHideResult.fromJson(data);
  }

  Future<ChatUserProfile> fetchChatUserProfile(
    int userId, {
    int? conversationId,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/messages/users/$userId/profile',
      query: conversationId == null
          ? null
          : {'conversationId': '$conversationId'},
    );
    return ChatUserProfile.fromJson(data);
  }

  Future<void> addChatEssence(int conversationId, int messageId) async {
    await _request<Map<String, dynamic>>(
      'POST',
      '/messages/family-groups/$conversationId/essences/$messageId',
    );
  }

  Future<void> removeChatEssence(int conversationId, int messageId) =>
      _request<void>(
        'DELETE',
        '/messages/family-groups/$conversationId/essences/$messageId',
      );

  Future<ChatEssenceList> fetchChatEssences(int conversationId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/messages/family-groups/$conversationId/essences',
    );
    return ChatEssenceList.fromJson(data);
  }

  Future<void> setChatEssencePinned(
    int conversationId,
    int messageId,
    bool pinned,
  ) async {
    await _request<Map<String, dynamic>>(
      'PUT',
      '/messages/family-groups/$conversationId/essences/$messageId/pin',
      body: {'pinned': pinned},
    );
  }

  Future<void> setChatEssenceLiked(
    int conversationId,
    int messageId,
    bool liked,
  ) async {
    await _request<Map<String, dynamic>>(
      'PUT',
      '/messages/family-groups/$conversationId/essences/$messageId/like',
      body: {'liked': liked},
    );
  }

  Future<void> markChatEssencesRead(
    int conversationId, {
    int? throughMessageId,
  }) => _request<void>(
    'PUT',
    '/messages/family-groups/$conversationId/essences/read',
    body: {'throughMessageId': throughMessageId},
  );

  Future<ChatMessage?> fetchLatestUnreadChatEssence(int conversationId) async {
    final data = await _request<dynamic>(
      'GET',
      '/messages/family-groups/$conversationId/essences/latest-unread',
    );
    if (data is! Map<String, dynamic>) return null;
    return ChatMessage.fromJson(
      data['message'] is Map<String, dynamic>
          ? {
              ...(data['message'] as Map<String, dynamic>),
              'essence': true,
              'essencePinned': data['pinned'],
              'essenceLikeCount': data['likeCount'],
              'essenceLikedByMe': data['liked'],
            }
          : data,
    );
  }

  Future<ChatMessage> shareShopToConversation({
    required int conversationId,
    required int shopId,
    int? quotedMessageId,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/messages/shop-shares',
      body: {
        'conversationId': conversationId,
        'shopId': shopId,
        'quotedMessageId': quotedMessageId,
      },
    );
    return ChatMessage.fromJson(data);
  }

  Future<void> registerPushDevice({
    required String token,
    required String platform,
  }) => _request<void>(
    'POST',
    '/messages/push-devices',
    body: {'token': token, 'platform': platform},
  );

  Future<void> unregisterPushDevice(String token) async {
    final uri = Uri.parse(
      '${_uri('/messages/push-devices')}/${Uri.encodeComponent(token)}',
    );
    final response = await _client.delete(uri, headers: await _headers());
    final decoded = _decode(response);
    if (decoded is Map<String, dynamic> && decoded['code'] != 0) {
      throw ApiException(decoded['message']?.toString() ?? '取消推送注册失败');
    }
  }

  Future<ChatMessage> recallChatMessage(
    int conversationId,
    int messageId,
  ) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/messages/conversations/$conversationId/messages/$messageId/recall',
    );
    return ChatMessage.fromJson(data);
  }

  Future<void> deleteChatMessage(int conversationId, int messageId) =>
      _request<void>(
        'DELETE',
        '/messages/conversations/$conversationId/messages/$messageId',
      );

  Future<FamilyGroup> createFamilyGroup() async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/messages/family-groups',
    );
    return FamilyGroup.fromJson(data);
  }

  Future<FamilyGroup> fetchFamilyGroup(int conversationId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/messages/family-groups/$conversationId',
    );
    return FamilyGroup.fromJson(data);
  }

  Future<FamilyGroup> updateFamilyGroup(
    int conversationId, {
    String? name,
    String? announcement,
    String? coverImageUrl,
    bool? joinApprovalRequired,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/messages/family-groups/$conversationId',
      body: {
        'name': name,
        'announcement': announcement,
        'coverImageUrl': coverImageUrl,
        'joinApprovalRequired': joinApprovalRequired,
      },
    );
    return FamilyGroup.fromJson(data);
  }

  Future<void> dissolveFamilyGroup(int conversationId) => _request<void>(
    'POST',
    '/messages/family-groups/$conversationId/dissolve',
  );
  Future<void> restoreFamilyGroup(int conversationId) =>
      _request<void>('POST', '/messages/family-groups/$conversationId/restore');
  Future<void> removeFamilyGroupMember(int conversationId, int userId) =>
      _request<void>(
        'POST',
        '/messages/family-groups/$conversationId/members/$userId/remove',
      );
  Future<void> approveFamilyGroupMember(int conversationId, int userId) =>
      _request<void>(
        'POST',
        '/messages/family-groups/$conversationId/members/$userId/approve',
      );
  Future<void> rejectFamilyGroupMember(int conversationId, int userId) =>
      _request<void>(
        'POST',
        '/messages/family-groups/$conversationId/members/$userId/reject',
      );
  Future<void> setFamilyGroupMuted(int conversationId, bool muted) =>
      _request<void>(
        'PUT',
        '/messages/family-groups/$conversationId/mute',
        body: {'muted': muted},
      );

  Future<List<UserNotification>> fetchMessageNotifications() async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/messages/notifications',
    );
    return (data['notifications'] as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(UserNotification.fromJson)
        .toList();
  }

  Future<int> fetchUnreadMessageNotificationCount() async {
    final data = await _request<dynamic>(
      'GET',
      '/messages/notifications/unread-count',
    );
    return data is num ? data.toInt() : int.tryParse('$data') ?? 0;
  }

  Future<void> markAllMessageNotificationsRead() =>
      _request<void>('PUT', '/messages/notifications/read-all');

  Future<void> markMessageNotificationRead(int notificationId) =>
      _request<void>('PUT', '/messages/notifications/$notificationId/read');

  Future<ChatBlockState> fetchChatBlock(int userId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/messages/blocks/users/$userId',
    );
    return ChatBlockState.fromJson(data);
  }

  Future<void> blockChatUser(int userId) =>
      _request<void>('POST', '/messages/blocks/users/$userId');

  Future<void> unblockChatUser(int userId) =>
      _request<void>('DELETE', '/messages/blocks/users/$userId');

  Future<ShopInfo> fetchMyShop() async {
    final data = await _request<Map<String, dynamic>>('GET', '/shop/me');
    return ShopInfo.fromJson(data);
  }

  Future<ShopInfo> updateMyShop({
    required String name,
    String? description,
    String? coverImageUrl,
    String? address,
    String? phone,
    required String businessStatus,
    String? visibilityType,
    String? familyName,
    String? familyJoinMode,
    String? familyImageUrl,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/shop/me',
      body: {
        'name': name,
        'description': description,
        'coverImageUrl': coverImageUrl,
        'address': address,
        'phone': phone,
        'businessStatus': businessStatus,
        'visibilityType': visibilityType,
        'familyName': familyName,
        'familyJoinMode': familyJoinMode,
        'familyImageUrl': familyImageUrl,
      },
    );
    return ShopInfo.fromJson(data);
  }

  Future<List<CategoryInfo>> fetchCategories() async {
    final data = await _request<List<dynamic>>('GET', '/shop/categories');
    return data
        .whereType<Map<String, dynamic>>()
        .map(CategoryInfo.fromJson)
        .toList();
  }

  Future<CategoryInfo> createCategory({
    required String name,
    required int sortOrder,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/shop/categories',
      body: {'name': name, 'sortOrder': sortOrder},
    );
    return CategoryInfo.fromJson(data);
  }

  Future<CategoryInfo> updateCategory({
    required int categoryId,
    required String name,
    required int sortOrder,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/shop/categories/$categoryId',
      body: {'name': name, 'sortOrder': sortOrder},
    );
    return CategoryInfo.fromJson(data);
  }

  Future<void> deleteCategory(int categoryId) async {
    await _request<void>('DELETE', '/shop/categories/$categoryId');
  }

  Future<PageResult<DishInfo>> fetchDishes({
    int page = 1,
    int pageSize = 100,
    int? categoryId,
    String? keyword,
    String? status,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/shop/dishes',
      query: {
        'page': '$page',
        'pageSize': '$pageSize',
        if (categoryId != null) 'categoryId': '$categoryId',
        if (keyword != null && keyword.trim().isNotEmpty) 'keyword': keyword,
        if (status != null && status.trim().isNotEmpty) 'status': status,
      },
    );
    return PageResult.fromJson(data, DishInfo.fromJson);
  }

  Future<DishInfo> createDish(Map<String, dynamic> body) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/shop/dishes',
      body: body,
    );
    return DishInfo.fromJson(data);
  }

  Future<DishInfo> updateDish(int dishId, Map<String, dynamic> body) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/shop/dishes/$dishId',
      body: body,
    );
    return DishInfo.fromJson(data);
  }

  Future<DishInfo> updateDishStatus(int dishId, String status) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/shop/dishes/$dishId/status',
      body: {'status': status},
    );
    return DishInfo.fromJson(data);
  }

  Future<void> deleteDish(int dishId) async {
    await _request<void>('DELETE', '/shop/dishes/$dishId');
  }

  Future<void> updateShowcaseDishes(List<int> dishIds) async {
    await _request<void>('PUT', '/shop/dishes/showcase', body: dishIds);
  }

  Future<PageResult<OrderSummary>> fetchOrders({
    int page = 1,
    int pageSize = 100,
    String? status,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/shop/orders',
      query: {
        'page': '$page',
        'pageSize': '$pageSize',
        if (status != null && status.isNotEmpty) 'status': status,
      },
    );
    return PageResult.fromJson(data, OrderSummary.fromJson);
  }

  Future<OrderDetail> fetchOrderDetail(int orderId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/shop/orders/$orderId',
    );
    return OrderDetail.fromJson(data);
  }

  Future<void> acceptOrder(int orderId) async {
    await _request<void>('PUT', '/shop/orders/$orderId/accept');
  }

  Future<void> rejectOrder(int orderId) async {
    await _request<void>('PUT', '/shop/orders/$orderId/reject');
  }

  Future<void> completeOrder(int orderId) async {
    await _request<void>('PUT', '/shop/orders/$orderId/complete');
  }

  Future<ShopNotificationInfo> fetchNotifications() async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/shop/notifications',
    );
    return ShopNotificationInfo.fromJson(data);
  }

  Future<PageResult<FeedbackInfo>> fetchFeedback({
    int page = 1,
    int pageSize = 100,
    String? status,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/shop/feedback',
      query: {
        'page': '$page',
        'pageSize': '$pageSize',
        if (status != null && status.isNotEmpty) 'status': status,
      },
    );
    return PageResult.fromJson(data, FeedbackInfo.fromJson);
  }

  Future<FeedbackInfo> replyFeedback({
    required int feedbackId,
    required String reply,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/shop/feedback/$feedbackId/reply',
      body: {'reply': reply},
    );
    return FeedbackInfo.fromJson(data);
  }

  Future<String> uploadShopImage(String filePath) {
    return _upload('/upload/shop', filePath);
  }

  Future<String> uploadFamilyImage(String filePath) {
    return _upload('/upload/family', filePath);
  }

  Future<List<FamilyMemberInfo>> fetchFamilyMembers({String? status}) async {
    final data = await _request<List<dynamic>>(
      'GET',
      '/shop/family/members',
      query: status == null ? null : {'status': status},
    );
    return data
        .whereType<Map<String, dynamic>>()
        .map(FamilyMemberInfo.fromJson)
        .toList();
  }

  Future<void> approveFamilyMember(int memberId) async {
    await _request<void>('PUT', '/shop/family/members/$memberId/approve');
  }

  Future<void> rejectFamilyMember(int memberId) async {
    await _request<void>('DELETE', '/shop/family/members/$memberId');
  }

  Future<String> uploadMenuImage({
    required int shopId,
    required String filePath,
  }) {
    return _upload('/upload/menu', filePath, fields: {'shopId': '$shopId'});
  }

  Future<String> _upload(
    String path,
    String filePath, {
    Map<String, String>? fields,
    String fileField = 'file',
  }) async {
    final request = http.MultipartRequest('POST', _uri(path));
    request.headers.addAll(await _headers(jsonContent: false));
    request.fields.addAll(fields ?? const {});
    request.files.add(await http.MultipartFile.fromPath(fileField, filePath));
    final streamed = await _client.send(request);
    final response = await http.Response.fromStream(streamed);
    final data = _decode(response);
    if (data is Map<String, dynamic>) {
      final body = data['data'];
      if (body is Map<String, dynamic>) {
        return body['url']?.toString() ?? '';
      }
    }
    throw const ApiException('上传失败');
  }

  Future<T> _request<T>(
    String method,
    String path, {
    Map<String, String>? query,
    Object? body,
    bool authenticated = true,
  }) async {
    if (authenticated && (_token == null || _token!.isEmpty)) {
      await loadSavedToken();
    }

    final uri = _uri(path, query);
    final headers = await _headers(authenticated: authenticated);
    late final http.Response response;
    try {
      switch (method) {
        case 'GET':
          response = await _client.get(uri, headers: headers);
        case 'POST':
          response = await _client.post(
            uri,
            headers: headers,
            body: body == null ? null : jsonEncode(body),
          );
        case 'PUT':
          response = await _client.put(
            uri,
            headers: headers,
            body: body == null ? null : jsonEncode(body),
          );
        case 'DELETE':
          response = await _client.delete(
            uri,
            headers: headers,
            body: body == null ? null : jsonEncode(body),
          );
        default:
          throw ApiException('不支持的请求方法 $method');
      }
    } on SocketException {
      throw const ApiException('网络连接失败，请检查网络后重试');
    } on TimeoutException {
      throw const ApiException('请求超时，请稍后重试');
    } on http.ClientException {
      throw const ApiException('网络连接失败，请检查网络后重试');
    }

    final decoded = _decode(response);
    if (decoded == null) {
      return null as T;
    }
    if (decoded is Map<String, dynamic>) {
      final code = decoded['code'];
      if (code == 0) {
        return decoded['data'] as T;
      }
      throw ApiException(
        decoded['message']?.toString() ?? '请求失败',
        statusCode: response.statusCode,
      );
    }
    throw const ApiException('服务返回格式异常');
  }

  Object? _decode(http.Response response) {
    Object? decoded;
    if (response.body.isNotEmpty) {
      decoded = jsonDecode(utf8.decode(response.bodyBytes));
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      if (decoded is Map<String, dynamic>) {
        throw ApiException(
          decoded['message']?.toString() ?? '请求失败',
          statusCode: response.statusCode,
        );
      }
      throw ApiException('请求失败 (${response.statusCode})');
    }
    return decoded;
  }

  Future<Map<String, String>> _headers({
    bool authenticated = true,
    bool jsonContent = true,
  }) async {
    final headers = <String, String>{};
    if (jsonContent) {
      headers['Content-Type'] = 'application/json';
    }
    headers['Accept'] = 'application/json';
    if (authenticated) {
      final saved = _token ?? await AuthSession.loadToken();
      if (saved != null && saved.isNotEmpty) {
        headers['Authorization'] = 'Bearer $saved';
      }
    }
    return headers;
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    final normalizedPath = path.startsWith('/') ? path.substring(1) : path;
    final basePath = _baseUrl.path.endsWith('/')
        ? _baseUrl.path
        : '${_baseUrl.path}/';
    return _baseUrl.replace(
      path: '$basePath$normalizedPath',
      queryParameters: query,
    );
  }

  Future<MomentPage<MomentItem>> fetchMoments({
    String feed = 'recommended',
    int page = 0,
    int size = 10,
    String? query,
    int? topicId,
    int? userId,
    bool consumeExposure = true,
  }) async {
    final data = await _request<Object?>(
      'GET',
      '/moments',
      query: {
        'feed': feed.toUpperCase(),
        'page': '${page + 1}',
        'pageSize': '$size',
        if (query?.trim().isNotEmpty == true) 'keyword': query!.trim(),
        if (topicId != null) 'topicId': '$topicId',
        if (userId != null) 'authorId': '$userId',
        'consumeExposure': '$consumeExposure',
      },
    );
    return MomentPage.fromJson(data, MomentItem.fromJson);
  }

  Future<MomentItem> fetchMoment(int momentId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/moments/$momentId',
    );
    return MomentItem.fromJson(data);
  }

  Future<MomentItem> saveMoment({
    int? momentId,
    required String title,
    required String content,
    required String visibility,
    List<String> topics = const [],
    List<String> imageUrls = const [],
    int? dishId,
    bool draft = false,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      momentId == null ? 'POST' : 'PUT',
      momentId == null ? '/moments' : '/moments/$momentId',
      body: {
        'title': title.trim(),
        'content': content.trim(),
        'visibility': visibility,
        'topics': topics.take(5).toList(),
        'imageUrls': imageUrls,
        'dishId': dishId,
        'status': draft ? 'DRAFT' : 'PUBLISHED',
      },
    );
    return MomentItem.fromJson(data);
  }

  Future<void> deleteMoment(int momentId) =>
      _request<void>('DELETE', '/moments/$momentId');

  Future<String> uploadMomentImage(String filePath, {bool comment = false}) =>
      _upload(
        comment ? '/moments/comment-images' : '/moments/images',
        filePath,
        fileField: 'image',
      );

  Future<bool> toggleMomentLike(int momentId, bool liked) async {
    await _request<void>(liked ? 'POST' : 'DELETE', '/moments/$momentId/likes');
    return liked;
  }

  Future<void> toggleMomentReaction(
    int momentId,
    String emoji,
    bool selected,
  ) => _request<void>(
    'PUT',
    '/moments/$momentId/reactions',
    body: {'emoji': emoji, 'active': selected},
  );

  Future<MomentPage<MomentComment>> fetchMomentComments(
    int momentId, {
    int page = 0,
    int size = 10,
  }) async {
    final data = await _request<Object?>(
      'GET',
      '/moments/$momentId/comments',
      query: {'page': '${page + 1}', 'pageSize': '$size'},
    );
    return MomentPage.fromJson(data, MomentComment.fromJson);
  }

  Future<MomentPage<MomentComment>> fetchCommentReplies(
    int commentId, {
    int page = 0,
    int size = 10,
  }) async {
    final data = await _request<Object?>(
      'GET',
      '/moments/comments/$commentId/replies',
      query: {'page': '${page + 1}', 'pageSize': '$size'},
    );
    return MomentPage.fromJson(data, MomentComment.fromJson);
  }

  Future<MomentComment> addMomentComment(
    int momentId,
    String content, {
    int? parentId,
    int? replyToCommentId,
    String? imageUrl,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/moments/$momentId/comments',
      body: {
        'content': content.trim(),
        'parentCommentId': parentId,
        'replyToCommentId': replyToCommentId,
        'imageUrls': imageUrl == null ? const <String>[] : [imageUrl],
      },
    );
    return MomentComment.fromJson(data);
  }

  Future<void> deleteMomentComment(int commentId) =>
      _request<void>('DELETE', '/moments/comments/$commentId');

  Future<void> toggleCommentLike(int commentId, bool liked) => _request<void>(
    'PUT',
    '/moments/comments/$commentId/like',
    body: {'active': liked},
  );

  Future<MomentPage<MomentTopic>> fetchMomentTopics({
    String keyword = '',
    int page = 0,
    int size = 10,
  }) async {
    final data = await _request<Object?>(
      'GET',
      '/moments/topics',
      query: {
        'page': '${page + 1}',
        'pageSize': '$size',
        if (keyword.trim().isNotEmpty) 'query': keyword.trim(),
      },
    );
    return MomentPage.fromJson(data, MomentTopic.fromJson);
  }

  Future<MomentTopic> createMomentTopic(String name) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/moments/topics',
      body: {'name': name.trim()},
    );
    return MomentTopic.fromJson(data);
  }

  Future<MomentPrivacy> fetchMomentPrivacy(int userId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/moments/users/$userId/privacy',
    );
    return MomentPrivacy.fromJson(data);
  }

  Future<MomentProfileSummary> fetchMomentProfileSummary(int userId) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/moments/users/$userId/summary',
    );
    return MomentProfileSummary.fromJson(data);
  }

  Future<MomentPrivacy> updateMomentPrivacy(
    int userId, {
    required bool hideTheirMoments,
    required bool hideMineFromThem,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'PUT',
      '/moments/users/$userId/privacy',
      body: {
        'hideTargetMoments': hideTheirMoments,
        'hideMyMoments': hideMineFromThem,
      },
    );
    return MomentPrivacy.fromJson(data);
  }

  Future<String> fetchMomentProfileVisibility() async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/moments/privacy/profile',
    );
    return data['visibility']?.toString() ?? 'PUBLIC';
  }

  Future<void> updateMomentProfileVisibility(String visibility) =>
      _request<void>(
        'PUT',
        '/moments/privacy/profile',
        body: {'visibility': visibility},
      );

  Future<List<MomentItem>> fetchMomentDrafts() async {
    final data = await _request<Object?>('GET', '/moments/drafts');
    return MomentPage.fromJson(data, MomentItem.fromJson).items;
  }

  Future<void> shareMomentToChat({
    required int conversationId,
    required int momentId,
    int? commentId,
  }) => _request<void>(
    'POST',
    '/moments/$momentId/share',
    body: {'conversationId': conversationId, 'commentId': commentId},
  );

  Future<int> fetchRelatedMomentUnreadCount() async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/moments/unread-count',
    );
    final value = data['relatedUnreadCount'];
    return value is num ? value.toInt() : int.tryParse('$value') ?? 0;
  }

  Future<void> markMomentRead(int momentId) => markMomentsRead([momentId]);

  Future<void> markMomentsRead(Iterable<int> momentIds) => _request<void>(
    'PUT',
    '/moments/read',
    body: {'momentIds': momentIds.toSet().toList()},
  );
}

final shopApi = ShopApiService();
