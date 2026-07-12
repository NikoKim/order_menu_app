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
    this.allowPrivateChat = true,
    this.autoJoinFamilyChat = true,
  });

  factory CustomerUser.fromJson(Map<String, dynamic> json) {
    return CustomerUser(
      id: _asInt(json['id']),
      nickname: json['nickname']?.toString() ?? '',
      role: json['role']?.toString() ?? 'CUSTOMER',
      avatarUrl: json['avatarUrl']?.toString(),
      phone: json['phone']?.toString(),
      allowPrivateChat: json['allowPrivateChat'] != false,
      autoJoinFamilyChat: json['autoJoinFamilyChat'] != false,
    );
  }

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
  final bool allowPrivateChat;
  final bool autoJoinFamilyChat;
}

class ChatConversation {
  const ChatConversation({
    required this.id,
    required this.type,
    required this.status,
    this.name,
    this.coverImageUrl,
    this.lastMessageAt,
    this.lastMessageContent,
    this.unreadCount = 0,
    this.muted = false,
    this.directUserId,
    this.directUserNickname,
    this.directUserAvatarUrl,
  });

  factory ChatConversation.fromJson(Map<String, dynamic> json) {
    return ChatConversation(
      id: _asInt(json['id']),
      type: json['type']?.toString() ?? 'DIRECT',
      status: json['status']?.toString() ?? 'ACTIVE',
      name: json['name']?.toString(),
      coverImageUrl: json['coverImageUrl']?.toString(),
      lastMessageAt: json['lastMessageAt']?.toString(),
      lastMessageContent: (json['lastMessageContent'] ?? json['lastMessage'])
          ?.toString(),
      unreadCount: _asInt(json['unreadCount']),
      muted: json['muted'] == true,
      directUserId: json['directUserId'] == null
          ? null
          : _asInt(json['directUserId']),
      directUserNickname: json['directUserNickname']?.toString(),
      directUserAvatarUrl: json['directUserAvatarUrl']?.toString(),
    );
  }

  final int id;
  final String type;
  final String status;
  final String? name;
  final String? coverImageUrl;
  final String? lastMessageAt;
  final String? lastMessageContent;
  final int unreadCount;
  final bool muted;
  final int? directUserId;
  final String? directUserNickname;
  final String? directUserAvatarUrl;

  bool get isDirect => type == 'DIRECT';
  bool get isActive => status == 'ACTIVE';
  String get title =>
      isDirect ? (directUserNickname ?? '私聊') : (name ?? '家庭群聊');
  String? get imageUrl => isDirect ? directUserAvatarUrl : coverImageUrl;
  bool get showUnreadBadge => unreadCount > 0 && !muted;

  ChatConversation markedRead() => ChatConversation(
    id: id,
    type: type,
    status: status,
    name: name,
    coverImageUrl: coverImageUrl,
    lastMessageAt: lastMessageAt,
    lastMessageContent: lastMessageContent,
    unreadCount: 0,
    muted: muted,
    directUserId: directUserId,
    directUserNickname: directUserNickname,
    directUserAvatarUrl: directUserAvatarUrl,
  );
}

class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.conversationId,
    required this.senderUserId,
    required this.type,
    required this.content,
    required this.createdAt,
    this.dishId,
    this.shopId,
    this.dishName,
    this.dishImageUrl,
    this.dishPrice,
    this.senderNickname,
    this.senderAvatarUrl,
    this.dishDescription,
    this.dishSales,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
    id: _asInt(json['id']),
    conversationId: _asInt(json['conversationId']),
    senderUserId: _asInt(json['senderUserId']),
    type: json['type']?.toString() ?? 'TEXT',
    content: json['content']?.toString() ?? '',
    createdAt: json['createdAt']?.toString() ?? '',
    dishId: json['dishId'] == null ? null : _asInt(json['dishId']),
    shopId: json['shopId'] == null ? null : _asInt(json['shopId']),
    dishName: json['dishName']?.toString(),
    dishImageUrl: json['dishImageUrl']?.toString(),
    dishPrice: json['dishPrice'] == null ? null : _asDouble(json['dishPrice']),
    senderNickname: json['senderNickname']?.toString(),
    senderAvatarUrl: json['senderAvatarUrl']?.toString(),
    dishDescription: json['dishDescription']?.toString(),
    dishSales: json['dishSalesCount'] == null
        ? (json['dishSales'] == null ? null : _asInt(json['dishSales']))
        : _asInt(json['dishSalesCount']),
  );

  final int id;
  final int conversationId;
  final int senderUserId;
  final String type;
  final String content;
  final String createdAt;
  final int? dishId;
  final int? shopId;
  final String? dishName;
  final String? dishImageUrl;
  final double? dishPrice;
  final String? senderNickname;
  final String? senderAvatarUrl;
  final String? dishDescription;
  final int? dishSales;

  bool get isDishShare => type == 'DISH_SHARE';
}

bool shouldShowChatTimestamp(DateTime? previousTimestamp, DateTime timestamp) {
  if (previousTimestamp == null) return true;
  return timestamp.difference(previousTimestamp).inSeconds >= 60;
}

String formatChatListTime(String? value, {DateTime? now}) {
  final parsed = value == null ? null : DateTime.tryParse(value);
  if (parsed == null) return '';
  final local = parsed.toLocal();
  final today = now ?? DateTime.now();
  String twoDigits(int number) => number.toString().padLeft(2, '0');
  if (local.year == today.year &&
      local.month == today.month &&
      local.day == today.day) {
    return '${twoDigits(local.hour)}:${twoDigits(local.minute)}';
  }
  final monthAndDay = '${twoDigits(local.month)}/${twoDigits(local.day)}';
  return local.year == today.year ? monthAndDay : '${local.year}/$monthAndDay';
}

class FamilyGroupMember {
  const FamilyGroupMember({
    required this.userId,
    required this.nickname,
    required this.role,
    required this.memberStatus,
    required this.muted,
    this.avatarUrl,
  });
  factory FamilyGroupMember.fromJson(Map<String, dynamic> json) =>
      FamilyGroupMember(
        userId: _asInt(json['userId']),
        nickname: json['nickname']?.toString() ?? '',
        avatarUrl: json['avatarUrl']?.toString(),
        role: json['role']?.toString() ?? 'MEMBER',
        memberStatus: json['memberStatus']?.toString() ?? 'ACTIVE',
        muted: json['muted'] == true,
      );
  final int userId;
  final String nickname;
  final String? avatarUrl;
  final String role;
  final String memberStatus;
  final bool muted;
}

class FamilyGroup {
  const FamilyGroup({
    required this.id,
    required this.shopId,
    required this.name,
    required this.status,
    required this.members,
    this.coverImageUrl,
    this.announcement,
  });
  factory FamilyGroup.fromJson(Map<String, dynamic> json) {
    final rawMembers = json['members'];
    return FamilyGroup(
      id: _asInt(json['id']),
      shopId: _asInt(json['shopId']),
      name: json['name']?.toString() ?? '家庭群聊',
      status: json['status']?.toString() ?? 'ACTIVE',
      coverImageUrl: json['coverImageUrl']?.toString(),
      announcement: json['announcement']?.toString(),
      members: rawMembers is List
          ? rawMembers
                .whereType<Map<String, dynamic>>()
                .map(FamilyGroupMember.fromJson)
                .toList()
          : const [],
    );
  }
  final int id;
  final int shopId;
  final String name;
  final String? coverImageUrl;
  final String? announcement;
  final String status;
  final List<FamilyGroupMember> members;
  bool get isActive => status == 'ACTIVE';
}

class ChatMessages {
  const ChatMessages({required this.conversation, required this.messages});

  factory ChatMessages.fromJson(Map<String, dynamic> json) {
    final rawMessages = json['messages'];
    return ChatMessages(
      conversation: ChatConversation.fromJson(
        json['conversation'] as Map<String, dynamic>,
      ),
      messages: rawMessages is List
          ? rawMessages
                .whereType<Map<String, dynamic>>()
                .map(ChatMessage.fromJson)
                .toList()
          : const [],
    );
  }

  final ChatConversation conversation;
  final List<ChatMessage> messages;
}

class ChatUser {
  const ChatUser({required this.id, required this.nickname, this.avatarUrl});

  factory ChatUser.fromJson(Map<String, dynamic> json) => ChatUser(
    id: _asInt(json['id']),
    nickname: json['nickname']?.toString() ?? '',
    avatarUrl: json['avatarUrl']?.toString(),
  );

  final int id;
  final String nickname;
  final String? avatarUrl;
}

class UserNotification {
  const UserNotification({
    required this.id,
    required this.userId,
    required this.type,
    required this.title,
    required this.content,
    required this.createdAt,
    this.payload,
    this.readAt,
  });

  factory UserNotification.fromJson(Map<String, dynamic> json) =>
      UserNotification(
        id: _asInt(json['id']),
        userId: _asInt(json['userId']),
        type: json['type']?.toString() ?? '',
        title: json['title']?.toString() ?? '',
        content: json['content']?.toString() ?? '',
        payload: json['payload']?.toString(),
        readAt: json['readAt']?.toString(),
        createdAt: json['createdAt']?.toString() ?? '',
      );

  final int id;
  final int userId;
  final String type;
  final String title;
  final String content;
  final String? payload;
  final String? readAt;
  final String createdAt;

  bool get isUnread => readAt == null || readAt!.isEmpty;

  UserNotification markRead() => UserNotification(
    id: id,
    userId: userId,
    type: type,
    title: title,
    content: content,
    payload: payload,
    readAt: DateTime.now().toIso8601String(),
    createdAt: createdAt,
  );
}

class NotificationList {
  const NotificationList({
    required this.notifications,
    required this.unreadCount,
  });

  factory NotificationList.fromJson(Map<String, dynamic> json) {
    final rawNotifications = json['notifications'];
    return NotificationList(
      notifications: rawNotifications is List
          ? rawNotifications
                .whereType<Map<String, dynamic>>()
                .map(UserNotification.fromJson)
                .toList()
          : const [],
      unreadCount: _asInt(json['unreadCount']),
    );
  }

  final List<UserNotification> notifications;
  final int unreadCount;
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
    this.previewDishes,
  });

  factory Shop.fromJson(Map<String, dynamic> json) {
    final previewDishesJson = json['previewDishes'];
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
      previewDishes: previewDishesJson is List
          ? previewDishesJson
                .whereType<Map<String, dynamic>>()
                .map(Dish.fromJson)
                .toList()
          : null,
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
  final List<Dish>? previewDishes;
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
    this.groupConversationId,
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
      groupConversationId:
          json['groupConversationId'] == null && json['conversationId'] == null
          ? null
          : _asInt(json['groupConversationId'] ?? json['conversationId']),
    );
  }

  final int shopId;
  final String? shopName;
  final String familyName;
  final String familyCode;
  final String? familyImageUrl;
  final String? joinMode;
  final String memberStatus;
  final int? groupConversationId;

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
