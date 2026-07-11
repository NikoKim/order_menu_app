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

  Future<T> delete<T>(String path, T Function(Object? json) decode) {
    return _request('DELETE', path, decode);
  }

  Future<T> upload<T>(
    String path,
    T Function(Object? json) decode, {
    required String field,
    required String filePath,
  }) async {
    final uri = Uri.parse('$baseUrl$path');
    final request = http.MultipartRequest('POST', uri)
      ..headers.addAll({
        'Accept': 'application/json',
        if (token != null && token!.isNotEmpty)
          'Authorization': 'Bearer $token',
      })
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
  }) async {
    final updated = await api.put<CustomerUser>(
      '/users/me',
      (json) => CustomerUser.fromJson(json as Map<String, dynamic>),
      body: {'nickname': nickname, 'phone': phone, 'avatarUrl': avatarUrl},
    );
    user = updated;
    await _persistUser();
    notifyListeners();
    return updated;
  }

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
    required int shopId,
    required List<CartItem> items,
    String? remark,
  }) {
    return api.post(
      '/orders',
      (json) => json as Map<String, dynamic>,
      body: {
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
