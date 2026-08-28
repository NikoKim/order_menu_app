import 'package:customer_frontend/models.dart';
import 'package:customer_frontend/moment_models.dart';
import 'package:customer_frontend/widgets/moment_mention.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('moment parses quote, mentions, unread counters and share counters', () {
    final moment = Moment.fromJson({
      'id': 12,
      'title': '引用测试',
      'content': '@小李 看这里',
      'visibility': 'PUBLIC',
      'rootCommentCount': 2,
      'commentCount': 5,
      'shareCount': 9,
      'appShareCount': 4,
      'externalShareCount': 5,
      'mentions': [
        {'id': 7, 'nickname': '小李', 'role': 'CUSTOMER'},
      ],
      'quote': {
        'type': 'COMMENT',
        'momentId': 3,
        'commentId': 8,
        'momentTitle': '来源动态',
        'content': '引用的评论',
        'createdAt': '2026-08-24T10:00:00+08:00',
        'available': false,
        'unavailableReason': '无权查看',
        'author': {'id': 9, 'nickname': '评论者'},
      },
    });

    expect(moment.rootCommentCount, 2);
    expect(moment.shareCount, 9);
    expect(moment.appShareCount, 4);
    expect(moment.externalShareCount, 5);
    expect(moment.mentions.single.id, 7);
    expect(moment.quote?.type, 'COMMENT');
    expect(moment.quote?.commentId, 8);
    expect(moment.quote?.available, isFalse);
    expect(moment.quote?.unavailableReason, '无权查看');
  });

  test('comment keeps all six images and mention metadata', () {
    final comment = MomentComment.fromJson({
      'id': 5,
      'content': '@店主 多图评论',
      'createdAt': '2026-08-24T10:00:00+08:00',
      'imageUrls': List.generate(6, (index) => '/images/$index.jpg'),
      'mentions': [
        {'id': 4, 'nickname': '店主', 'role': 'SHOP'},
      ],
    });

    expect(comment.imageUrls, hasLength(6));
    expect(comment.imageUrl, '/images/0.jpg');
    expect(comment.mentions.single.merchant, isTrue);
  });

  test('active mention ids de-duplicate and follow text deletion', () {
    const users = [
      MomentUser(id: 1, nickname: '小李'),
      MomentUser(id: 2, nickname: '店主', merchant: true),
      MomentUser(id: 1, nickname: '小李'),
    ];
    expect(activeMomentMentionIds('@小李 @店主 @小李', users), [1, 2]);
    expect(activeMomentMentionIds('只留下 @店主', users), [2]);
  });

  test('notification preserves structured payload for routing', () {
    final notification = UserNotification.fromJson({
      'id': 1,
      'userId': 2,
      'type': 'MOMENT_COMMENT_MENTIONED',
      'title': '有人提及你',
      'content': '服务端完整文案',
      'createdAt': '2026-08-24T10:00:00+08:00',
      'payload': {'momentId': 10, 'commentId': 11},
    });

    expect(notification.payloadData['momentId'], 10);
    expect(notification.payloadData['commentId'], 11);
    expect(notification.content, '服务端完整文案');
  });
}
