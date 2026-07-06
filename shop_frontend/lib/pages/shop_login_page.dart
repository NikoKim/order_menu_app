import 'package:flutter/material.dart';

import '../services/shop_api_service.dart';
import '../widgets/app_footer.dart';

const _warmOrange = Color(0xFFF28C28);
const _lineColor = Color(0xFFF0E7DE);
const _textColor = Color(0xFF242424);
const _mutedColor = Color(0xFF77716B);

class ShopLoginPage extends StatefulWidget {
  const ShopLoginPage({super.key, this.onLoggedIn});

  final VoidCallback? onLoggedIn;

  @override
  State<ShopLoginPage> createState() => _ShopLoginPageState();
}

class _ShopLoginPageState extends State<ShopLoginPage> {
  final _accountController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;
  bool _submitting = false;

  @override
  void dispose() {
    _accountController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    final account = _accountController.text.trim();
    final password = _passwordController.text;
    if (account.isEmpty) {
      _showMessage('请输入商家账号');
      return;
    }
    if (password.isEmpty) {
      _showMessage('请输入密码');
      return;
    }

    setState(() => _submitting = true);
    try {
      await shopApi.login(nickname: account, password: password);
      if (!mounted) {
        return;
      }
      _showMessage('登录成功');
      widget.onLoggedIn?.call();
    } on ApiException catch (error) {
      if (mounted) {
        _showMessage(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          behavior: SnackBarBehavior.floating,
          backgroundColor: _warmOrange,
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const _LoginHeader(),
                  const SizedBox(height: 32),
                  _LoginField(
                    controller: _accountController,
                    label: '商家账号',
                    hintText: '请输入手机号或账号',
                    icon: Icons.storefront_outlined,
                  ),
                  const SizedBox(height: 16),
                  _LoginField(
                    controller: _passwordController,
                    label: '登录密码',
                    hintText: '请输入密码',
                    icon: Icons.lock_outline,
                    obscureText: _obscurePassword,
                    suffixIcon: IconButton(
                      tooltip: _obscurePassword ? '显示密码' : '隐藏密码',
                      onPressed: () {
                        setState(() => _obscurePassword = !_obscurePassword);
                      },
                      icon: Icon(
                        _obscurePassword
                            ? Icons.visibility_outlined
                            : Icons.visibility_off_outlined,
                        color: _mutedColor,
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
                    height: 48,
                    child: FilledButton(
                      onPressed: _submitting ? null : _handleLogin,
                      style: FilledButton.styleFrom(
                        backgroundColor: _warmOrange,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: _submitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text(
                              '登录',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    '使用后端商家账号登录，登录状态会保存在本机。',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: _mutedColor, fontSize: 13),
                  ),
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

class _LoginHeader extends StatelessWidget {
  const _LoginHeader();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            boxShadow: [
              BoxShadow(
                color: _warmOrange.withValues(alpha: 0.18),
                blurRadius: 18,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          clipBehavior: Clip.antiAlias,
          child: Image.asset('assets/app_icon.png', fit: BoxFit.cover),
        ),
        const SizedBox(height: 20),
        const Text(
          '商家端登录',
          style: TextStyle(
            color: _textColor,
            fontSize: 28,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          '登录后管理门店订单、菜品和经营信息',
          style: TextStyle(color: _mutedColor, fontSize: 14),
        ),
      ],
    );
  }
}

class _LoginField extends StatelessWidget {
  const _LoginField({
    required this.controller,
    required this.label,
    required this.hintText,
    required this.icon,
    this.obscureText = false,
    this.suffixIcon,
  });

  final TextEditingController controller;
  final String label;
  final String hintText;
  final IconData icon;
  final bool obscureText;
  final Widget? suffixIcon;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      obscureText: obscureText,
      decoration: InputDecoration(
        labelText: label,
        hintText: hintText,
        prefixIcon: Icon(icon, color: _warmOrange),
        suffixIcon: suffixIcon,
        filled: true,
        fillColor: const Color(0xFFFFFAF4),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _lineColor),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _lineColor),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _warmOrange, width: 1.5),
        ),
      ),
    );
  }
}
