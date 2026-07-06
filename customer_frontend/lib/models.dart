class PageResult<T> {
  const PageResult({
    required this.records,
    required this.page,
    required this.pageSize,
    required this.total,
  });

  final List<T> records;
  final int page;
  final int pageSize;
  final int total;
}

class CustomerUser {
  const CustomerUser({
    required this.id,
    required this.nickname,
    required this.role,
    this.avatarUrl,
    this.phone,
  });

  factory CustomerUser.fromJson(Map<String, dynamic> json) {
    return CustomerUser(
      id: _asInt(json['id']),
      nickname: json['nickname']?.toString() ?? '',
      role: json['role']?.toString() ?? 'CUSTOMER',
      avatarUrl: json['avatarUrl']?.toString(),
      phone: json['phone']?.toString(),
    );
  }

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
}

class Shop {
  const Shop({
    required this.id,
    required this.name,
    this.description,
    this.coverImageUrl,
    this.address,
    this.phone,
    this.businessStatus,
    this.visibilityType,
    this.familyName,
    this.familyCode,
    this.familyJoinMode,
    this.familyImageUrl,
  });

  factory Shop.fromJson(Map<String, dynamic> json) {
    return Shop(
      id: _asInt(json['id']),
      name: json['name']?.toString() ?? '未命名商家',
      description: json['description']?.toString(),
      coverImageUrl: json['coverImageUrl']?.toString(),
      address: json['address']?.toString(),
      phone: json['phone']?.toString(),
      businessStatus: json['businessStatus']?.toString(),
      visibilityType: json['visibilityType']?.toString(),
      familyName: json['familyName']?.toString(),
      familyCode: json['familyCode']?.toString(),
      familyJoinMode: json['familyJoinMode']?.toString(),
      familyImageUrl: json['familyImageUrl']?.toString(),
    );
  }

  final int id;
  final String name;
  final String? description;
  final String? coverImageUrl;
  final String? address;
  final String? phone;
  final String? businessStatus;
  final String? visibilityType;
  final String? familyName;
  final String? familyCode;
  final String? familyJoinMode;
  final String? familyImageUrl;
}

class DishOptionItem {
  const DishOptionItem({
    required this.id,
    required this.name,
    required this.priceDelta,
    required this.sortOrder,
  });

  factory DishOptionItem.fromJson(Map<String, dynamic> json) {
    return DishOptionItem(
      id: _asInt(json['id']),
      name: json['name']?.toString() ?? '',
      priceDelta: _asDouble(json['priceDelta']),
      sortOrder: _asInt(json['sortOrder']),
    );
  }

  final int id;
  final String name;
  final double priceDelta;
  final int sortOrder;
}

class DishOptionGroup {
  const DishOptionGroup({
    required this.id,
    required this.name,
    required this.required,
    required this.selectionType,
    required this.minSelect,
    this.maxSelect,
    required this.sortOrder,
    required this.options,
  });

  factory DishOptionGroup.fromJson(Map<String, dynamic> json) {
    final optionsJson = json['options'];
    return DishOptionGroup(
      id: _asInt(json['id']),
      name: json['name']?.toString() ?? '',
      required: json['required'] == true,
      selectionType: json['selectionType']?.toString() ?? 'SINGLE',
      minSelect: _asInt(json['minSelect']),
      maxSelect: json['maxSelect'] == null ? null : _asInt(json['maxSelect']),
      sortOrder: _asInt(json['sortOrder']),
      options: optionsJson is List
          ? optionsJson
                .whereType<Map<String, dynamic>>()
                .map(DishOptionItem.fromJson)
                .toList()
          : const [],
    );
  }

  final int id;
  final String name;
  final bool required;
  final String selectionType;
  final int minSelect;
  final int? maxSelect;
  final int sortOrder;
  final List<DishOptionItem> options;

  bool get isMultiple => selectionType == 'MULTIPLE';
}

class Dish {
  const Dish({
    required this.id,
    required this.shopId,
    required this.name,
    required this.price,
    this.categoryId,
    this.description,
    this.imageUrl,
    this.stock,
    this.salesCount,
    this.status,
    this.optionGroups = const [],
  });

  factory Dish.fromJson(Map<String, dynamic> json) {
    final groupsJson = json['optionGroups'];
    return Dish(
      id: _asInt(json['id']),
      shopId: _asInt(json['shopId']),
      categoryId: json['categoryId'] == null
          ? null
          : _asInt(json['categoryId']),
      name: json['name']?.toString() ?? '未命名菜品',
      description: json['description']?.toString(),
      price: _asDouble(json['price']),
      imageUrl: json['imageUrl']?.toString(),
      stock: json['stock'] == null ? null : _asInt(json['stock']),
      salesCount: json['salesCount'] == null
          ? null
          : _asInt(json['salesCount']),
      status: json['status']?.toString(),
      optionGroups: groupsJson is List
          ? groupsJson
                .whereType<Map<String, dynamic>>()
                .map(DishOptionGroup.fromJson)
                .toList()
          : const [],
    );
  }

  final int id;
  final int shopId;
  final int? categoryId;
  final String name;
  final String? description;
  final double price;
  final String? imageUrl;
  final int? stock;
  final int? salesCount;
  final String? status;
  final List<DishOptionGroup> optionGroups;
}

class MenuCategory {
  const MenuCategory({
    required this.id,
    required this.name,
    required this.dishes,
  });

  factory MenuCategory.fromJson(Map<String, dynamic> json) {
    final dishesJson = json['dishes'];
    return MenuCategory(
      id: _asInt(json['id']),
      name: json['name']?.toString() ?? '默认分类',
      dishes: dishesJson is List
          ? dishesJson
                .whereType<Map<String, dynamic>>()
                .map(Dish.fromJson)
                .toList()
          : const [],
    );
  }

  final int id;
  final String name;
  final List<Dish> dishes;
}

class ShopMenu {
  const ShopMenu({required this.shop, required this.categories});

  factory ShopMenu.fromJson(Map<String, dynamic> json) {
    final categoriesJson = json['categories'];
    return ShopMenu(
      shop: Shop.fromJson(json['shop'] as Map<String, dynamic>),
      categories: categoriesJson is List
          ? categoriesJson
                .whereType<Map<String, dynamic>>()
                .map(MenuCategory.fromJson)
                .toList()
          : const [],
    );
  }

  final Shop shop;
  final List<MenuCategory> categories;
}

class CartItem {
  const CartItem({
    required this.dish,
    required this.quantity,
    this.selectedOptionIds = const [],
    this.selectedOptionText,
    this.optionAmount = 0,
  });

  final Dish dish;
  final int quantity;
  final List<int> selectedOptionIds;
  final String? selectedOptionText;
  final double optionAmount;

  String get key => cartKey(dish.id, selectedOptionIds);
  double get unitPrice => dish.price + optionAmount;
  double get subtotal => unitPrice * quantity;

  CartItem copyWith({int? quantity}) {
    return CartItem(
      dish: dish,
      quantity: quantity ?? this.quantity,
      selectedOptionIds: selectedOptionIds,
      selectedOptionText: selectedOptionText,
      optionAmount: optionAmount,
    );
  }
}

String cartKey(int dishId, List<int> optionIds) {
  final sorted = [...optionIds]..sort();
  return '$dishId:${sorted.join(",")}';
}

class FamilyInfo {
  const FamilyInfo({
    required this.shopId,
    required this.familyName,
    required this.familyCode,
    required this.memberStatus,
    this.shopName,
    this.familyImageUrl,
    this.joinMode,
  });

  factory FamilyInfo.fromJson(Map<String, dynamic> json) {
    return FamilyInfo(
      shopId: _asInt(json['shopId']),
      shopName: json['shopName']?.toString(),
      familyName: json['familyName']?.toString() ?? '家庭',
      familyCode: json['familyCode']?.toString() ?? '',
      familyImageUrl: json['familyImageUrl']?.toString(),
      joinMode: json['joinMode']?.toString(),
      memberStatus: json['memberStatus']?.toString() ?? '',
    );
  }

  final int shopId;
  final String? shopName;
  final String familyName;
  final String familyCode;
  final String? familyImageUrl;
  final String? joinMode;
  final String memberStatus;

  bool get isApproved => memberStatus == 'APPROVED';
}

class OrderSummary {
  const OrderSummary({
    required this.id,
    required this.orderNo,
    required this.shopId,
    required this.shopName,
    required this.status,
    required this.totalAmount,
    required this.createdAt,
  });

  factory OrderSummary.fromJson(Map<String, dynamic> json) {
    return OrderSummary(
      id: _asInt(json['id']),
      orderNo: json['orderNo']?.toString() ?? '',
      shopId: _asInt(json['shopId']),
      shopName: json['shopName']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      totalAmount: _asDouble(json['totalAmount']),
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }

  final int id;
  final String orderNo;
  final int shopId;
  final String shopName;
  final String status;
  final double totalAmount;
  final String createdAt;
}

class OrderDetail extends OrderSummary {
  const OrderDetail({
    required super.id,
    required super.orderNo,
    required super.shopId,
    required super.shopName,
    required super.status,
    required super.totalAmount,
    required super.createdAt,
    required this.remark,
    required this.items,
  });

  factory OrderDetail.fromJson(Map<String, dynamic> json) {
    final itemsJson = json['items'];
    return OrderDetail(
      id: _asInt(json['id']),
      orderNo: json['orderNo']?.toString() ?? '',
      shopId: _asInt(json['shopId']),
      shopName: json['shopName']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      totalAmount: _asDouble(json['totalAmount']),
      createdAt: json['createdAt']?.toString() ?? '',
      remark: json['remark']?.toString() ?? '',
      items: itemsJson is List
          ? itemsJson
                .whereType<Map<String, dynamic>>()
                .map(OrderItem.fromJson)
                .toList()
          : const [],
    );
  }

  final String remark;
  final List<OrderItem> items;
}

class OrderItem {
  const OrderItem({
    required this.dishId,
    required this.dishName,
    required this.unitPrice,
    required this.quantity,
    required this.subtotalAmount,
    this.dishImageUrl,
    this.selectedOptions,
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    return OrderItem(
      dishId: _asInt(json['dishId']),
      dishName: json['dishName']?.toString() ?? '',
      dishImageUrl: json['dishImageUrl']?.toString(),
      unitPrice: _asDouble(json['unitPrice']),
      quantity: _asInt(json['quantity']),
      subtotalAmount: _asDouble(json['subtotalAmount']),
      selectedOptions: json['selectedOptions']?.toString(),
    );
  }

  final int dishId;
  final String dishName;
  final String? dishImageUrl;
  final double unitPrice;
  final int quantity;
  final double subtotalAmount;
  final String? selectedOptions;
}

class FeedbackEntry {
  const FeedbackEntry({
    required this.id,
    required this.content,
    required this.status,
    required this.createdAt,
    this.shopId,
    this.reply,
  });

  factory FeedbackEntry.fromJson(Map<String, dynamic> json) {
    return FeedbackEntry(
      id: _asInt(json['id']),
      shopId: json['shopId'] == null ? null : _asInt(json['shopId']),
      content: json['content']?.toString() ?? '',
      reply: json['reply']?.toString(),
      status: json['status']?.toString() ?? '',
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }

  final int id;
  final int? shopId;
  final String content;
  final String? reply;
  final String status;
  final String createdAt;
}

int _asInt(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '') ?? 0;
}

double _asDouble(Object? value) {
  if (value is double) return value;
  if (value is num) return value.toDouble();
  return double.tryParse(value?.toString() ?? '') ?? 0;
}
