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
    this.showJoinedFamilies = true,
    this.showPhone = true,
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
      showJoinedFamilies: json['showJoinedFamilies'] != false,
      showPhone: json['showPhone'] != false,
    );
  }

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
  final bool allowPrivateChat;
  final bool autoJoinFamilyChat;
  final bool showJoinedFamilies;
  final bool showPhone;
}

class ChatConversation {
  const ChatConversation({
    required this.id,
    required this.type,
    required this.status,
    this.name,
    this.coverImageUrl,
    this.lastMessageAt,
    this.messageVersion = 0,
    this.lastMessageContent,
    this.unreadCount = 0,
    this.muted = false,
    this.directUserId,
    this.directUserNickname,
    this.directUserAvatarUrl,
    this.pinned = false,
    this.pinnedAt,
    this.hidden = false,
    this.mutedBadge = false,
  });

  factory ChatConversation.fromJson(Map<String, dynamic> json) {
    return ChatConversation(
      id: _asInt(json['id']),
      type: json['type']?.toString() ?? 'DIRECT',
      status: json['status']?.toString() ?? 'ACTIVE',
      name: json['name']?.toString(),
      coverImageUrl: json['coverImageUrl']?.toString(),
      lastMessageAt: json['lastMessageAt']?.toString(),
      messageVersion: _asInt(json['messageVersion']),
      lastMessageContent: (json['lastMessageContent'] ?? json['lastMessage'])
          ?.toString(),
      unreadCount: _asInt(json['unreadCount']),
      muted: json['muted'] == true,
      directUserId: json['directUserId'] == null
          ? null
          : _asInt(json['directUserId']),
      directUserNickname: json['directUserNickname']?.toString(),
      directUserAvatarUrl: json['directUserAvatarUrl']?.toString(),
      pinned: json['pinned'] == true,
      pinnedAt: json['pinnedAt']?.toString(),
      hidden: json['hidden'] == true,
      mutedBadge: json['mutedBadge'] == true,
    );
  }

  final int id;
  final String type;
  final String status;
  final String? name;
  final String? coverImageUrl;
  final String? lastMessageAt;
  final int messageVersion;
  final String? lastMessageContent;
  final int unreadCount;
  final bool muted;
  final int? directUserId;
  final String? directUserNickname;
  final String? directUserAvatarUrl;
  final bool pinned;
  final String? pinnedAt;
  final bool hidden;
  final bool mutedBadge;

  bool get isDirect => type == 'DIRECT';
  bool get isActive => status == 'ACTIVE';
  String get title =>
      isDirect ? (directUserNickname ?? '私聊') : (name ?? '家庭群聊');
  String? get imageUrl => isDirect ? directUserAvatarUrl : coverImageUrl;
  bool get showUnreadBadge => unreadCount > 0 && !muted;
  bool get showMutedBadge => muted && mutedBadge && unreadCount > 0;

  ChatConversation markedRead() => ChatConversation(
    id: id,
    type: type,
    status: status,
    name: name,
    coverImageUrl: coverImageUrl,
    lastMessageAt: lastMessageAt,
    messageVersion: messageVersion,
    lastMessageContent: lastMessageContent,
    unreadCount: 0,
    muted: muted,
    directUserId: directUserId,
    directUserNickname: directUserNickname,
    directUserAvatarUrl: directUserAvatarUrl,
    pinned: pinned,
    pinnedAt: pinnedAt,
    hidden: hidden,
    mutedBadge: false,
  );

  ChatConversation withPreferences({bool? muted, bool? pinned, bool? hidden}) =>
      ChatConversation(
        id: id,
        type: type,
        status: status,
        name: name,
        coverImageUrl: coverImageUrl,
        lastMessageAt: lastMessageAt,
        messageVersion: messageVersion,
        lastMessageContent: lastMessageContent,
        unreadCount: unreadCount,
        muted: muted ?? this.muted,
        directUserId: directUserId,
        directUserNickname: directUserNickname,
        directUserAvatarUrl: directUserAvatarUrl,
        pinned: pinned ?? this.pinned,
        pinnedAt: pinned == null
            ? pinnedAt
            : (pinned ? DateTime.now().toIso8601String() : null),
        hidden: hidden ?? this.hidden,
        mutedBadge: (muted ?? this.muted) && unreadCount > 0,
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
    this.senderNickname,
    this.senderAvatarUrl,
    this.quotedMessageId,
    this.quotedType,
    this.quotedContent,
    this.quotedSenderName,
    this.quotedMessage,
    this.recalledAt,
    this.dishDescription,
    this.dishSales,
    this.orderId,
    this.orderNo,
    this.orderCreatedAt,
    this.orderStatus,
    this.orderShopName,
    this.orderItems = const [],
    this.orderTotalAmount,
    this.orderRemark,
    this.deliveryStatus = 'DELIVERED',
    this.deliveryFailed = false,
    this.failureReason,
    this.imageUrl,
    this.imageExpiresAt,
    this.imageExpired = false,
    this.sharedShopId,
    this.sharedShopName,
    this.sharedShopLogoUrl,
    this.sharedShopDescription,
    this.sharedMomentId,
    this.sharedMomentCommentId,
    this.sharedMomentTitle,
    this.sharedMomentContent,
    this.sharedMomentPreview,
    this.sharedCommentPreview,
    this.sharedMomentAuthorName,
    this.sharedMomentAuthorAvatar,
    this.sharedMomentCoverUrl,
    this.sharedCardIcon,
    this.essence = false,
    this.essencePinned = false,
    this.essenceLikeCount = 0,
    this.essenceLikedByMe = false,
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
    shopName: json['shopName']?.toString(),
    dishName: json['dishName']?.toString(),
    dishImageUrl: json['dishImageUrl']?.toString(),
    dishPrice: json['dishPrice'] == null ? null : _asDouble(json['dishPrice']),
    senderNickname: json['senderNickname']?.toString(),
    senderAvatarUrl: json['senderAvatarUrl']?.toString(),
    quotedMessageId: json['quotedMessageId'] == null
        ? null
        : _asInt(json['quotedMessageId']),
    quotedType: json['quotedType']?.toString(),
    quotedContent: json['quotedContent']?.toString(),
    quotedSenderName: json['quotedSenderName']?.toString(),
    quotedMessage: json['quotedMessage'] is Map<String, dynamic>
        ? QuotedChatMessage.fromJson(
            json['quotedMessage'] as Map<String, dynamic>,
          )
        : null,
    recalledAt: json['recalledAt']?.toString(),
    dishDescription: json['dishDescription']?.toString(),
    dishSales: json['dishSalesCount'] == null
        ? (json['dishSales'] == null ? null : _asInt(json['dishSales']))
        : _asInt(json['dishSalesCount']),
    orderId: json['orderId'] == null ? null : _asInt(json['orderId']),
    orderNo: json['orderNo']?.toString(),
    orderCreatedAt: json['orderCreatedAt']?.toString(),
    orderStatus: json['orderStatus']?.toString(),
    orderShopName: json['orderShopName']?.toString(),
    orderItems: json['orderItems'] is List
        ? (json['orderItems'] as List)
              .whereType<Map<String, dynamic>>()
              .map(OrderShareItem.fromJson)
              .toList()
        : const [],
    orderTotalAmount: json['orderTotalAmount'] == null
        ? null
        : _asDouble(json['orderTotalAmount']),
    orderRemark: json['orderRemark']?.toString(),
    deliveryStatus: json['deliveryStatus']?.toString() ?? 'DELIVERED',
    deliveryFailed: json['deliveryFailed'] == true,
    failureReason: json['failureReason']?.toString(),
    imageUrl: json['imageUrl']?.toString(),
    imageExpiresAt: json['imageExpiresAt']?.toString(),
    imageExpired: json['imageExpired'] == true,
    sharedShopId: json['sharedShopId'] == null
        ? null
        : _asInt(json['sharedShopId']),
    sharedShopName: json['sharedShopName']?.toString(),
    sharedShopLogoUrl: json['sharedShopLogoUrl']?.toString(),
    sharedShopDescription: json['sharedShopDescription']?.toString(),
    sharedMomentId: json['sharedMomentId'] == null
        ? null
        : _asInt(json['sharedMomentId']),
    sharedMomentCommentId: json['sharedMomentCommentId'] == null
        ? null
        : _asInt(json['sharedMomentCommentId']),
    sharedMomentTitle: json['sharedMomentTitle']?.toString(),
    sharedMomentContent: json['sharedMomentContent']?.toString(),
    sharedMomentPreview: json['sharedMomentPreview']?.toString(),
    sharedCommentPreview: json['sharedCommentPreview']?.toString(),
    sharedMomentAuthorName: json['sharedMomentAuthorName']?.toString(),
    sharedMomentAuthorAvatar: json['sharedMomentAuthorAvatar']?.toString(),
    sharedMomentCoverUrl: json['sharedMomentCoverUrl']?.toString(),
    sharedCardIcon: json['sharedCardIcon']?.toString(),
    essence: json['essence'] == true || json['isEssence'] == true,
    essencePinned: json['essencePinned'] == true,
    essenceLikeCount: _asInt(json['essenceLikeCount']),
    essenceLikedByMe:
        json['essenceLikedByMe'] == true || json['likedByMe'] == true,
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
  final String? senderNickname;
  final String? senderAvatarUrl;
  final int? quotedMessageId;
  final String? quotedType;
  final String? quotedContent;
  final String? quotedSenderName;
  final QuotedChatMessage? quotedMessage;
  final String? recalledAt;
  final String? dishDescription;
  final int? dishSales;
  final int? orderId;
  final String? orderNo;
  final String? orderCreatedAt;
  final String? orderStatus;
  final String? orderShopName;
  final List<OrderShareItem> orderItems;
  final double? orderTotalAmount;
  final String? orderRemark;
  final String deliveryStatus;
  final bool deliveryFailed;
  final String? failureReason;
  final String? imageUrl;
  final String? imageExpiresAt;
  final bool imageExpired;
  final int? sharedShopId;
  final String? sharedShopName;
  final String? sharedShopLogoUrl;
  final String? sharedShopDescription;
  final int? sharedMomentId;
  final int? sharedMomentCommentId;
  final String? sharedMomentTitle;
  final String? sharedMomentContent;
  final String? sharedMomentPreview;
  final String? sharedCommentPreview;
  final String? sharedMomentAuthorName;
  final String? sharedMomentAuthorAvatar;
  final String? sharedMomentCoverUrl;
  final String? sharedCardIcon;
  final bool essence;
  final bool essencePinned;
  final int essenceLikeCount;
  final bool essenceLikedByMe;

  bool get isDishShare => type == 'DISH_SHARE';
  bool get isOrderShare => type == 'ORDER_SHARE';
  bool get isImageShare => type == 'IMAGE_SHARE';
  bool get isShopShare => type == 'SHOP_SHARE';
  bool get isMomentShare =>
      type == 'MOMENT_SHARE' || type == 'MOMENT_COMMENT_SHARE';
  bool get isSystem => type == 'SYSTEM';
  bool get isRecalled => recalledAt != null && recalledAt!.isNotEmpty;
}

class ChatUserProfile {
  const ChatUserProfile({
    required this.id,
    required this.nickname,
    required this.role,
    this.avatarUrl,
    this.phone,
    this.joinedFamilies = const [],
    this.shopId,
    this.shopName,
    this.shopLogoUrl,
    this.shopDescription,
  });

  factory ChatUserProfile.fromJson(Map<String, dynamic> json) =>
      ChatUserProfile(
        id: _asInt(json['id'] ?? json['userId']),
        nickname: json['nickname']?.toString() ?? '用户',
        role: json['role']?.toString() ?? 'CUSTOMER',
        avatarUrl: json['avatarUrl']?.toString(),
        phone: json['phoneVisible'] == false ? null : json['phone']?.toString(),
        joinedFamilies: json['joinedFamiliesVisible'] == false
            ? const []
            : (json['joinedFamilies'] as List? ?? const [])
                  .map(
                    (item) => item is Map
                        ? (item['familyName'] ?? item['shopName'] ?? '')
                              .toString()
                        : item.toString(),
                  )
                  .where((item) => item.isNotEmpty)
                  .toList(),
        shopId: _profileShopId(json),
        shopName: _profileShopValue(json, 'shopName'),
        shopLogoUrl: _profileShopValue(json, 'shopLogoUrl'),
        shopDescription: _profileShopValue(json, 'shopDescription'),
      );

  final int id;
  final String nickname;
  final String role;
  final String? avatarUrl;
  final String? phone;
  final List<String> joinedFamilies;
  final int? shopId;
  final String? shopName;
  final String? shopLogoUrl;
  final String? shopDescription;
  bool get isMerchant => role == 'SHOP' || role == 'MERCHANT';
}

class ConversationPreferences {
  const ConversationPreferences({
    required this.conversationId,
    required this.muted,
    required this.hidden,
    required this.pinned,
  });

  factory ConversationPreferences.fromJson(Map<String, dynamic> json) =>
      ConversationPreferences(
        conversationId: _asInt(json['conversationId'] ?? json['id']),
        muted: json['muted'] == true,
        hidden: json['hidden'] == true,
        pinned: json['pinned'] == true,
      );

  final int conversationId;
  final bool muted;
  final bool hidden;
  final bool pinned;
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

class OrderShareItem {
  const OrderShareItem({
    required this.dishName,
    required this.quantity,
    this.dishImageUrl,
    this.unitPrice,
    this.subtotalAmount,
    this.selectedOptions,
  });

  factory OrderShareItem.fromJson(Map<String, dynamic> json) => OrderShareItem(
    dishName: json['dishName']?.toString() ?? '',
    quantity: _asInt(json['quantity']),
    dishImageUrl: json['dishImageUrl']?.toString(),
    unitPrice: json['unitPrice'] == null ? null : _asDouble(json['unitPrice']),
    subtotalAmount: json['subtotalAmount'] == null
        ? null
        : _asDouble(json['subtotalAmount']),
    selectedOptions: json['selectedOptions']?.toString(),
  );

  final String dishName;
  final int quantity;
  final String? dishImageUrl;
  final double? unitPrice;
  final double? subtotalAmount;
  final String? selectedOptions;
}

class QuotedChatMessage {
  const QuotedChatMessage({
    required this.id,
    required this.type,
    required this.content,
    this.dishId,
    this.shopId,
    this.shopName,
    this.dishName,
    this.dishImageUrl,
    this.dishPrice,
    this.dishDescription,
    this.dishSales,
    this.orderId,
    this.orderNo,
    this.orderCreatedAt,
    this.orderStatus,
    this.orderShopName,
    this.orderItems = const [],
    this.orderTotalAmount,
    this.orderRemark,
    this.imageUrl,
    this.imageExpiresAt,
    this.imageExpired = false,
    this.sharedShopId,
    this.sharedShopName,
    this.sharedShopLogoUrl,
    this.sharedShopDescription,
    this.sharedMomentId,
    this.sharedMomentCommentId,
    this.sharedMomentTitle,
    this.sharedMomentContent,
    this.sharedMomentPreview,
    this.sharedCommentPreview,
    this.sharedMomentAuthorName,
    this.sharedMomentAuthorAvatar,
    this.sharedMomentCoverUrl,
    this.sharedCardIcon,
  });

  factory QuotedChatMessage.fromJson(Map<String, dynamic> json) =>
      QuotedChatMessage(
        id: _asInt(json['id']),
        type: json['type']?.toString() ?? 'TEXT',
        content: json['content']?.toString() ?? '',
        dishId: json['dishId'] == null ? null : _asInt(json['dishId']),
        shopId: json['shopId'] == null ? null : _asInt(json['shopId']),
        shopName: json['shopName']?.toString(),
        dishName: json['dishName']?.toString(),
        dishImageUrl: json['dishImageUrl']?.toString(),
        dishPrice: json['dishPrice'] == null
            ? null
            : _asDouble(json['dishPrice']),
        dishDescription: json['dishDescription']?.toString(),
        dishSales: json['dishSalesCount'] == null
            ? null
            : _asInt(json['dishSalesCount']),
        orderId: json['orderId'] == null ? null : _asInt(json['orderId']),
        orderNo: json['orderNo']?.toString(),
        orderCreatedAt: json['orderCreatedAt']?.toString(),
        orderStatus: json['orderStatus']?.toString(),
        orderShopName: json['orderShopName']?.toString(),
        orderItems: json['orderItems'] is List
            ? (json['orderItems'] as List)
                  .whereType<Map<String, dynamic>>()
                  .map(OrderShareItem.fromJson)
                  .toList()
            : const [],
        orderTotalAmount: json['orderTotalAmount'] == null
            ? null
            : _asDouble(json['orderTotalAmount']),
        orderRemark: json['orderRemark']?.toString(),
        imageUrl: json['imageUrl']?.toString(),
        imageExpiresAt: json['imageExpiresAt']?.toString(),
        imageExpired: json['imageExpired'] == true,
        sharedShopId: json['sharedShopId'] == null
            ? null
            : _asInt(json['sharedShopId']),
        sharedShopName: json['sharedShopName']?.toString(),
        sharedShopLogoUrl: json['sharedShopLogoUrl']?.toString(),
        sharedShopDescription: json['sharedShopDescription']?.toString(),
        sharedMomentId: json['sharedMomentId'] == null
            ? null
            : _asInt(json['sharedMomentId']),
        sharedMomentCommentId: json['sharedMomentCommentId'] == null
            ? null
            : _asInt(json['sharedMomentCommentId']),
        sharedMomentTitle: json['sharedMomentTitle']?.toString(),
        sharedMomentContent: json['sharedMomentContent']?.toString(),
        sharedMomentPreview: json['sharedMomentPreview']?.toString(),
        sharedCommentPreview: json['sharedCommentPreview']?.toString(),
        sharedMomentAuthorName: json['sharedMomentAuthorName']?.toString(),
        sharedMomentAuthorAvatar: json['sharedMomentAuthorAvatar']?.toString(),
        sharedMomentCoverUrl: json['sharedMomentCoverUrl']?.toString(),
        sharedCardIcon: json['sharedCardIcon']?.toString(),
      );

  final int id;
  final String type;
  final String content;
  final int? dishId;
  final int? shopId;
  final String? shopName;
  final String? dishName;
  final String? dishImageUrl;
  final double? dishPrice;
  final String? dishDescription;
  final int? dishSales;
  final int? orderId;
  final String? orderNo;
  final String? orderCreatedAt;
  final String? orderStatus;
  final String? orderShopName;
  final List<OrderShareItem> orderItems;
  final double? orderTotalAmount;
  final String? orderRemark;
  final String? imageUrl;
  final String? imageExpiresAt;
  final bool imageExpired;
  final int? sharedShopId;
  final String? sharedShopName;
  final String? sharedShopLogoUrl;
  final String? sharedShopDescription;
  final int? sharedMomentId;
  final int? sharedMomentCommentId;
  final String? sharedMomentTitle;
  final String? sharedMomentContent;
  final String? sharedMomentPreview;
  final String? sharedCommentPreview;
  final String? sharedMomentAuthorName;
  final String? sharedMomentAuthorAvatar;
  final String? sharedMomentCoverUrl;
  final String? sharedCardIcon;

  ChatMessage toChatMessage() => ChatMessage(
    id: id,
    conversationId: 0,
    senderUserId: 0,
    type: type,
    content: content,
    createdAt: '',
    dishId: dishId,
    shopId: shopId,
    shopName: shopName,
    dishName: dishName,
    dishImageUrl: dishImageUrl,
    dishPrice: dishPrice,
    dishDescription: dishDescription,
    dishSales: dishSales,
    orderId: orderId,
    orderNo: orderNo,
    orderCreatedAt: orderCreatedAt,
    orderStatus: orderStatus,
    orderShopName: orderShopName,
    orderItems: orderItems,
    orderTotalAmount: orderTotalAmount,
    orderRemark: orderRemark,
    imageUrl: imageUrl,
    imageExpiresAt: imageExpiresAt,
    imageExpired: imageExpired,
    sharedShopId: sharedShopId,
    sharedShopName: sharedShopName,
    sharedShopLogoUrl: sharedShopLogoUrl,
    sharedShopDescription: sharedShopDescription,
    sharedMomentId: sharedMomentId,
    sharedMomentCommentId: sharedMomentCommentId,
    sharedMomentTitle: sharedMomentTitle,
    sharedMomentContent: sharedMomentContent,
    sharedMomentPreview: sharedMomentPreview,
    sharedCommentPreview: sharedCommentPreview,
    sharedMomentAuthorName: sharedMomentAuthorName,
    sharedMomentAuthorAvatar: sharedMomentAuthorAvatar,
    sharedMomentCoverUrl: sharedMomentCoverUrl,
    sharedCardIcon: sharedCardIcon,
  );
}

class ChatBlock {
  const ChatBlock({
    required this.userId,
    required this.blockedByMe,
    required this.blockedMe,
  });

  factory ChatBlock.fromJson(Map<String, dynamic> json) => ChatBlock(
    userId: _asInt(json['userId']),
    blockedByMe: json['blockedByMe'] == true,
    blockedMe: json['blockedMe'] == true,
  );

  final int userId;
  final bool blockedByMe;
  final bool blockedMe;
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
    this.joinApprovalRequired = false,
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
      joinApprovalRequired: json['joinApprovalRequired'] == true,
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
  final bool joinApprovalRequired;
  final String status;
  final List<FamilyGroupMember> members;
  bool get isActive => status == 'ACTIVE';
}

class ChatMessages {
  const ChatMessages({
    required this.conversation,
    required this.messages,
    this.hasMore = false,
    this.nextBeforeId,
  });

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
      hasMore: json['hasMore'] == true,
      nextBeforeId: json['nextBeforeId'] == null
          ? null
          : _asInt(json['nextBeforeId']),
    );
  }

  final ChatConversation conversation;
  final List<ChatMessage> messages;
  final bool hasMore;
  final int? nextBeforeId;
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

int? _profileShopId(Map<String, dynamic> json) {
  final nested = json['shopProfile'];
  final value = nested is Map ? nested['shopId'] : json['shopId'];
  return value == null ? null : _asInt(value);
}

String? _profileShopValue(Map<String, dynamic> json, String key) {
  final nested = json['shopProfile'];
  return (nested is Map ? nested[key] : json[key])?.toString();
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
