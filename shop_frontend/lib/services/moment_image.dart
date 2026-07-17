import 'dart:io';

import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path_provider/path_provider.dart';

Future<String> prepareMomentImage(String path, {required bool comment}) async {
  final limit = (comment ? 5 : 10) * 1024 * 1024;
  final source = File(path);
  if (!await source.exists()) throw StateError('图片文件不存在');
  if (await source.length() < limit) return path;

  final directory = await getTemporaryDirectory();
  const attempts = <(int, int)>[(78, 2400), (62, 1920), (46, 1600), (32, 1280)];
  for (var index = 0; index < attempts.length; index++) {
    final (quality, dimension) = attempts[index];
    final target =
        '${directory.path}${Platform.pathSeparator}'
        'moment_${comment ? 'comment' : 'post'}_${DateTime.now().microsecondsSinceEpoch}_$index.jpg';
    final compressed = await FlutterImageCompress.compressAndGetFile(
      path,
      target,
      quality: quality,
      minWidth: dimension,
      minHeight: dimension,
      keepExif: false,
    );
    if (compressed != null && await compressed.length() < limit)
      return compressed.path;
  }
  throw StateError('图片压缩后仍超过限制');
}
