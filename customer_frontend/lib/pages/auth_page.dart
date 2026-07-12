import 'package:flutter/material.dart';

import '../main.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';
import '../widgets/app_notice.dart';

class AuthPage extends StatefulWidget {
  const AuthPage({super.key, this.onLoggedIn});

  final VoidCallback? onLoggedIn;

  @override
  State<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends State<AuthPage> {
  final _loginFormKey = GlobalKey<FormState>();
  final _registerFormKey = GlobalKey<FormState>();
  final _loginNameController = TextEditingController();
  final _loginPasswordController = TextEditingController();
  final _registerNameController = TextEditingController();
  final _registerPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  static const _warmOrange = Color(0xFFF28C28);
  static const _softOrange = Color(0xFFFFF3E5);
  static const _textDark = Color(0xFF242424);
  static const _textMuted = Color(0xFF7A6A5C);

  @override
  void dispose() {
    _loginNameController.dispose();
    _loginPasswordController.dispose();
    _registerNameController.dispose();
    _registerPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = AppScope.of(context).auth;
    return AnimatedBuilder(
      animation: auth,
      builder: (context, _) {
        if (auth.isLoggedIn) {
          return _LoggedInPanel(auth: auth);
        }
        return DefaultTabController(
          length: 2,
          child: Scaffold(
            backgroundColor: Colors.white,
            appBar: AppBar(
              title: const Text('登录'),
              bottom: PreferredSize(
                preferredSize: const Size.fromHeight(56),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: Container(
                    height: 44,
                    decoration: BoxDecoration(
                      color: _softOrange,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: TabBar(
                      indicatorSize: TabBarIndicatorSize.tab,
                      dividerColor: Colors.transparent,
                      labelColor: Colors.white,
                      unselectedLabelColor: _textMuted,
                      indicator: BoxDecoration(
                        color: _warmOrange,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      tabs: const [
                        Tab(text: '登录'),
                        Tab(text: '注册'),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            body: TabBarView(
              children: [
                _AuthFormPanel(
                  title: '欢迎回来',
                  subtitle: '用昵称和密码继续点餐',
                  formKey: _loginFormKey,
                  children: [
                    _AuthTextField(
                      controller: _loginNameController,
                      labelText: '昵称',
                      icon: Icons.person_outline,
                      validator: _requiredValidator,
                    ),
                    const SizedBox(height: 14),
                    _AuthTextField(
                      controller: _loginPasswordController,
                      labelText: '密码',
                      icon: Icons.lock_outline,
                      obscureText: true,
                      validator: _requiredValidator,
                    ),
                    const SizedBox(height: 24),
                    _PrimaryAuthButton(
                      text: '登录',
                      busy: auth.isBusy,
                      onPressed: _handleLogin,
                    ),
                  ],
                ),
                _AuthFormPanel(
                  title: '创建账号',
                  subtitle: '注册后即可保存你的点餐记录',
                  formKey: _registerFormKey,
                  children: [
                    _AuthTextField(
                      controller: _registerNameController,
                      labelText: '昵称',
                      icon: Icons.person_add_alt_1_outlined,
                      validator: _requiredValidator,
                    ),
                    const SizedBox(height: 14),
                    _AuthTextField(
                      controller: _registerPasswordController,
                      labelText: '密码',
                      icon: Icons.lock_outline,
                      obscureText: true,
                      validator: _passwordValidator,
                    ),
                    const SizedBox(height: 14),
                    _AuthTextField(
                      controller: _confirmPasswordController,
                      labelText: '确认密码',
                      icon: Icons.lock_reset_outlined,
                      obscureText: true,
                      validator: _confirmPasswordValidator,
                    ),
                    const SizedBox(height: 24),
                    _PrimaryAuthButton(
                      text: '注册并登录',
                      busy: auth.isBusy,
                      onPressed: _handleRegister,
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  String? _requiredValidator(String? value) {
    if (value == null || value.trim().isEmpty) {
      return '请填写此项';
    }
    return null;
  }

  String? _passwordValidator(String? value) {
    final requiredMessage = _requiredValidator(value);
    if (requiredMessage != null) return requiredMessage;
    if (value!.length < 6) return '密码至少 6 位';
    return null;
  }

  String? _confirmPasswordValidator(String? value) {
    final requiredMessage = _passwordValidator(value);
    if (requiredMessage != null) {
      return requiredMessage;
    }
    if (value != _registerPasswordController.text) {
      return '两次密码不一致';
    }
    return null;
  }

  Future<void> _handleLogin() async {
    if (!(_loginFormKey.currentState?.validate() ?? false)) return;
    final auth = AppScope.of(context).auth;
    try {
      await auth.login(
        _loginNameController.text.trim(),
        _loginPasswordController.text,
      );
      _afterAuth('登录成功');
    } catch (error) {
      _showNotice(error.toString());
    }
  }

  Future<void> _handleRegister() async {
    if (!(_registerFormKey.currentState?.validate() ?? false)) return;
    final auth = AppScope.of(context).auth;
    try {
      await auth.register(
        _registerNameController.text.trim(),
        _registerPasswordController.text,
      );
      _afterAuth('注册成功');
    } catch (error) {
      _showNotice(error.toString());
    }
  }

  void _afterAuth(String message) {
    _showNotice(message);
    widget.onLoggedIn?.call();
    if (Navigator.of(context).canPop()) {
      Navigator.of(context).pop();
    }
  }

  void _showNotice(String message) {
    if (!mounted) return;
    AppNotice.show(context, message);
  }
}

class _LoggedInPanel extends StatelessWidget {
  const _LoggedInPanel({required this.auth});

  final AuthController auth;

  @override
  Widget build(BuildContext context) {
    final user = auth.user;
    return Scaffold(
      appBar: AppBar(title: const Text('账号')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 72,
                height: 72,
                decoration: const BoxDecoration(
                  color: _AuthPageState._softOrange,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.person,
                  color: _AuthPageState._warmOrange,
                  size: 40,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                user?.nickname ?? '已登录用户',
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                '当前已登录顾客账号',
                style: TextStyle(color: _AuthPageState._textMuted),
              ),
              const SizedBox(height: 24),
              FilledButton.tonalIcon(
                onPressed: auth.logout,
                icon: const Icon(Icons.logout),
                label: const Text('退出登录'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AuthFormPanel extends StatelessWidget {
  const _AuthFormPanel({
    required this.title,
    required this.subtitle,
    required this.formKey,
    required this.children,
  });

  final String title;
  final String subtitle;
  final GlobalKey<FormState> formKey;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Form(
              key: formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    width: 64,
                    height: 64,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(8),
                      boxShadow: [
                        BoxShadow(
                          color: _AuthPageState._warmOrange.withValues(
                            alpha: 0.18,
                          ),
                          blurRadius: 18,
                          offset: const Offset(0, 8),
                        ),
                      ],
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: Image.asset(
                      'assets/app_icon.png',
                      fit: BoxFit.cover,
                    ),
                  ),
                  const SizedBox(height: 24),
                  Text(
                    title,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      color: _AuthPageState._textDark,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    subtitle,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: _AuthPageState._textMuted,
                    ),
                  ),
                  const SizedBox(height: 28),
                  ...children,
                  const AppFooter(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AuthTextField extends StatelessWidget {
  const _AuthTextField({
    required this.controller,
    required this.labelText,
    required this.icon,
    required this.validator,
    this.obscureText = false,
  });

  final TextEditingController controller;
  final String labelText;
  final IconData icon;
  final FormFieldValidator<String> validator;
  final bool obscureText;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      obscureText: obscureText,
      validator: validator,
      decoration: InputDecoration(labelText: labelText, prefixIcon: Icon(icon)),
    );
  }
}

class _PrimaryAuthButton extends StatelessWidget {
  const _PrimaryAuthButton({
    required this.text,
    required this.onPressed,
    required this.busy,
  });

  final String text;
  final VoidCallback onPressed;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: FilledButton(
        onPressed: busy ? null : onPressed,
        child: busy
            ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Text(text),
      ),
    );
  }
}
