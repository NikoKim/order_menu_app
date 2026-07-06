import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/shop_api_models.dart';
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
  }) async {
    final request = http.MultipartRequest('POST', _uri(path));
    request.headers.addAll(await _headers(jsonContent: false));
    request.fields.addAll(fields ?? const {});
    request.files.add(await http.MultipartFile.fromPath('file', filePath));
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
    Map<String, dynamic>? body,
    bool authenticated = true,
  }) async {
    if (authenticated && (_token == null || _token!.isEmpty)) {
      await loadSavedToken();
    }

    final uri = _uri(path, query);
    final headers = await _headers(authenticated: authenticated);
    late final http.Response response;
    switch (method) {
      case 'GET':
        response = await _client.get(uri, headers: headers);
      case 'POST':
        response = await _client.post(
          uri,
          headers: headers,
          body: jsonEncode(body ?? const {}),
        );
      case 'PUT':
        response = await _client.put(
          uri,
          headers: headers,
          body: jsonEncode(body ?? const {}),
        );
      case 'DELETE':
        response = await _client.delete(uri, headers: headers);
      default:
        throw ApiException('不支持的请求方法 $method');
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
}

final shopApi = ShopApiService();
