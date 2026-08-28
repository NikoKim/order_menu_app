import 'dart:io';

import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path_provider/path_provider.dart';

import 'api_client.dart';

Future<String> prepareMomentImage(
  String sourcePath, {
  required bool comment,
  int rotationDegrees = 0,
}) async {
  final limit = comment ? 5 * 1024 * 1024 : 10 * 1024 * 1024;
  final source = File(sourcePath);
  if (!await source.exists()) throw const ApiException('所选图片不存在');

  final directory = await getTemporaryDirectory();
  final normalizedRotation = ((rotationDegrees % 360) + 360) % 360;
  final normalizedTarget =
      '${directory.path}/moment_normalized_${DateTime.now().microsecondsSinceEpoch}.jpg';
  final normalized = await FlutterImageCompress.compressAndGetFile(
    sourcePath,
    normalizedTarget,
    quality: 94,
    minWidth: 1,
    minHeight: 1,
    rotate: normalizedRotation,
    autoCorrectionAngle: true,
    format: CompressFormat.jpeg,
    keepExif: false,
  );
  if (normalized == null) throw const ApiException('图片方向处理失败，请重新选择');
  if (await File(normalized.path).length() < limit) return normalized.path;

  final qualities = comment ? const [86, 72, 58, 44] : const [90, 78, 66, 52];
  XFile? result;
  for (var i = 0; i < qualities.length; i++) {
    final target =
        '${directory.path}/moment_${DateTime.now().microsecondsSinceEpoch}_$i.jpg';
    result = await FlutterImageCompress.compressAndGetFile(
      normalized.path,
      target,
      quality: qualities[i],
      minWidth: i < 2 ? 2048 : 1440,
      minHeight: i < 2 ? 2048 : 1440,
      autoCorrectionAngle: false,
      format: CompressFormat.jpeg,
      keepExif: false,
    );
    if (result != null && await File(result.path).length() < limit)
      return result.path;
  }
  throw ApiException(
    comment ? '评论图片压缩后仍超过5MB，请选择更小的图片' : '动态图片压缩后仍超过10MB，请选择更小的图片',
  );
}
