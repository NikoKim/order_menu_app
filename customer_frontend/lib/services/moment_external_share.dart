import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../main.dart';
import '../moment_models.dart';
import '../widgets/app_notice.dart';
import 'api_client.dart';
import 'moment_api.dart';

enum MomentExternalShareMode { browse, isolated }

Future<void> shareMomentExternally(
  BuildContext context,
  Moment moment, {
  int? commentId,
  MomentExternalShareMode? selectedMode,
}) async {
  final mode =
      selectedMode ??
      await showModalBottomSheet<MomentExternalShareMode>(
        context: context,
        showDragHandle: true,
        builder: (sheetContext) => SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const ListTile(
                title: Text(
                  '分享到其他应用',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.view_stream_outlined),
                title: const Text('可继续浏览动态列表'),
                onTap: () =>
                    Navigator.pop(sheetContext, MomentExternalShareMode.browse),
              ),
              ListTile(
                leading: const Icon(Icons.article_outlined),
                title: const Text('仅查看当前动态'),
                onTap: () => Navigator.pop(
                  sheetContext,
                  MomentExternalShareMode.isolated,
                ),
              ),
            ],
          ),
        ),
      );
  if (mode == null || !context.mounted) return;
  final query = <String>[
    if (commentId != null) 'commentId=$commentId',
    'mode=${mode == MomentExternalShareMode.isolated ? 'isolated' : 'browse'}',
  ].join('&');
  final url =
      'https://order-menu-backend.zsj-unknown.top/api/moments/${moment.id}/share-card?$query';
  final result = await SharePlus.instance.share(
    ShareParams(
      subject: moment.title.isEmpty ? '动态分享' : moment.title,
      text: '${moment.title}\n$url',
    ),
  );
  if (result.status != ShareResultStatus.success || !context.mounted) return;
  try {
    await CustomerApi(
      AppScope.of(context).api,
    ).reportExternalMomentShare(moment.id, commentId: commentId);
  } on ApiException catch (error) {
    if (context.mounted) AppNotice.show(context, error.message);
  }
}
