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
    this.showPhone = true,
    this.showJoinedFamilies = true,
  });

  factory CurrentUser.fromJson(Map<String, dynamic> json) {
    return CurrentUser(
      id: _asInt(json['id']),
      nickname: _asString(json['nickname']),
      role: _asString(json['role']),
      avatarUrl: json['avatarUrl'] as String?,
      phone: json['phone'] as String?,
      allowPrivateChat: json['allowPrivateChat'] != false,
      showPhone: json['showPhone'] != false,
      showJoinedFamilies: json['showJoinedFamilies'] != false,
    );
  }

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
  final bool allowPrivateChat;
  final bool showPhone;
  final bool showJoinedFamilies;
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
    this.messageVersion = 0,
    this.lastMessageContent,
    this.unreadCount = 0,
    this.muted = false,
    this.pinned = false,
    this.pinnedAt,
    this.hidden = false,
    this.mutedBadge = false,
    this.directUserRole,
    this.directShopId,
    this.directShopName,
  });

  factory ChatConversation.fromJson(Map<String, dynamic> json) =>
      ChatConversation(
        id: _asInt(json['id'] ?? json['conversationId']),
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
        messageVersion: _asInt(json['messageVersion']),
        lastMessageContent: json['lastMessageContent']?.toString(),
        unreadCount: _asInt(json['unreadCount']),
        muted: json['muted'] == true,
        pinned: json['pinned'] == true,
        pinnedAt: json['pinnedAt']?.toString(),
        hidden: json['hidden'] == true,
        mutedBadge: json['mutedBadge'] == true,
        directUserRole: json['directUserRole']?.toString(),
        directShopId: json['directShopId'] == null
            ? null
            : _asInt(json['directShopId']),
        directShopName: json['directShopName']?.toString(),
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
  final int messageVersion;
  final String? lastMessageContent;
  final int unreadCount;
  final bool muted;
  final bool pinned;
  final String? pinnedAt;
  final bool hidden;
  final bool mutedBadge;
  final String? directUserRole;
  final int? directShopId;
  final String? directShopName;

  bool get isDirect => type == 'DIRECT';
  bool get isActive => status == 'ACTIVE';
  String get title =>
      isDirect ? (directUserNickname ?? '私聊') : (name ?? '家庭群聊');
  String? get imageUrl => isDirect ? directUserAvatarUrl : coverImageUrl;
  bool get shouldShowUnreadBadge => unreadCount > 0;
  bool get canDeleteFromList => isDirect;

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
    messageVersion: messageVersion,
    lastMessageContent: lastMessageContent,
    unreadCount: 0,
    muted: muted,
    pinned: pinned,
    pinnedAt: pinnedAt,
    hidden: hidden,
    mutedBadge: mutedBadge,
    directUserRole: directUserRole,
    directShopId: directShopId,
    directShopName: directShopName,
  );

  ChatConversation withPreferences({bool? muted, bool? pinned, bool? hidden}) =>
      ChatConversation(
        id: id,
        type: type,
        status: status,
        name: name,
        coverImageUrl: coverImageUrl,
        directUserId: directUserId,
        directUserNickname: directUserNickname,
        directUserAvatarUrl: directUserAvatarUrl,
        lastMessageAt: lastMessageAt,
        messageVersion: messageVersion,
        lastMessageContent: lastMessageContent,
        unreadCount: unreadCount,
        muted: muted ?? this.muted,
        pinned: pinned ?? this.pinned,
        pinnedAt: pinned == null
            ? pinnedAt
            : (pinned ? DateTime.now().toIso8601String() : null),
        hidden: hidden ?? this.hidden,
        mutedBadge: (muted ?? this.muted) && unreadCount > 0,
        directUserRole: directUserRole,
        directShopId: directShopId,
        directShopName: directShopName,
      );
}

class ConversationHideResult {
  const ConversationHideResult({
    required this.hidden,
    required this.messageVersion,
  });

  factory ConversationHideResult.fromJson(Map<String, dynamic> json) =>
      ConversationHideResult(
        hidden: json['hidden'] == true,
        messageVersion: _asInt(json['messageVersion']),
      );

  final bool hidden;
  final int messageVersion;
}

List<ChatConversation> sortChatConversations(
  Iterable<ChatConversation> conversations,
) {
  final result = conversations.where((item) => !item.hidden).toList();
  DateTime time(String? value) => DateTime.tryParse(value ?? '') ?? DateTime(0);
  result.sort((a, b) {
    if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
    final aTime = a.pinned ? a.pinnedAt : a.lastMessageAt;
    final bTime = b.pinned ? b.pinnedAt : b.lastMessageAt;
    return time(bTime).compareTo(time(aTime));
  });
  return result;
}

class PinnedConversationMerge {
  const PinnedConversationMerge({
    required this.conversations,
    required this.confirmedIds,
  });

  final List<ChatConversation> conversations;
  final Set<int> confirmedIds;
}

PinnedConversationMerge mergePinnedConversationOverrides(
  Iterable<ChatConversation> conversations,
  Map<int, bool> overrides, {
  Set<int> pendingIds = const {},
}) {
  final confirmedIds = <int>{};
  final merged = conversations.map((conversation) {
    final pinned = overrides[conversation.id];
    if (pinned == null) return conversation;
    if (!pendingIds.contains(conversation.id) &&
        conversation.pinned == pinned) {
      confirmedIds.add(conversation.id);
      return conversation;
    }
    return conversation.withPreferences(pinned: pinned);
  }).toList();
  return PinnedConversationMerge(
    conversations: merged,
    confirmedIds: confirmedIds,
  );
}

bool shouldRemoveConversationAfterHide(
  ConversationHideResult result,
  ChatConversation? current,
) =>
    result.hidden &&
    (current == null || current.messageVersion <= result.messageVersion);

class EssencePinnedMerge {
  const EssencePinnedMerge({required this.items, required this.confirmedIds});

  final List<ChatMessage> items;
  final Set<int> confirmedIds;
}

EssencePinnedMerge mergeEssencePinnedOverrides(
  Iterable<ChatMessage> items,
  Map<int, bool> overrides, {
  Set<int> pendingIds = const {},
}) {
  final confirmedIds = <int>{};
  final merged = items.map((item) {
    final pinned = overrides[item.id];
    if (pinned == null) return item;
    if (!pendingIds.contains(item.id) && item.essencePinned == pinned) {
      confirmedIds.add(item.id);
      return item;
    }
    return item.withEssencePinned(pinned);
  }).toList();
  return EssencePinnedMerge(items: merged, confirmedIds: confirmedIds);
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
    this.shopName,
    this.dishName,
    this.dishImageUrl,
    this.dishPrice,
    this.dishDescription,
    this.dishSalesCount,
    this.senderNickname,
    this.senderAvatarUrl,
    this.quotedMessageId,
    this.quotedType,
    this.quotedContent,
    this.quotedSenderName,
    this.quotedMessage,
    this.deliveryStatus = 'DELIVERED',
    this.deliveryFailed = false,
    this.failureReason,
    this.orderId,
    this.orderNo,
    this.orderCreatedAt,
    this.orderStatus,
    this.orderShopName,
    this.orderItems = const [],
    this.orderTotalAmount,
    this.orderRemark,
    this.recalledAt,
    this.imageUrl,
    this.imageExpiresAt,
    this.imageExpired = false,
    this.isEssence = false,
    this.essencePinned = false,
    this.essenceLikes = 0,
    this.essenceLabel,
    this.essenceLikedByMe = false,
    this.sharedShopId,
    this.sharedShopName,
    this.sharedShopLogoUrl,
    this.sharedShopDescription,
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
    shopName: json['shopName']?.toString(),
    dishName: json['dishName']?.toString(),
    dishImageUrl: json['dishImageUrl']?.toString(),
    dishPrice: json['dishPrice'] == null ? null : _asDouble(json['dishPrice']),
    dishDescription: json['dishDescription']?.toString(),
    dishSalesCount: json['dishSalesCount'] == null
        ? null
        : _asInt(json['dishSalesCount']),
    senderNickname: json['senderNickname']?.toString(),
    senderAvatarUrl: json['senderAvatarUrl']?.toString(),
    quotedMessageId: json['quotedMessageId'] == null
        ? null
        : _asInt(json['quotedMessageId']),
    quotedType: json['quotedType']?.toString(),
    quotedContent: json['quotedContent']?.toString(),
    quotedSenderName: json['quotedSenderName']?.toString(),
    quotedMessage: json['quotedMessage'] is Map<String, dynamic>
        ? ChatMessage.fromJson({
            ...(json['quotedMessage'] as Map<String, dynamic>),
            'conversationId': json['conversationId'] ?? 0,
            'senderUserId': 0,
            'createdAt': '',
          })
        : null,
    deliveryStatus: _asString(json['deliveryStatus'], fallback: 'DELIVERED'),
    deliveryFailed: json['deliveryFailed'] == true,
    failureReason: json['failureReason']?.toString(),
    orderId: json['orderId'] == null ? null : _asInt(json['orderId']),
    orderNo: json['orderNo']?.toString(),
    orderCreatedAt: json['orderCreatedAt']?.toString(),
    orderStatus: json['orderStatus']?.toString(),
    orderShopName: json['orderShopName']?.toString(),
    orderItems: (json['orderItems'] as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(OrderShareItem.fromJson)
        .toList(),
    orderTotalAmount: json['orderTotalAmount'] == null
        ? null
        : _asDouble(json['orderTotalAmount']),
    orderRemark: json['orderRemark']?.toString(),
    recalledAt: json['recalledAt']?.toString(),
    imageUrl: json['imageUrl']?.toString(),
    imageExpiresAt: json['imageExpiresAt']?.toString(),
    imageExpired: json['imageExpired'] == true,
    isEssence: json['essence'] == true,
    essencePinned: json['essencePinned'] == true,
    essenceLikes: _asInt(json['essenceLikeCount'] ?? json['essenceLikes']),
    essenceLabel: json['essenceLabel']?.toString(),
    essenceLikedByMe: json['essenceLikedByMe'] == true,
    sharedShopId: json['sharedShopId'] == null
        ? null
        : _asInt(json['sharedShopId']),
    sharedShopName: json['sharedShopName']?.toString(),
    sharedShopLogoUrl: json['sharedShopLogoUrl']?.toString(),
    sharedShopDescription: json['sharedShopDescription']?.toString(),
  );
  final int id;
  final int conversationId;
  final int senderUserId;
  final String type;
  final String content;
  final String createdAt;
  final int? dishId;
  final int? shopId;
  final String? shopName;
  final String? dishName;
  final String? dishImageUrl;
  final double? dishPrice;
  final String? dishDescription;
  final int? dishSalesCount;
  final String? senderNickname;
  final String? senderAvatarUrl;
  final int? quotedMessageId;
  final String? quotedType;
  final String? quotedContent;
  final String? quotedSenderName;
  final ChatMessage? quotedMessage;
  final String deliveryStatus;
  final bool deliveryFailed;
  final String? failureReason;
  final int? orderId;
  final String? orderNo;
  final String? orderCreatedAt;
  final String? orderStatus;
  final String? orderShopName;
  final List<OrderShareItem> orderItems;
  final double? orderTotalAmount;
  final String? orderRemark;
  final String? recalledAt;
  final String? imageUrl;
  final String? imageExpiresAt;
  final bool imageExpired;
  final bool isEssence;
  final bool essencePinned;
  final int essenceLikes;
  final String? essenceLabel;
  final bool essenceLikedByMe;
  final int? sharedShopId;
  final String? sharedShopName;
  final String? sharedShopLogoUrl;
  final String? sharedShopDescription;

  bool get isDishShare => type == 'DISH_SHARE';
  bool get isOrderShare => type == 'ORDER_SHARE';
  bool get isImage => type == 'IMAGE_SHARE';
  bool get isShopShare => type == 'SHOP_SHARE';
  bool get isSystem => type == 'SYSTEM';
  bool get isRecalled => recalledAt != null && recalledAt!.isNotEmpty;
  DateTime? get sentAt => DateTime.tryParse(createdAt);

  ChatMessage withEssencePinned(bool pinned) => ChatMessage(
    id: id,
    conversationId: conversationId,
    senderUserId: senderUserId,
    type: type,
    content: content,
    createdAt: createdAt,
    dishId: dishId,
    shopId: shopId,
    shopName: shopName,
    dishName: dishName,
    dishImageUrl: dishImageUrl,
    dishPrice: dishPrice,
    dishDescription: dishDescription,
    dishSalesCount: dishSalesCount,
    senderNickname: senderNickname,
    senderAvatarUrl: senderAvatarUrl,
    quotedMessageId: quotedMessageId,
    quotedType: quotedType,
    quotedContent: quotedContent,
    quotedSenderName: quotedSenderName,
    quotedMessage: quotedMessage,
    deliveryStatus: deliveryStatus,
    deliveryFailed: deliveryFailed,
    failureReason: failureReason,
    orderId: orderId,
    orderNo: orderNo,
    orderCreatedAt: orderCreatedAt,
    orderStatus: orderStatus,
    orderShopName: orderShopName,
    orderItems: orderItems,
    orderTotalAmount: orderTotalAmount,
    orderRemark: orderRemark,
    recalledAt: recalledAt,
    imageUrl: imageUrl,
    imageExpiresAt: imageExpiresAt,
    imageExpired: imageExpired,
    isEssence: isEssence,
    essencePinned: pinned,
    essenceLikes: essenceLikes,
    essenceLabel: essenceLabel,
    essenceLikedByMe: essenceLikedByMe,
    sharedShopId: sharedShopId,
    sharedShopName: sharedShopName,
    sharedShopLogoUrl: sharedShopLogoUrl,
    sharedShopDescription: sharedShopDescription,
  );
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

String formatChatMessageTime(DateTime value, {DateTime? now}) {
  final local = value.toLocal();
  final current = now ?? DateTime.now();
  String two(int number) => number.toString().padLeft(2, '0');
  final time = '${two(local.hour)}:${two(local.minute)}';
  final day = DateTime(local.year, local.month, local.day);
  final today = DateTime(current.year, current.month, current.day);
  if (day == today) return time;
  if (day == today.subtract(const Duration(days: 1))) return '昨天 $time';
  return '${local.month}月${local.day}日 $time';
}

String familyMemberStatusLabel(String status) => switch (status) {
  'ACTIVE' => '已加入',
  'PENDING' => '待处理',
  'LEFT' => '已退出',
  'REMOVED' => '已移出',
  _ => status,
};

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
    this.joinApprovalRequired = false,
  });
  factory FamilyGroup.fromJson(Map<String, dynamic> json) => FamilyGroup(
    id: _asInt(json['id']),
    shopId: _asInt(json['shopId']),
    name: _asString(json['name'], fallback: '家庭群聊'),
    status: _asString(json['status'], fallback: 'ACTIVE'),
    coverImageUrl: json['coverImageUrl']?.toString(),
    announcement: json['announcement']?.toString(),
    joinApprovalRequired: json['joinApprovalRequired'] == true,
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
  final bool joinApprovalRequired;
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
        read: json['read'] == true || json['readAt'] != null,
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

  FamilyMemberInfo withStatus(String value) => FamilyMemberInfo(
    id: id,
    userId: userId,
    status: value,
    nickname: nickname,
    phone: phone,
    avatarUrl: avatarUrl,
  );
}

class OrderShareItem {
  const OrderShareItem({required this.dishName, required this.quantity});

  factory OrderShareItem.fromJson(Map<String, dynamic> json) => OrderShareItem(
    dishName: _asString(json['dishName']),
    quantity: _asInt(json['quantity']),
  );

  final String dishName;
  final int quantity;
}

class ChatBlockState {
  const ChatBlockState({
    required this.userId,
    required this.blockedByMe,
    required this.blockedMe,
  });

  factory ChatBlockState.fromJson(Map<String, dynamic> json) => ChatBlockState(
    userId: _asInt(json['userId']),
    blockedByMe: json['blockedByMe'] == true,
    blockedMe: json['blockedMe'] == true,
  );

  final int userId;
  final bool blockedByMe;
  final bool blockedMe;
}

class ChatUserProfile {
  const ChatUserProfile({
    required this.userId,
    required this.nickname,
    this.avatarUrl,
    this.phone,
    this.role,
    this.joinedFamilies = const [],
    this.shopId,
    this.shopName,
    this.shopLogoUrl,
    this.shopDescription,
  });

  factory ChatUserProfile.fromJson(Map<String, dynamic> json) =>
      ChatUserProfile(
        userId: _asInt(json['userId'] ?? json['id']),
        nickname: _asString(json['nickname'], fallback: '用户'),
        avatarUrl: json['avatarUrl']?.toString(),
        phone: json['phoneVisible'] == false ? null : json['phone']?.toString(),
        role: json['role']?.toString(),
        joinedFamilies: json['joinedFamiliesVisible'] == false
            ? const []
            : (json['joinedFamilies'] as List? ?? const [])
                  .map(
                    (item) => item is Map
                        ? (item['familyName'] ??
                                  item['shopName'] ??
                                  item['name'] ??
                                  '')
                              .toString()
                        : item.toString(),
                  )
                  .where((item) => item.isNotEmpty)
                  .toList(),
        shopId: (json['shopProfile'] as Map?)?['shopId'] == null
            ? (json['shopId'] == null ? null : _asInt(json['shopId']))
            : _asInt((json['shopProfile'] as Map)['shopId']),
        shopName:
            (json['shopProfile'] as Map?)?['shopName']?.toString() ??
            json['shopName']?.toString(),
        shopLogoUrl:
            (json['shopProfile'] as Map?)?['shopLogoUrl']?.toString() ??
            json['shopLogoUrl']?.toString(),
        shopDescription:
            (json['shopProfile'] as Map?)?['shopDescription']?.toString() ??
            json['shopDescription']?.toString(),
      );

  final int userId;
  final String nickname;
  final String? avatarUrl;
  final String? phone;
  final String? role;
  final List<String> joinedFamilies;
  final int? shopId;
  final String? shopName;
  final String? shopLogoUrl;
  final String? shopDescription;
  bool get isMerchant => role == 'SHOP' || shopId != null;
}

class ChatEssenceList {
  const ChatEssenceList({required this.items, required this.unreadCount});
  factory ChatEssenceList.fromJson(Map<String, dynamic> json) =>
      ChatEssenceList(
        items: (json['items'] as List? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(
              (item) => ChatMessage.fromJson(
                item['message'] is Map<String, dynamic>
                    ? {
                        ...(item['message'] as Map<String, dynamic>),
                        'essence': true,
                        'essencePinned': item['pinned'],
                        'essenceLikeCount': item['likeCount'],
                        'essenceLikedByMe': item['liked'],
                      }
                    : item,
              ),
            )
            .toList(),
        unreadCount: _asInt(json['unreadCount']),
      );
  final List<ChatMessage> items;
  final int unreadCount;
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
