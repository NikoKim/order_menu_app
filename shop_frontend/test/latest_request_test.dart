import 'package:flutter_test/flutter_test.dart';
import 'package:shop_frontend/services/latest_request.dart';

void main() {
  test('only the newest request generation can update state', () {
    final requests = LatestRequest();
    final older = requests.begin();
    final newer = requests.begin();

    expect(requests.isLatest(older), isFalse);
    expect(requests.isLatest(newer), isTrue);
  });
}
