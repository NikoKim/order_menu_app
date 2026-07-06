import 'package:shared_preferences/shared_preferences.dart';

class AuthSession {
  AuthSession._();

  static const _tokenKey = 'shop_auth_token';
  static const _nicknameKey = 'shop_user_nickname';

  static Future<String?> loadToken() async {
    final preferences = await SharedPreferences.getInstance();
    return preferences.getString(_tokenKey);
  }

  static Future<String?> loadNickname() async {
    final preferences = await SharedPreferences.getInstance();
    return preferences.getString(_nicknameKey);
  }

  static Future<void> save({required String token, String? nickname}) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_tokenKey, token);
    if (nickname != null && nickname.isNotEmpty) {
      await preferences.setString(_nicknameKey, nickname);
    }
  }

  static Future<void> clear() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.remove(_tokenKey);
    await preferences.remove(_nicknameKey);
  }
}
