class PageResult<T> {
  const PageResult({
    required this.records,
    required this.page,
    required this.pageSize,
    required this.total,
  });

  factory PageResult.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromJson,
  ) {
    final records = (json['records'] as List<dynamic>? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(fromJson)
        .toList();
    return PageResult(
      records: records,
      page: _asInt(json['page']),
      pageSize: _asInt(json['pageSize']),
      total: _asInt(json['total']),
    );
  }

  final List<T> records;
  final int page;
  final int pageSize;
  final int total;
}

class CurrentUser {
  const CurrentUser({
    required this.id,
    required this.nickname,
    required this.role,
    this.avatarUrl,
    this.phone,
  });

  factory CurrentUser.fromJson(Map<String, dynamic> json) {
    return CurrentUser(
      id: _asInt(json['id']),
      nickname: _asString(json['nickname']),
      role: _asString(json['role']),
      avatarUrl: json['avatarUrl'] as String?,
      phone: json['phone'] as String?,
    );
  }

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
}

class LoginResult {
  const LoginResult({required this.token, required this.user});

  factory LoginResult.fromJson(Map<String, dynamic> json) {
    return LoginResult(
      token: _asString(json['token']),
      user: CurrentUser.fromJson(json['user'] as Map<String, dynamic>? ?? {}),
    );
  }

  final String token;
  final CurrentUser user;
}

class ShopInfo {
  const ShopInfo({
    required this.id,
    required this.name,
    required this.businessStatus,
    this.description,
    this.coverImageUrl,
    this.address,
    this.phone,
    this.visibilityType,
    this.familyName,
    this.familyCode,
    this.familyJoinMode,
    this.familyImageUrl,
  });

  factory ShopInfo.fromJson(Map<String, dynamic> json) {
    return ShopInfo(
      id: _asInt(json['id']),
      name: _asString(json['name']),
      description: json['description'] as String?,
      coverImageUrl: json['coverImageUrl'] as String?,
      address: json['address'] as String?,
      phone: json['phone'] as String?,
      visibilityType: json['visibilityType'] as String?,
      familyName: json['familyName'] as String?,
      familyCode: json['familyCode'] as String?,
      familyJoinMode: json['familyJoinMode'] as String?,
      familyImageUrl: json['familyImageUrl'] as String?,
      businessStatus: _asString(json['businessStatus'], fallback: 'OPEN'),
    );
  }

  final int id;
  final String name;
  final String? description;
  final String? coverImageUrl;
  final String? address;
  final String? phone;
  final String? visibilityType;
  final String? familyName;
  final String? familyCode;
  final String? familyJoinMode;
  final String? familyImageUrl;
  final String businessStatus;

  bool get isOpen => businessStatus == 'OPEN';
}

class CategoryInfo {
  const CategoryInfo({
    required this.id,
    required this.name,
    required this.sortOrder,
    required this.status,
  });

  factory CategoryInfo.fromJson(Map<String, dynamic> json) {
    return CategoryInfo(
      id: _asInt(json['id']),
      name: _asString(json['name']),
      sortOrder: _asInt(json['sortOrder']),
      status: _asInt(json['status'], fallback: 1),
    );
  }

  final int id;
  final String name;
  final int sortOrder;
  final int status;
}

class DishInfo {
  const DishInfo({
    required this.id,
    required this.shopId,
    required this.categoryId,
    required this.name,
    required this.price,
    required this.stock,
    required this.salesCount,
    required this.status,
    required this.sortOrder,
    required this.recommendOrder,
    this.description,
    this.imageUrl,
    this.optionGroups = const [],
  });

  factory DishInfo.fromJson(Map<String, dynamic> json) {
    return DishInfo(
      id: _asInt(json['id']),
      shopId: _asInt(json['shopId']),
      categoryId: _asInt(json['categoryId']),
      name: _asString(json['name']),
      description: json['description'] as String?,
      price: _asDouble(json['price']),
      imageUrl: json['imageUrl'] as String?,
      optionGroups: (json['optionGroups'] as List<dynamic>? ?? [])
          .whereType<Map<String, dynamic>>()
          .map(DishOptionGroupInfo.fromJson)
          .toList(),
      stock: _asInt(json['stock']),
      salesCount: _asInt(json['salesCount']),
      status: _asString(json['status'], fallback: 'ON_SALE'),
      sortOrder: _asInt(json['sortOrder']),
      recommendOrder: _asInt(json['recommendOrder']),
    );
  }

  final int id;
  final int shopId;
  final int categoryId;
  final String name;
  final String? description;
  final double price;
  final String? imageUrl;
  final List<DishOptionGroupInfo> optionGroups;
  final int stock;
  final int salesCount;
  final String status;
  final int sortOrder;
  final int recommendOrder;

  bool get isOnSale => status == 'ON_SALE';
}

class DishOptionGroupInfo {
  const DishOptionGroupInfo({
    required this.name,
    required this.required,
    required this.selectionType,
    required this.minSelect,
    required this.sortOrder,
    required this.options,
    this.id,
    this.maxSelect,
  });

  factory DishOptionGroupInfo.fromJson(Map<String, dynamic> json) {
    return DishOptionGroupInfo(
      id: _asInt(json['id']),
      name: _asString(json['name']),
      required: json['required'] == true,
      selectionType: _asString(json['selectionType'], fallback: 'SINGLE'),
      minSelect: _asInt(json['minSelect']),
      maxSelect: json['maxSelect'] == null ? null : _asInt(json['maxSelect']),
      sortOrder: _asInt(json['sortOrder']),
      options: (json['options'] as List<dynamic>? ?? [])
          .whereType<Map<String, dynamic>>()
          .map(DishOptionItemInfo.fromJson)
          .toList(),
    );
  }

  final int? id;
  final String name;
  final bool required;
  final String selectionType;
  final int minSelect;
  final int? maxSelect;
  final int sortOrder;
  final List<DishOptionItemInfo> options;

  bool get isMultiple => selectionType == 'MULTIPLE';
}

class ShopNotificationInfo {
  const ShopNotificationInfo({
    required this.pendingOrders,
    required this.pendingFamilyMembers,
    required this.pendingFeedback,
  });

  factory ShopNotificationInfo.fromJson(Map<String, dynamic> json) {
    return ShopNotificationInfo(
      pendingOrders: _asInt(json['pendingOrders']),
      pendingFamilyMembers: _asInt(json['pendingFamilyMembers']),
      pendingFeedback: _asInt(json['pendingFeedback']),
    );
  }

  final int pendingOrders;
  final int pendingFamilyMembers;
  final int pendingFeedback;
}

class DishOptionItemInfo {
  const DishOptionItemInfo({
    required this.name,
    required this.priceDelta,
    required this.sortOrder,
    this.id,
  });

  factory DishOptionItemInfo.fromJson(Map<String, dynamic> json) {
    return DishOptionItemInfo(
      id: _asInt(json['id']),
      name: _asString(json['name']),
      priceDelta: _asDouble(json['priceDelta']),
      sortOrder: _asInt(json['sortOrder']),
    );
  }

  final int? id;
  final String name;
  final double priceDelta;
  final int sortOrder;
}

class OrderSummary {
  const OrderSummary({
    required this.id,
    required this.orderNo,
    required this.status,
    required this.totalAmount,
    required this.createdAt,
    this.shopName,
  });

  factory OrderSummary.fromJson(Map<String, dynamic> json) {
    return OrderSummary(
      id: _asInt(json['id']),
      orderNo: _asString(json['orderNo']),
      shopName: json['shopName'] as String?,
      status: _asString(json['status']),
      totalAmount: _asDouble(json['totalAmount']),
      createdAt: _asString(json['createdAt']),
    );
  }

  final int id;
  final String orderNo;
  final String? shopName;
  final String status;
  final double totalAmount;
  final String createdAt;
}

class OrderDetail {
  const OrderDetail({
    required this.id,
    required this.orderNo,
    required this.customerUserId,
    required this.status,
    required this.totalAmount,
    required this.createdAt,
    required this.items,
    this.customerNickname,
    this.customerPhone,
    this.customerAvatarUrl,
    this.shopName,
    this.remark,
    this.familyCustomer = false,
  });

  factory OrderDetail.fromJson(Map<String, dynamic> json) {
    final items = (json['items'] as List<dynamic>? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(OrderItemInfo.fromJson)
        .toList();
    return OrderDetail(
      id: _asInt(json['id']),
      orderNo: _asString(json['orderNo']),
      shopName: json['shopName'] as String?,
      customerUserId: _asInt(json['customerUserId']),
      customerNickname: json['customerNickname'] as String?,
      customerPhone: json['customerPhone'] as String?,
      customerAvatarUrl: json['customerAvatarUrl'] as String?,
      status: _asString(json['status']),
      totalAmount: _asDouble(json['totalAmount']),
      remark: json['remark'] as String?,
      familyCustomer: json['familyCustomer'] == true,
      createdAt: _asString(json['createdAt']),
      items: items,
    );
  }

  final int id;
  final String orderNo;
  final String? shopName;
  final int customerUserId;
  final String? customerNickname;
  final String? customerPhone;
  final String? customerAvatarUrl;
  final String status;
  final double totalAmount;
  final String? remark;
  final bool familyCustomer;
  final String createdAt;
  final List<OrderItemInfo> items;
}

class OrderItemInfo {
  const OrderItemInfo({
    required this.dishId,
    required this.dishName,
    required this.unitPrice,
    required this.quantity,
    required this.subtotalAmount,
    this.dishImageUrl,
    this.selectedOptions,
  });

  factory OrderItemInfo.fromJson(Map<String, dynamic> json) {
    return OrderItemInfo(
      dishId: _asInt(json['dishId']),
      dishName: _asString(json['dishName']),
      dishImageUrl: json['dishImageUrl'] as String?,
      unitPrice: _asDouble(json['unitPrice']),
      quantity: _asInt(json['quantity']),
      subtotalAmount: _asDouble(json['subtotalAmount']),
      selectedOptions: json['selectedOptions'] as String?,
    );
  }

  final int dishId;
  final String dishName;
  final String? dishImageUrl;
  final String? selectedOptions;
  final double unitPrice;
  final int quantity;
  final double subtotalAmount;
}

class FeedbackInfo {
  const FeedbackInfo({
    required this.id,
    required this.userId,
    required this.shopId,
    required this.content,
    required this.status,
    required this.createdAt,
    this.reply,
    this.userNickname,
    this.userAvatarUrl,
  });

  factory FeedbackInfo.fromJson(Map<String, dynamic> json) {
    return FeedbackInfo(
      id: _asInt(json['id']),
      userId: _asInt(json['userId']),
      shopId: _asInt(json['shopId']),
      content: _asString(json['content']),
      reply: json['reply'] as String?,
      status: _asString(json['status'], fallback: 'PENDING'),
      createdAt: _asString(json['createdAt']),
      userNickname: json['userNickname'] as String?,
      userAvatarUrl: json['userAvatarUrl'] as String?,
    );
  }

  final int id;
  final int userId;
  final int shopId;
  final String content;
  final String? reply;
  final String status;
  final String createdAt;
  final String? userNickname;
  final String? userAvatarUrl;
}

class FamilyMemberInfo {
  const FamilyMemberInfo({
    required this.id,
    required this.userId,
    required this.status,
    this.nickname,
    this.phone,
    this.avatarUrl,
  });

  factory FamilyMemberInfo.fromJson(Map<String, dynamic> json) {
    return FamilyMemberInfo(
      id: _asInt(json['id']),
      userId: _asInt(json['userId']),
      nickname: json['nickname'] as String?,
      phone: json['phone'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      status: _asString(json['status']),
    );
  }

  final int id;
  final int userId;
  final String? nickname;
  final String? phone;
  final String? avatarUrl;
  final String status;
}

int _asInt(Object? value, {int fallback = 0}) {
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  if (value is String) {
    return int.tryParse(value) ?? fallback;
  }
  return fallback;
}

double _asDouble(Object? value) {
  if (value is num) {
    return value.toDouble();
  }
  if (value is String) {
    return double.tryParse(value) ?? 0;
  }
  return 0;
}

String _asString(Object? value, {String fallback = ''}) {
  return value?.toString() ?? fallback;
}
