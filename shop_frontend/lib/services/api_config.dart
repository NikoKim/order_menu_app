class ApiConfig {
  const ApiConfig._();

  static const String baseUrl = String.fromEnvironment(
    'SHOP_API_BASE_URL',
    defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
  );

  static const String wsBaseUrl = String.fromEnvironment(
    'SHOP_WS_BASE_URL',
    defaultValue: 'wss://order-menu-backend.zsj-unknown.top/ws',
  );

  static String resolveImageUrl(String url) {
    final trimmed = url.trim();
    final uri = Uri.tryParse(trimmed);
    if (uri != null && (uri.scheme == 'http' || uri.scheme == 'https')) {
      return trimmed;
    }
    final api = Uri.parse(baseUrl);
    final relative = Uri.tryParse(trimmed);
    final path = relative?.path ?? trimmed;
    return api.replace(
      path: path.startsWith('/') ? path : '/$path',
      query: relative?.hasQuery == true ? relative!.query : null,
    ).toString();
  }
}
