import 'package:flutter/material.dart';

const momentReactionEmojis = <String>[
  '😀',
  '😁',
  '🤣',
  '😅',
  '😊',
  '😋',
  '😍',
  '🥰',
  '🤗',
  '😐',
  '😶',
  '🙄',
  '🥱',
  '😪',
  '😴',
  '😛',
  '🙃',
  '🙁',
  '😤',
  '😭',
  '😨',
  '😱',
  '🤪',
  '😵',
  '🥴',
  '😡',
  '😇',
  '🥳',
  '😷',
  '🤓',
  '🎈',
  '🎯',
  '🏆',
  '📢',
  '🩹',
  '💣',
  '🔍',
  '⏰',
];

Future<String?> showMomentReactionPicker(
  BuildContext context, {
  required Map<String, int> counts,
  required Set<String> selected,
}) => showModalBottomSheet<String>(
  context: context,
  showDragHandle: true,
  backgroundColor: Colors.white,
  builder: (sheetContext) => SafeArea(
    child: SizedBox(
      height: 390,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(18, 0, 18, 12),
            child: Text(
              '选择回应',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
          ),
          Expanded(
            child: GridView.builder(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 16),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 6,
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: .88,
              ),
              itemCount: momentReactionEmojis.length,
              itemBuilder: (_, index) {
                final emoji = momentReactionEmojis[index];
                final active = selected.contains(emoji);
                return Material(
                  color: active
                      ? const Color(0xFFFFE2B8)
                      : const Color(0xFFF5F6F7),
                  borderRadius: BorderRadius.circular(8),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(8),
                    onTap: () => Navigator.pop(sheetContext, emoji),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(emoji, style: const TextStyle(fontSize: 25)),
                        const SizedBox(height: 2),
                        Text(
                          '${counts[emoji] ?? 0}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: active
                                ? const Color(0xFFB75E0A)
                                : const Color(0xFF73777D),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    ),
  ),
);
