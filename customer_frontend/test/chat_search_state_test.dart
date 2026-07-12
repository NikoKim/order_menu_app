import 'package:customer_frontend/pages/chat_list_page.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('conversation list is hidden while a search query is active', () {
    expect(shouldShowConversationsForSearch(''), isTrue);
    expect(shouldShowConversationsForSearch('   '), isTrue);
    expect(shouldShowConversationsForSearch('小明'), isFalse);
  });

  test('empty completed search exposes the no-user state', () {
    expect(
      shouldShowNoChatUser(query: '小明', searching: false, resultCount: 0),
      isTrue,
    );
    expect(
      shouldShowNoChatUser(query: '小明', searching: true, resultCount: 0),
      isFalse,
    );
    expect(
      shouldShowNoChatUser(query: '', searching: false, resultCount: 0),
      isFalse,
    );
  });
}
