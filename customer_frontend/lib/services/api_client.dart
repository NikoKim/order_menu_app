import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../models.dart';

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class ApiClient {
  ApiClient({
    http.Client? httpClient,
    String? baseUrl,
    this.requestTimeout = const Duration(seconds: 15),
  }) : _httpClient = httpClient ?? http.Client(),
       baseUrl = baseUrl ?? defaultBaseUrl;

  static const defaultBaseUrl = String.fromEnvironment(
    'CUSTOMER_API_BASE_URL',
    defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
  );

  final http.Client _httpClient;
  final String baseUrl;
  final Duration requestTimeout;
  String? token;

  Future<T> get<T>(
    String path,
    T Function(Object? json) decode, {
    Map<String, String?> query = const {},
  }) {
    return _request('GET', path, decode, query: query);
  }

  Future<T> post<T>(
    String path,
    T Function(Object? json) decode, {
    Object? body,
  }) {
    return _request('POST', path, decode, body: body);
  }

  Future<T> put<T>(
    String path,
    T Function(Object? json) decode, {
    Object? body,
  }) {
    return _request('PUT', path, decode, body: body);
  }

  Future<T> delete<T>(
    String path,
    T Function(Object? json) decode, {
    Map<String, String?> query = const {},
  }) {
    return _request('DELETE', path, decode, query: query);
  }

  Future<T> upload<T>(
    String path,
    T Function(Object? json) decode, {
    required String field,
    required String filePath,
    Map<String, String> fields = const {},
  }) async {
    final uri = Uri.parse('$baseUrl$path');
    final request = http.MultipartRequest('POST', uri)
      ..headers.addAll({
        'Accept': 'application/json',
        if (token != null && token!.isNotEmpty)
          'Authorization': 'Bearer $token',
      })
      ..fields.addAll(fields)
      ..files.add(await http.MultipartFile.fromPath(field, filePath));
    final streamed = await _httpClient.send(request).timeout(requestTimeout);
    final response = await http.Response.fromStream(
      streamed,
    ).timeout(requestTimeout);
    final decoded = response.body.isEmpty ? null : jsonDecode(response.body);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException('Request failed', statusCode: response.statusCode);
    }
    if (decoded is Map<String, dynamic>) {
      final code = decoded['code'];
      if (code != null && code != 0) {
        throw ApiException(decoded['message']?.toString() ?? 'Request failed');
      }
      return decode(decoded['data']);
    }
    return decode(decoded);
  }

  Future<T> _request<T>(
    String method,
    String path,
    T Function(Object? json) decode, {
    Object? body,
    Map<String, String?> query = const {},
  }) async {
    final uri = Uri.parse('$baseUrl$path').replace(
      queryParameters: {
        for (final entry in query.entries)
          if (entry.value != null && entry.value!.isNotEmpty)
            entry.key: entry.value!,
      },
    );
    final headers = <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      if (token != null && token!.isNotEmpty) 'Authorization': 'Bearer $token',
    };

    final response = switch (method) {
      'GET' =>
        await _httpClient.get(uri, headers: headers).timeout(requestTimeout),
      'POST' =>
        await _httpClient
            .post(
              uri,
              headers: headers,
              body: body == null ? null : jsonEncode(body),
            )
            .timeout(requestTimeout),
      'PUT' =>
        await _httpClient
            .put(
              uri,
              headers: headers,
              body: body == null ? null : jsonEncode(body),
            )
            .timeout(requestTimeout),
      'DELETE' =>
        await _httpClient.delete(uri, headers: headers).timeout(requestTimeout),
      _ => throw ApiException('不支持的请求方法：$method'),
    };

    Object? decoded;
    if (response.body.isNotEmpty) {
      decoded = jsonDecode(utf8.decode(response.bodyBytes));
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = decoded is Map<String, dynamic>
          ? decoded['message']?.toString()
          : null;
      throw ApiException(
        message ?? '网络请求失败（${response.statusCode}）',
        statusCode: response.statusCode,
      );
    }
    if (decoded is! Map<String, dynamic>) {
      return decode(decoded);
    }
    final code = decoded['code'];
    if (code != 0) {
      throw ApiException(decoded['message']?.toString() ?? '请求失败');
    }
    return decode(decoded['data']);
  }
}

String resolveCustomerImageUrl(String url) {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  final api = Uri.parse(ApiClient.defaultBaseUrl);
  return api.replace(path: url.startsWith('/') ? url : '/$url').toString();
}

class AppServices {
  AppServices._(this.api, this.auth);

  factory AppServices({ApiClient? apiClient}) {
    final api = apiClient ?? ApiClient();
    return AppServices._(api, AuthController(api));
  }

  factory AppServices.shared() {
    final api = ApiClient();
    return AppServices._(api, AuthController(api));
  }

  final ApiClient api;
  final AuthController auth;

  Future<void> init() => auth.restoreSession();
}

class AuthController extends ChangeNotifier {
  AuthController(this.api);

  static const _tokenKey = 'customer_token';
  static const _userKey = 'customer_user';

  final ApiClient api;
  CustomerUser? user;
  bool isReady = false;
  bool isBusy = false;
  Future<void> Function()? beforeLogout;

  bool get isLoggedIn => user != null && api.token != null;

  Future<void> restoreSession() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedToken = prefs.getString(_tokenKey);
      final savedUser = prefs.getString(_userKey);
      if (savedToken != null && savedUser != null) {
        api.token = savedToken;
        user = CustomerUser.fromJson(jsonDecode(savedUser));
        await refreshMe();
      }
    } catch (_) {
      api.token = null;
      user = null;
    } finally {
      isReady = true;
      notifyListeners();
    }
  }

  Future<void> login(String nickname, String password) async {
    await _runAuth(() async {
      final result = await api.post<Map<String, dynamic>>(
        '/auth/login',
        (json) => json as Map<String, dynamic>,
        body: {'nickname': nickname, 'password': password, 'role': 'CUSTOMER'},
      );
      await _saveSession(
        result['token']?.toString() ?? '',
        CustomerUser.fromJson(result['user'] as Map<String, dynamic>),
      );
    });
  }

  Future<void> register(String nickname, String password) async {
    await _runAuth(() async {
      await api.post<Object?>(
        '/auth/customer/register',
        (json) => json,
        body: {'nickname': nickname, 'password': password},
      );
      await login(nickname, password);
    });
  }

  Future<void> refreshMe() async {
    if (api.token == null) return;
    user = await api.get<CustomerUser>(
      '/users/me',
      (json) => CustomerUser.fromJson(json as Map<String, dynamic>),
    );
    await _persistUser();
    notifyListeners();
  }

  Future<CustomerUser> updateMe({
    String? nickname,
    String? phone,
    String? avatarUrl,
    bool? allowPrivateChat,
    bool? autoJoinFamilyChat,
    bool? showJoinedFamilies,
    bool? showPhone,
  }) async {
    final updated = await api.put<CustomerUser>(
      '/users/me',
      (json) => CustomerUser.fromJson(json as Map<String, dynamic>),
      body: {
        'nickname': nickname,
        'phone': phone,
        'avatarUrl': avatarUrl,
        'allowPrivateChat': allowPrivateChat,
        'autoJoinFamilyChat': autoJoinFamilyChat,
        'showJoinedFamilies': showJoinedFamilies,
        'showPhone': showPhone,
      },
    );
    user = updated;
    await _persistUser();
    notifyListeners();
    return updated;
  }

  Future<CustomerUser> updateChatSettings({
    required bool allowPrivateChat,
    required bool autoJoinFamilyChat,
  }) {
    final current = user;
    return updateMe(
      nickname: current?.nickname,
      phone: current?.phone,
      avatarUrl: current?.avatarUrl,
      allowPrivateChat: allowPrivateChat,
      autoJoinFamilyChat: autoJoinFamilyChat,
    );
  }

  Future<CustomerUser> updatePrivacySettings({
    required bool allowPrivateChat,
    required bool autoJoinFamilyChat,
    required bool showJoinedFamilies,
    required bool showPhone,
  }) => updateMe(
    allowPrivateChat: allowPrivateChat,
    autoJoinFamilyChat: autoJoinFamilyChat,
    showJoinedFamilies: showJoinedFamilies,
    showPhone: showPhone,
  );

  Future<String> uploadAvatar(String filePath) async {
    final avatarUrl = await api.upload<String>(
      '/upload/avatar',
      (json) => (json as Map<String, dynamic>)['url'].toString(),
      field: 'file',
      filePath: filePath,
    );
    final current = user;
    if (current != null) {
      await updateMe(
        nickname: current.nickname,
        phone: current.phone,
        avatarUrl: avatarUrl,
      );
    }
    return avatarUrl;
  }

  Future<void> logout() async {
    try {
      await beforeLogout?.call();
    } catch (_) {}
    api.token = null;
    user = null;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_tokenKey);
      await prefs.remove(_userKey);
    } catch (_) {}
    notifyListeners();
  }

  Future<void> _runAuth(Future<void> Function() action) async {
    isBusy = true;
    notifyListeners();
    try {
      await action();
    } finally {
      isBusy = false;
      notifyListeners();
    }
  }

  Future<void> _saveSession(String token, CustomerUser nextUser) async {
    api.token = token;
    user = nextUser;
    await _persistUser();
    notifyListeners();
  }

  Future<void> _persistUser() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (api.token != null) {
        await prefs.setString(_tokenKey, api.token!);
      }
      if (user != null) {
        await prefs.setString(
          _userKey,
          jsonEncode({
            'id': user!.id,
            'nickname': user!.nickname,
            'role': user!.role,
            'avatarUrl': user!.avatarUrl,
            'phone': user!.phone,
            'allowPrivateChat': user!.allowPrivateChat,
            'autoJoinFamilyChat': user!.autoJoinFamilyChat,
            'showJoinedFamilies': user!.showJoinedFamilies,
            'showPhone': user!.showPhone,
          }),
        );
      }
    } catch (error) {
      debugPrint('保存登录状态失败：$error');
    }
  }
}

class CustomerApi {
  CustomerApi(this.api);

  final ApiClient api;

  Future<Map<String, dynamic>> checkUpdate(
    String appCode,
    int currentVersionCode,
  ) async {
    try {
      final data = await api.get<Map<String, dynamic>>(
        '/version/check',
        (json) => json as Map<String, dynamic>,
        query: {
          'appCode': appCode,
          'currentVersionCode': currentVersionCode.toString(),
        },
      );
      return data;
    } catch (e) {
      // 忽略网络异常，防止断网时影响APP正常打开
      return {'hasUpdate': false};
    }
  }

  Future<PageResult<Shop>> shops({String? keyword, String? familyCode}) {
    return api.get(
      '/shops',
      (json) => _page(json, Shop.fromJson),
      query: {
        'page': '1',
        'pageSize': '50',
        'keyword': keyword,
        'familyCode': familyCode,
      },
    );
  }

  Future<ShopMenu> menu(int shopId) {
    return api.get(
      '/shops/$shopId/menu',
      (json) => ShopMenu.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<Map<String, dynamic>> createOrder({
    required String clientRequestId,
    required int shopId,
    required List<CartItem> items,
    String? remark,
  }) {
    return api.post(
      '/orders',
      (json) => json as Map<String, dynamic>,
      body: {
        'clientRequestId': clientRequestId,
        'shopId': shopId,
        'remark': remark,
        'items': [
          for (final item in items)
            {
              'dishId': item.dish.id,
              'quantity': item.quantity,
              if (item.selectedOptionIds.isNotEmpty)
                'optionItemIds': item.selectedOptionIds,
            },
        ],
      },
    );
  }

  Future<List<FamilyInfo>> myFamilies() {
    return api.get('/families/my', (json) {
      final map = json as Map<String, dynamic>;
      final families = map['families'];
      return families is List
          ? families
                .whereType<Map<String, dynamic>>()
                .map(FamilyInfo.fromJson)
                .toList()
          : const <FamilyInfo>[];
    });
  }

  Future<FamilyInfo> joinFamily(String familyCode) {
    return api.post(
      '/families/join',
      (json) => FamilyInfo.fromJson(json as Map<String, dynamic>),
      body: {'familyCode': familyCode.trim().toUpperCase()},
    );
  }

  Future<void> leaveFamily(int shopId) {
    return api.delete('/families/$shopId', (_) {});
  }

  Future<PageResult<OrderSummary>> orders() {
    return api.get(
      '/orders/my',
      (json) => _page(json, OrderSummary.fromJson),
      query: {'page': '1', 'pageSize': '50'},
    );
  }

  Future<OrderDetail> orderDetail(int orderId) {
    return api.get(
      '/orders/$orderId',
      (json) => OrderDetail.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<void> cancelOrder(int orderId) {
    return api.put('/orders/$orderId/cancel', (_) {});
  }

  Future<FeedbackEntry> submitFeedback({int? shopId, required String content}) {
    return api.post(
      '/feedback',
      (json) => FeedbackEntry.fromJson(json as Map<String, dynamic>),
      body: {'shopId': shopId, 'content': content},
    );
  }

  Future<List<ChatConversation>> conversations() {
    return api.get('/messages/conversations', (json) {
      final items = json as List? ?? const [];
      return items
          .whereType<Map<String, dynamic>>()
          .map(ChatConversation.fromJson)
          .toList();
    });
  }

  Future<NotificationList> notifications() => api.get(
    '/messages/notifications',
    (json) => NotificationList.fromJson(json as Map<String, dynamic>),
  );

  Future<int> unreadNotificationCount() =>
      api.get('/messages/notifications/unread-count', (json) => _asInt(json));

  Future<void> markNotificationRead(int notificationId) =>
      api.put('/messages/notifications/$notificationId/read', (_) {});

  Future<void> markAllNotificationsRead() =>
      api.put('/messages/notifications/read-all', (_) {});

  Future<ChatConversation> startChatWithShop(int shopId) => api.post(
    '/messages/direct/shops/$shopId',
    (json) => ChatConversation.fromJson(json as Map<String, dynamic>),
  );

  Future<ChatConversation> startChatWithCustomer(int userId) => api.post(
    '/messages/direct/users/$userId',
    (json) => ChatConversation.fromJson(json as Map<String, dynamic>),
  );

  Future<List<ChatUser>> searchChatCustomers(String nickname) {
    return api.get('/messages/users', (json) {
      final items = json as List? ?? const [];
      return items
          .whereType<Map<String, dynamic>>()
          .map(ChatUser.fromJson)
          .toList();
    }, query: {'nickname': nickname.trim()});
  }

  Future<ChatBlock> chatBlock(int userId) => api.get(
    '/messages/blocks/users/$userId',
    (json) => ChatBlock.fromJson(json as Map<String, dynamic>),
  );

  Future<void> blockChatUser(int userId) =>
      api.post('/messages/blocks/users/$userId', (_) {});

  Future<void> unblockChatUser(int userId) =>
      api.delete('/messages/blocks/users/$userId', (_) {});

  Future<ChatMessages> chatMessages(
    int conversationId, {
    int? beforeId,
    int limit = 50,
  }) => api.get(
    '/messages/conversations/$conversationId/messages',
    (json) => ChatMessages.fromJson(json as Map<String, dynamic>),
    query: {'beforeId': beforeId?.toString(), 'limit': limit.toString()},
  );

  Future<void> markConversationRead(
    int conversationId,
    int lastReadMessageId,
  ) => api.put(
    '/messages/conversations/$conversationId/read',
    (_) {},
    body: {'lastReadMessageId': lastReadMessageId},
  );

  Future<void> heartbeatPresence() =>
      api.put('/messages/presence/online', (_) {});

  Future<void> setConversationTyping(int conversationId, bool active) =>
      api.put(
        '/messages/conversations/$conversationId/typing',
        (_) {},
        body: {'active': active},
      );

  Future<Set<int>> typingUsers(int conversationId) =>
      api.get('/messages/conversations/$conversationId/typing', (json) {
        final data = json as Map<String, dynamic>? ?? const {};
        return (data['userIds'] as List? ?? const [])
            .map((value) => int.tryParse('$value'))
            .whereType<int>()
            .toSet();
      });

  Future<ChatUserProfile> chatUserProfile(int userId, {int? conversationId}) =>
      api.get(
        '/messages/users/$userId/profile',
        (json) => ChatUserProfile.fromJson(json as Map<String, dynamic>),
        query: {'conversationId': conversationId?.toString()},
      );

  Future<ConversationPreferences> conversationPreferences(int conversationId) =>
      api.get(
        '/messages/conversations/$conversationId/preferences',
        (json) =>
            ConversationPreferences.fromJson(json as Map<String, dynamic>),
      );

  Future<ConversationPreferences> updateConversationPreferences(
    int conversationId, {
    bool? muted,
    bool? hidden,
    bool? pinned,
  }) => api.put(
    '/messages/conversations/$conversationId/preferences',
    (json) => ConversationPreferences.fromJson(json as Map<String, dynamic>),
    body: <String, bool?>{'muted': muted, 'hidden': hidden, 'pinned': pinned}
      ..removeWhere((_, value) => value == null),
  );

  Future<ConversationHideResult> hideConversation(
    int conversationId, {
    required int expectedMessageVersion,
  }) => api.delete(
    '/messages/conversations/$conversationId',
    (json) => ConversationHideResult.fromJson(json as Map<String, dynamic>),
    query: {
      'guarded': 'true',
      'expectedMessageVersion': '$expectedMessageVersion',
    },
  );

  Future<void> setConversationMuted(int conversationId, bool muted) => api.put(
    '/messages/conversations/$conversationId/mute',
    (_) {},
    body: {'muted': muted},
  );

  Future<ChatMessage> sendChatImage({
    required int conversationId,
    required String filePath,
    String? content,
    int? quotedMessageId,
  }) => api.upload(
    '/messages/conversations/$conversationId/images',
    (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
    field: 'image',
    filePath: filePath,
    fields: {
      if (content != null && content.trim().isNotEmpty)
        'content': content.trim(),
      if (quotedMessageId != null)
        'quotedMessageId': quotedMessageId.toString(),
    },
  );

  Future<ChatMessage> shareShop({
    required int conversationId,
    required int shopId,
    int? quotedMessageId,
  }) => api.post(
    '/messages/shop-shares',
    (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
    body: {
      'conversationId': conversationId,
      'shopId': shopId,
      'quotedMessageId': quotedMessageId,
    },
  );

  Future<ChatEssenceList> chatEssences(int conversationId) => api.get(
    '/messages/family-groups/$conversationId/essences',
    (json) => ChatEssenceList.fromJson(json as Map<String, dynamic>),
  );

  Future<ChatMessage?> latestUnreadEssence(int conversationId) => api.get(
    '/messages/family-groups/$conversationId/essences/latest-unread',
    (json) {
      if (json is! Map<String, dynamic>) return null;
      return ChatMessage.fromJson(
        json['message'] is Map<String, dynamic>
            ? {
                ...(json['message'] as Map<String, dynamic>),
                'essence': true,
                'essencePinned': json['pinned'],
                'essenceLikeCount': json['likeCount'],
                'essenceLikedByMe': json['liked'],
              }
            : json,
      );
    },
  );

  Future<void> markEssencesRead(int conversationId, {int? throughMessageId}) =>
      api.put(
        '/messages/family-groups/$conversationId/essences/read',
        (_) {},
        body: {'throughMessageId': throughMessageId},
      );

  Future<void> setEssenceLiked(int conversationId, int messageId, bool liked) =>
      api.put(
        '/messages/family-groups/$conversationId/essences/$messageId/like',
        (_) {},
        body: {'liked': liked},
      );

  Future<void> registerPushDevice({
    required String token,
    required String platform,
  }) => api.post(
    '/messages/push-devices',
    (_) {},
    body: {'token': token, 'platform': platform},
  );

  Future<void> unregisterPushDevice(String token) => api.delete(
    '/messages/push-devices/${Uri.encodeComponent(token)}',
    (_) {},
  );

  Future<ChatMessage> sendChatMessage(
    int conversationId,
    String content, {
    int? quotedMessageId,
  }) => api.post(
    '/messages/conversations/$conversationId/messages',
    (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
    body: {'content': content.trim(), 'quotedMessageId': quotedMessageId},
  );

  Future<ChatMessage> recallChatMessage(int conversationId, int messageId) =>
      api.post(
        '/messages/conversations/$conversationId/messages/$messageId/recall',
        (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
      );

  Future<void> deleteChatMessage(int conversationId, int messageId) =>
      api.delete(
        '/messages/conversations/$conversationId/messages/$messageId',
        (_) {},
      );

  Future<FamilyGroup> familyGroup(int conversationId) => api.get(
    '/messages/family-groups/$conversationId',
    (json) => FamilyGroup.fromJson(json as Map<String, dynamic>),
  );

  Future<FamilyGroup> familyGroupByShop(int shopId) => api.get(
    '/messages/family-groups/by-shop/$shopId',
    (json) => FamilyGroup.fromJson(json as Map<String, dynamic>),
  );

  Future<void> joinFamilyGroup(int conversationId) =>
      api.post('/messages/family-groups/$conversationId/join', (_) {});

  Future<void> acceptFamilyGroupInvitation(int conversationId) => api.post(
    '/messages/family-groups/$conversationId/approve-membership',
    (_) {},
  );

  Future<void> declineFamilyGroupInvitation(int conversationId) => api.post(
    '/messages/family-groups/$conversationId/decline-membership',
    (_) {},
  );

  Future<void> leaveFamilyGroup(int conversationId) =>
      api.post('/messages/family-groups/$conversationId/leave', (_) {});

  Future<void> setFamilyGroupMuted(int conversationId, bool muted) => api.put(
    '/messages/family-groups/$conversationId/mute',
    (_) {},
    body: {'muted': muted},
  );

  Future<ChatMessage> shareDish({
    required int conversationId,
    required int dishId,
  }) => api.post(
    '/messages/dish-shares',
    (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
    body: {'conversationId': conversationId, 'dishId': dishId},
  );

  Future<ChatMessage> shareOrder({
    required int conversationId,
    required int orderId,
  }) => api.post(
    '/messages/order-shares',
    (json) => ChatMessage.fromJson(json as Map<String, dynamic>),
    body: {'conversationId': conversationId, 'orderId': orderId},
  );

  Future<PageResult<FeedbackEntry>> myFeedback() {
    return api.get(
      '/feedback/my',
      (json) => _page(json, FeedbackEntry.fromJson),
      query: {'page': '1', 'pageSize': '20'},
    );
  }

  PageResult<T> _page<T>(
    Object? json,
    T Function(Map<String, dynamic> json) decode,
  ) {
    final map = json as Map<String, dynamic>;
    final recordsJson = map['records'];
    return PageResult(
      records: recordsJson is List
          ? recordsJson.whereType<Map<String, dynamic>>().map(decode).toList()
          : const [],
      page: _asInt(map['page']),
      pageSize: _asInt(map['pageSize']),
      total: _asInt(map['total']),
    );
  }
}

int _asInt(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '') ?? 0;
}
