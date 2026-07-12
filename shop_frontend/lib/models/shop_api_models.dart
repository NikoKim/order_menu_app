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
    this.allowPrivateChat = true,
  });

  factory CurrentUser.fromJson(Map<String, dynamic> json) {
    return CurrentUser(
      id: _asInt(json['id']),
      nickname: _asString(json['nickname']),
      role: _asString(json['role']),
      avatarUrl: json['avatarUrl'] as String?,
      phone: json['phone'] as String?,
      allowPrivateChat: json['allowPrivateChat'] != false,
    );
  }

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
  final bool allowPrivateChat;
}

class ChatConversation {
  const ChatConversation({
    required this.id,
    required this.type,
    required this.status,
    this.name,
    this.coverImageUrl,
    this.directUserId,
    this.directUserNickname,
    this.directUserAvatarUrl,
    this.lastMessageAt,
    this.lastMessageContent,
    this.unreadCount = 0,
    this.muted = false,
  });

  factory ChatConversation.fromJson(Map<String, dynamic> json) =>
      ChatConversation(
        id: _asInt(json['id']),
        type: _asString(json['type'], fallback: 'DIRECT'),
        status: _asString(json['status'], fallback: 'ACTIVE'),
        name: json['name']?.toString(),
        coverImageUrl: json['coverImageUrl']?.toString(),
        directUserId: json['directUserId'] == null
            ? null
            : _asInt(json['directUserId']),
        directUserNickname: json['directUserNickname']?.toString(),
        directUserAvatarUrl: json['directUserAvatarUrl']?.toString(),
        lastMessageAt: json['lastMessageAt']?.toString(),
        lastMessageContent: json['lastMessageContent']?.toString(),
        unreadCount: _asInt(json['unreadCount']),
        muted: json['muted'] == true,
      );

  final int id;
  final String type;
  final String status;
  final String? name;
  final String? coverImageUrl;
  final int? directUserId;
  final String? directUserNickname;
  final String? directUserAvatarUrl;
  final String? lastMessageAt;
  final String? lastMessageContent;
  final int unreadCount;
  final bool muted;

  bool get isDirect => type == 'DIRECT';
  bool get isActive => status == 'ACTIVE';
  String get title =>
      isDirect ? (directUserNickname ?? '私聊') : (name ?? '家庭群聊');
  String? get imageUrl => isDirect ? directUserAvatarUrl : coverImageUrl;
  bool get shouldShowUnreadBadge => unreadCount > 0 && !muted;

  ChatConversation markedRead() => ChatConversation(
    id: id,
    type: type,
    status: status,
    name: name,
    coverImageUrl: coverImageUrl,
    directUserId: directUserId,
    directUserNickname: directUserNickname,
    directUserAvatarUrl: directUserAvatarUrl,
    lastMessageAt: lastMessageAt,
    lastMessageContent: lastMessageContent,
    unreadCount: 0,
    muted: muted,
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
    this.dishDescription,
    this.dishSalesCount,
    this.senderNickname,
    this.senderAvatarUrl,
  });
  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
    id: _asInt(json['id']),
    conversationId: _asInt(json['conversationId']),
    senderUserId: _asInt(json['senderUserId']),
    type: _asString(json['type'], fallback: 'TEXT'),
    content: _asString(json['content']),
    createdAt: _asString(json['createdAt']),
    dishId: json['dishId'] == null ? null : _asInt(json['dishId']),
    shopId: json['shopId'] == null ? null : _asInt(json['shopId']),
    dishName: json['dishName']?.toString(),
    dishImageUrl: json['dishImageUrl']?.toString(),
    dishPrice: json['dishPrice'] == null ? null : _asDouble(json['dishPrice']),
    dishDescription: json['dishDescription']?.toString(),
    dishSalesCount: json['dishSalesCount'] == null
        ? null
        : _asInt(json['dishSalesCount']),
    senderNickname: json['senderNickname']?.toString(),
    senderAvatarUrl: json['senderAvatarUrl']?.toString(),
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
  final String? dishDescription;
  final int? dishSalesCount;
  final String? senderNickname;
  final String? senderAvatarUrl;

  bool get isDishShare => type == 'DISH_SHARE';
  DateTime? get sentAt => DateTime.tryParse(createdAt);
}

bool shouldShowChatTimestamp(DateTime current, DateTime? previous) {
  return previous == null ||
      current.difference(previous).abs() > const Duration(minutes: 1);
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

class ChatMessages {
  const ChatMessages({required this.conversation, required this.messages});
  factory ChatMessages.fromJson(Map<String, dynamic> json) => ChatMessages(
    conversation: ChatConversation.fromJson(
      json['conversation'] as Map<String, dynamic>? ?? const {},
    ),
    messages: (json['messages'] as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(ChatMessage.fromJson)
        .toList(),
  );
  final ChatConversation conversation;
  final List<ChatMessage> messages;
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
        nickname: _asString(json['nickname']),
        role: _asString(json['role'], fallback: 'MEMBER'),
        memberStatus: _asString(json['memberStatus'], fallback: 'ACTIVE'),
        muted: json['muted'] == true,
        avatarUrl: json['avatarUrl']?.toString(),
      );
  final int userId;
  final String nickname;
  final String role;
  final String memberStatus;
  final bool muted;
  final String? avatarUrl;
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
  factory FamilyGroup.fromJson(Map<String, dynamic> json) => FamilyGroup(
    id: _asInt(json['id']),
    shopId: _asInt(json['shopId']),
    name: _asString(json['name'], fallback: '家庭群聊'),
    status: _asString(json['status'], fallback: 'ACTIVE'),
    coverImageUrl: json['coverImageUrl']?.toString(),
    announcement: json['announcement']?.toString(),
    members: (json['members'] as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(FamilyGroupMember.fromJson)
        .toList(),
  );
  final int id;
  final int shopId;
  final String name;
  final String status;
  final List<FamilyGroupMember> members;
  final String? coverImageUrl;
  final String? announcement;
  bool get canManage => status == 'ACTIVE';
}

class FamilyGroupEligibility {
  const FamilyGroupEligibility._(this.approvedMemberCount);
  factory FamilyGroupEligibility.fromMembers(int approvedMemberCount) =>
      FamilyGroupEligibility._(approvedMemberCount);
  final int approvedMemberCount;
  bool get canCreate => approvedMemberCount >= 2;
}

class UserNotification {
  const UserNotification({
    required this.id,
    required this.title,
    required this.content,
    required this.read,
    required this.createdAt,
  });
  factory UserNotification.fromJson(Map<String, dynamic> json) =>
      UserNotification(
        id: _asInt(json['id']),
        title: _asString(json['title']),
        content: _asString(json['content']),
        read: json['read'] == true,
        createdAt: _asString(json['createdAt']),
      );
  final int id;
  final String title;
  final String content;
  final bool read;
  final String createdAt;
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
