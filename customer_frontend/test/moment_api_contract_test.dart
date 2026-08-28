import 'dart:convert';

import 'package:customer_frontend/services/api_client.dart';
import 'package:customer_frontend/services/moment_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

class _MomentContractClient extends http.BaseClient {
  final requests = <http.BaseRequest>[];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    final path = request.url.path;
    final data = switch (path) {
      '/api/moments/hot' => [
        {'id': 1, 'title': '热门', 'content': '内容', 'visibility': 'PUBLIC'},
      ],
      '/api/moments/1/comments' => {
        'id': 9,
        'content': '评论',
        'createdAt': '2026-08-24T10:00:00+08:00',
      },
      '/api/moments/1/external-shares' => {
        'shareCount': 3,
        'appShareCount': 1,
        'externalShareCount': 2,
      },
      _ => {'id': 1, 'title': '发布', 'content': '正文', 'visibility': 'PUBLIC'},
    };
    return http.StreamedResponse(
      Stream.value(utf8.encode(jsonEncode({'code': 0, 'data': data}))),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

Map<String, dynamic> _bodyOf(http.BaseRequest request) =>
    jsonDecode((request as http.Request).body) as Map<String, dynamic>;

void main() {
  test(
    'moment API sends quote, mentions, multi-image and external-share contracts',
    () async {
      final client = _MomentContractClient();
      final api = CustomerApi(
        ApiClient(httpClient: client, baseUrl: 'https://example.test/api'),
      );

      final hot = await api.hotMoments();
      await api.publishMoment(
        title: '标题',
        content: '@用户 正文',
        visibility: 'PUBLIC',
        quotedCommentId: 6,
        mentionedUserIds: const [4, 5],
      );
      await api.addMomentComment(
        1,
        content: '@用户 评论',
        imageUrls: List.generate(6, (index) => '/image/$index.jpg'),
        mentionedUserIds: const [4],
      );
      final stats = await api.reportExternalMomentShare(1, commentId: 9);

      expect(hot.single.title, '热门');
      expect(client.requests.first.url.queryParameters['limit'], '10');
      final publishBody = _bodyOf(client.requests[1]);
      expect(publishBody['quotedCommentId'], 6);
      expect(publishBody['mentionedUserIds'], [4, 5]);
      final commentBody = _bodyOf(client.requests[2]);
      expect(commentBody['imageUrls'], hasLength(6));
      expect(commentBody['mentionedUserIds'], [4]);
      expect(_bodyOf(client.requests[3])['commentId'], 9);
      expect(stats['externalShareCount'], 2);
    },
  );
}
