import 'dart:convert';

import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/pages/shop_share_page.dart';
import 'package:customer_frontend/services/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _StartConversationClient extends http.BaseClient {
  Uri? uri;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    uri = request.url;
    return http.StreamedResponse(
      Stream.value(
        utf8.encode(
          '{"code":0,"data":{"id":9,"type":"DIRECT","status":"ACTIVE","directUserId":10,"directUserNickname":"小周"}}',
        ),
      ),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

ChatConversation _direct(int id, int userId) => ChatConversation(
  id: id,
  type: 'DIRECT',
  status: 'ACTIVE',
  directUserId: userId,
  directUserNickname: '用户$userId',
);

ChatConversation _group(int id) => ChatConversation(
  id: id,
  type: 'FAMILY_GROUP',
  status: 'ACTIVE',
  name: '家庭群',
);

void main() {
  test('家庭店铺允许同家庭成员私聊和该店铺家庭群', () {
    final result = eligibleShopShareTargets(
      [_direct(1, 10), _direct(2, 20), _group(3), _group(4)],
      familyShop: true,
      familyConversationId: 3,
      familyMemberIds: {10},
    );

    expect(result.map((item) => item.id), [1, 3]);
  });

  test('公开店铺保留所有有效私聊且不误套家庭成员过滤', () {
    final result = eligibleShopShareTargets(
      [_direct(1, 10), _direct(2, 20), _group(3)],
      familyShop: false,
      familyConversationId: null,
      familyMemberIds: const {},
    );

    expect(result.map((item) => item.id), [1, 2]);
  });

  test('家庭成员没有既有私聊时仍生成可分享目标', () {
    final group = FamilyGroup(
      id: 3,
      shopId: 8,
      name: '家庭群',
      status: 'ACTIVE',
      members: const [
        FamilyGroupMember(
          userId: 1,
          nickname: '我',
          role: 'MEMBER',
          memberStatus: 'ACTIVE',
          muted: false,
        ),
        FamilyGroupMember(
          userId: 10,
          nickname: '小周',
          role: 'MEMBER',
          memberStatus: 'ACTIVE',
          muted: false,
        ),
        FamilyGroupMember(
          userId: 20,
          nickname: '待审核',
          role: 'MEMBER',
          memberStatus: 'PENDING',
          muted: false,
        ),
      ],
    );

    final result = buildShopShareTargets(
      [_group(3)],
      familyShop: true,
      familyGroup: group,
      currentUserId: 1,
    );

    expect(result.map((item) => item.title), ['家庭群', '小周']);
    expect(result.last.conversation, isNull);
    expect(result.last.userId, 10);
  });

  test('家庭成员已有私聊时复用原会话', () {
    final direct = _direct(1, 10);
    final group = FamilyGroup(
      id: 3,
      shopId: 8,
      name: '家庭群',
      status: 'ACTIVE',
      members: const [
        FamilyGroupMember(
          userId: 10,
          nickname: '小周',
          role: 'MEMBER',
          memberStatus: 'ACTIVE',
          muted: false,
        ),
      ],
    );

    final result = buildShopShareTargets(
      [direct, _group(3)],
      familyShop: true,
      familyGroup: group,
      currentUserId: 1,
    );

    expect(result.last.conversation, same(direct));
  });

  test('无既有私聊的成员在分享前自动创建会话', () async {
    final client = _StartConversationClient();
    final api = CustomerApi(
      ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
    );
    const target = ShopShareTarget(title: '小周', userId: 10);

    final conversation = await resolveShopShareConversation(api, target);

    expect(conversation.id, 9);
    expect(client.uri?.path, '/api/messages/direct/users/10');
  });
}
