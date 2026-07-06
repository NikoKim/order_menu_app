class ApiConfig {
  const ApiConfig._();

  static const String baseUrl = String.fromEnvironment(
    'SHOP_API_BASE_URL',
    defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
  );
}
