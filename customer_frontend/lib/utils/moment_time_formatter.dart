const _chinaOffset = Duration(hours: 8);

DateTime? parseMomentPublishedInstant(String value) {
  final chinaWallClock = _parseMomentTime(value);
  return chinaWallClock?.subtract(_chinaOffset);
}

bool momentPublishedInWindow(
  String value, {
  required DateTime after,
  required DateTime through,
}) {
  final publishedAt = parseMomentPublishedInstant(value);
  if (publishedAt == null) return false;
  final start = after.toUtc();
  final end = through.toUtc();
  return publishedAt.isAfter(start) && !publishedAt.isAfter(end);
}

String formatMomentTime(String value, {DateTime? now}) {
  final current = _asChinaWallClock(now ?? DateTime.now());
  final parsed = _parseMomentTime(value);
  if (parsed == null) return value;

  var difference = current.difference(parsed);
  if (difference.isNegative) {
    if (-difference <= const Duration(minutes: 5)) {
      difference = Duration.zero;
    } else {
      return _formatAbsolute(parsed, current);
    }
  }
  if (difference < const Duration(minutes: 1)) return '刚刚';
  if (difference < const Duration(hours: 1)) {
    return '${difference.inMinutes}分钟前';
  }
  if (difference < const Duration(hours: 24)) {
    return '${difference.inHours}小时前';
  }

  final today = DateTime.utc(current.year, current.month, current.day);
  final postedDay = DateTime.utc(parsed.year, parsed.month, parsed.day);
  if (postedDay == today.subtract(const Duration(days: 1))) {
    return '昨天 ${_clock(parsed)}';
  }
  return _formatAbsolute(parsed, current);
}

bool shouldShowMomentEdited({
  required String? editedAt,
  required String? publishedAt,
  required String createdAt,
  required String status,
}) {
  if (editedAt?.isNotEmpty != true || status != 'PUBLISHED') return false;
  final edited = _parseMomentTime(editedAt!);
  final published = _parseMomentTime(publishedAt ?? createdAt);
  if (edited == null) return false;
  if (published == null) return true;

  // Older clients performed a second update while finishing image uploads.
  return edited.isAfter(published.add(const Duration(seconds: 30)));
}

DateTime? _parseMomentTime(String value) {
  final raw = value.trim();
  if (raw.isEmpty) return null;

  final epoch = int.tryParse(raw);
  if (epoch != null && (raw.length == 10 || raw.length == 13)) {
    final milliseconds = raw.length == 10 ? epoch * 1000 : epoch;
    return _asChinaWallClock(
      DateTime.fromMillisecondsSinceEpoch(milliseconds, isUtc: true),
    );
  }

  final normalized = raw.replaceFirst(' ', 'T');
  final hasZone = RegExp(
    r'(?:Z|[+-]\d{2}:?\d{2})$',
    caseSensitive: false,
  ).hasMatch(normalized);
  if (hasZone) {
    final instant = DateTime.tryParse(normalized);
    return instant == null ? null : _asChinaWallClock(instant);
  }

  // Moment DTOs historically used LocalDateTime, so zone-less values are
  // server wall-clock values in Asia/Shanghai, not the phone's local zone.
  final match = RegExp(
    r'^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?',
  ).firstMatch(raw);
  if (match == null) return null;
  return DateTime.utc(
    int.parse(match.group(1)!),
    int.parse(match.group(2)!),
    int.parse(match.group(3)!),
    int.tryParse(match.group(4) ?? '') ?? 0,
    int.tryParse(match.group(5) ?? '') ?? 0,
    int.tryParse(match.group(6) ?? '') ?? 0,
  );
}

DateTime _asChinaWallClock(DateTime instant) {
  final china = instant.toUtc().add(_chinaOffset);
  return DateTime.utc(
    china.year,
    china.month,
    china.day,
    china.hour,
    china.minute,
    china.second,
    china.millisecond,
    china.microsecond,
  );
}

String _formatAbsolute(DateTime value, DateTime current) {
  final monthDay = '${_twoDigits(value.month)}-${_twoDigits(value.day)}';
  final date = value.year == current.year
      ? monthDay
      : '${value.year}-$monthDay';
  return '$date ${_clock(value)}';
}

String _clock(DateTime value) =>
    '${_twoDigits(value.hour)}:${_twoDigits(value.minute)}';

String _twoDigits(int value) => value.toString().padLeft(2, '0');
