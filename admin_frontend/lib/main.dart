import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const AdminApp());
}

const warmOrange = Color(0xFFF28C28);
const softOrange = Color(0xFFFFF4E8);
const lineColor = Color(0xFFF0E7DE);
const textColor = Color(0xFF242424);
const mutedColor = Color(0xFF77716B);

class AdminApp extends StatelessWidget {
  const AdminApp({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(
      seedColor: warmOrange,
      primary: warmOrange,
      secondary: const Color(0xFFFFB15C),
      surface: Colors.white,
    );

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '管理员端',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: Colors.white,
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: textColor,
          surfaceTintColor: Colors.white,
          elevation: 0,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            backgroundColor: warmOrange,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFFFFFAF4),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: lineColor),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: lineColor),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: warmOrange, width: 1.5),
          ),
        ),
      ),
      home: const AdminShell(),
    );
  }
}

class AdminShell extends StatefulWidget {
  const AdminShell({super.key});

  @override
  State<AdminShell> createState() => _AdminShellState();
}

class _AdminShellState extends State<AdminShell> {
  final AdminApi _api = AdminApi();
  bool _loading = true;
  bool _loggedIn = false;

  @override
  void initState() {
    super.initState();
    _restore();
  }

  Future<void> _restore() async {
    await _api.loadSavedToken();
    if (mounted) {
      setState(() {
        _loggedIn = _api.token != null && _api.token!.isNotEmpty;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!_loggedIn) {
      return AdminLoginPage(
        api: _api,
        onLoggedIn: () => setState(() => _loggedIn = true),
      );
    }
    return AdminManagePage(
      api: _api,
      onLogout: () async {
        await _api.logout();
        if (mounted) {
          setState(() => _loggedIn = false);
        }
      },
    );
  }
}

class AdminLoginPage extends StatefulWidget {
  const AdminLoginPage({
    super.key,
    required this.api,
    required this.onLoggedIn,
  });

  final AdminApi api;
  final VoidCallback onLoggedIn;

  @override
  State<AdminLoginPage> createState() => _AdminLoginPageState();
}

class _AdminLoginPageState extends State<AdminLoginPage> {
  final _accountController = TextEditingController(text: 'nikokim');
  final _passwordController = TextEditingController();
  bool _obscure = true;
  bool _submitting = false;

  @override
  void dispose() {
    _accountController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final account = _accountController.text.trim();
    final password = _passwordController.text;
    if (account.isEmpty || password.isEmpty) {
      _message('请输入账号和密码');
      return;
    }
    setState(() => _submitting = true);
    try {
      await widget.api.login(account, password);
      if (mounted) {
        widget.onLoggedIn();
      }
    } on ApiException catch (error) {
      if (mounted) {
        _message(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  void _message(String text) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(text), behavior: SnackBarBehavior.floating),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Align(
                    alignment: Alignment.center,
                    child: Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(16),
                        boxShadow: [
                          BoxShadow(
                            color: warmOrange.withValues(alpha: 0.18),
                            blurRadius: 20,
                            offset: const Offset(0, 10),
                          ),
                        ],
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: Image.asset(
                        'assets/app_icon.png',
                        fit: BoxFit.cover,
                      ),
                    ),
                  ),
                  const SizedBox(height: 18),
                  const Text(
                    '管理员登录',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 28),
                  TextField(
                    controller: _accountController,
                    decoration: const InputDecoration(
                      labelText: '管理员账号',
                      prefixIcon: Icon(Icons.person_outline),
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _passwordController,
                    obscureText: _obscure,
                    decoration: InputDecoration(
                      labelText: '密码',
                      prefixIcon: const Icon(Icons.lock_outline),
                      suffixIcon: IconButton(
                        tooltip: _obscure ? '显示密码' : '隐藏密码',
                        onPressed: () => setState(() => _obscure = !_obscure),
                        icon: Icon(
                          _obscure
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 22),
                  SizedBox(
                    height: 48,
                    child: FilledButton.icon(
                      onPressed: _submitting ? null : _login,
                      icon: _submitting
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.login),
                      label: const Text('登录'),
                    ),
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

class AdminManagePage extends StatefulWidget {
  const AdminManagePage({super.key, required this.api, required this.onLogout});

  final AdminApi api;
  final Future<void> Function() onLogout;

  @override
  State<AdminManagePage> createState() => _AdminManagePageState();
}

class _AdminManagePageState extends State<AdminManagePage> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final role = _index == 0 ? 'CUSTOMER' : 'SHOP';
    return Scaffold(
      appBar: AppBar(
        title: const Text('账号管理'),
        actions: [
          IconButton(
            tooltip: '退出登录',
            onPressed: widget.onLogout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: SafeArea(
        child: AccountList(api: widget.api, role: role),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        indicatorColor: const Color(0xFFFFE3C4),
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people),
            label: '顾客',
          ),
          NavigationDestination(
            icon: Icon(Icons.storefront_outlined),
            selectedIcon: Icon(Icons.storefront),
            label: '商家',
          ),
        ],
      ),
    );
  }
}

class AccountList extends StatefulWidget {
  const AccountList({super.key, required this.api, required this.role});

  final AdminApi api;
  final String role;

  @override
  State<AccountList> createState() => _AccountListState();
}

class _AccountListState extends State<AccountList> {
  final _keywordController = TextEditingController();
  Timer? _refreshTimer;
  bool _loading = false;
  String? _error;
  List<UserAccount> _items = [];

  @override
  void initState() {
    super.initState();
    _load();
    _refreshTimer = Timer.periodic(const Duration(seconds: 15), (_) {
      if (mounted) {
        _load(silent: true);
      }
    });
  }

  @override
  void didUpdateWidget(covariant AccountList oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.role != widget.role) {
      _load();
    }
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _keywordController.dispose();
    super.dispose();
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final items = await widget.api.fetchUsers(
        role: widget.role,
        keyword: _keywordController.text.trim(),
      );
      if (mounted) {
        setState(() {
          _items = items;
          _error = null;
        });
      }
    } on ApiException catch (error) {
      if (mounted && !silent) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted && !silent) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _setStatus(UserAccount user, int status) async {
    await widget.api.updateStatus(user.id, status);
    await _load();
  }

  Future<void> _create() async {
    final created = await showDialog<bool>(
      context: context,
      builder: (_) => CreateAccountDialog(api: widget.api, role: widget.role),
    );
    if (created == true) {
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.role == 'CUSTOMER' ? '顾客账号信息管理' : '商家账号信息管理';
    return RefreshIndicator(
      color: warmOrange,
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              IconButton.filled(
                tooltip: '创建账号',
                onPressed: _create,
                icon: const Icon(Icons.add),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _keywordController,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _load(),
            decoration: InputDecoration(
              hintText: '搜索昵称或电话',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: IconButton(
                tooltip: '搜索',
                onPressed: _load,
                icon: const Icon(Icons.arrow_forward),
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (_loading) const LinearProgressIndicator(color: warmOrange),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(
                _error!,
                style: const TextStyle(color: Colors.redAccent),
              ),
            ),
          for (final user in _items)
            _AccountTile(user: user, onStatus: _setStatus),
          if (!_loading && _items.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 42),
              child: Center(
                child: Text('暂无账号', style: TextStyle(color: mutedColor)),
              ),
            ),
          const AppFooter(),
        ],
      ),
    );
  }
}

class _AccountTile extends StatelessWidget {
  const _AccountTile({required this.user, required this.onStatus});

  final UserAccount user;
  final Future<void> Function(UserAccount user, int status) onStatus;

  @override
  Widget build(BuildContext context) {
    final enabled = user.status == 1;
    return Card(
      margin: const EdgeInsets.only(top: 12),
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: lineColor),
      ),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: enabled ? softOrange : const Color(0xFFF1F1F1),
          child: Icon(
            enabled ? Icons.person : Icons.block,
            color: enabled ? warmOrange : mutedColor,
          ),
        ),
        title: Text(
          user.nickname,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        subtitle: Text('${user.roleText} · ${enabled ? '正常' : '已封禁'}'),
        trailing: FilledButton.tonalIcon(
          onPressed: () => onStatus(user, enabled ? 0 : 1),
          icon: Icon(enabled ? Icons.block : Icons.check_circle_outline),
          label: Text(enabled ? '封禁' : '解封'),
        ),
      ),
    );
  }
}

class CreateAccountDialog extends StatefulWidget {
  const CreateAccountDialog({super.key, required this.api, required this.role});

  final AdminApi api;
  final String role;

  @override
  State<CreateAccountDialog> createState() => _CreateAccountDialogState();
}

class _CreateAccountDialogState extends State<CreateAccountDialog> {
  final _nicknameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _shopNameController = TextEditingController();
  final _phoneController = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _nicknameController.dispose();
    _passwordController.dispose();
    _shopNameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final nickname = _nicknameController.text.trim();
    final password = _passwordController.text;
    if (nickname.isEmpty || password.isEmpty) {
      return;
    }
    setState(() => _saving = true);
    try {
      await widget.api.createUser(
        nickname: nickname,
        password: password,
        role: widget.role,
        shopName: _shopNameController.text.trim(),
        phone: _phoneController.text.trim(),
      );
      if (mounted) {
        Navigator.of(context).pop(true);
      }
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.message),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isShop = widget.role == 'SHOP';
    return AlertDialog(
      title: Text(isShop ? '创建商家账号' : '创建顾客账号'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _nicknameController,
              decoration: const InputDecoration(labelText: '昵称'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passwordController,
              obscureText: true,
              decoration: const InputDecoration(labelText: '密码'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _phoneController,
              decoration: const InputDecoration(labelText: '电话'),
            ),
            if (isShop) ...[
              const SizedBox(height: 12),
              TextField(
                controller: _shopNameController,
                decoration: const InputDecoration(labelText: '店铺名称'),
              ),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(false),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: const Text('创建'),
        ),
      ],
    );
  }
}

class AppFooter extends StatelessWidget {
  const AppFooter({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 18),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Wrap(
              alignment: WrapAlignment.center,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const Text(
                  '2026 @NikoKim NJU v1.2',
                  style: TextStyle(color: mutedColor, fontSize: 11),
                ),
                InkWell(
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => const AdminDevelopmentLogPage(),
                      ),
                    );
                  },
                  child: const Text(
                    ' 开发日志',
                    style: TextStyle(
                      color: warmOrange,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 2),
            const Text(
              '更多反馈 Email: nju_zsj030702@163.com',
              textAlign: TextAlign.center,
              style: TextStyle(color: mutedColor, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }
}

class AdminDevelopmentLogPage extends StatelessWidget {
  const AdminDevelopmentLogPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('开发日志')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: const [
          _DevelopmentVersionCard(
            version: 'v1.2',
            title: '当前版本',
            items: [
              '顾客端点餐页升级为左侧分类、右侧菜品卡片布局。',
              '商家端支持菜品属性单选、多选、最少和最多选择。',
              '订单支持商家不接单，取消或拒单后库存退回。',
              '商家端新增待接单和家庭待审核红点提醒。',
            ],
          ),
          SizedBox(height: 12),
          _DevelopmentVersionCard(
            version: 'v1.1',
            title: '当前版本',
            items: [
              '餐品属性：支持规格、口味等餐品可选项。',
              '家庭店铺：支持家庭店铺标识与展示。',
              '家庭筛选：顾客端可按家庭店铺进行筛选。',
              '家庭用户订单标识：订单中区分家庭用户来源。',
              '库存扣减：下单后自动扣减餐品库存。',
              '底部优化：版本号与反馈信息作为页面内容底部展示。',
            ],
          ),
          SizedBox(height: 12),
          _DevelopmentVersionCard(
            version: 'v1.0',
            title: '前一个版本',
            items: [
              '基础点餐流程：浏览店铺、查看餐品、加入购物车并下单。',
              '三端角色：顾客端、商家端、管理员端基础能力。',
              '头像与资料：支持用户头像和基础信息展示。',
              '购物车：支持餐品数量调整和结算。',
              '下单图与页面素材：完善点餐过程中的基础视觉展示。',
            ],
          ),
        ],
      ),
    );
  }
}

class _DevelopmentVersionCard extends StatelessWidget {
  const _DevelopmentVersionCard({
    required this.version,
    required this.title,
    required this.items,
  });

  final String version;
  final String title;
  final List<String> items;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: lineColor),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: softOrange,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    version,
                    style: const TextStyle(
                      color: warmOrange,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  title,
                  style: const TextStyle(
                    color: textColor,
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            for (final item in items)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Padding(
                      padding: EdgeInsets.only(top: 5),
                      child: Icon(
                        Icons.check_circle,
                        color: warmOrange,
                        size: 14,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        item,
                        style: const TextStyle(color: textColor, height: 1.45),
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class AdminApi {
  AdminApi({http.Client? client, String? baseUrl})
    : _client = client ?? http.Client(),
      _baseUrl = Uri.parse(
        baseUrl ??
            const String.fromEnvironment(
              'ADMIN_API_BASE_URL',
              defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
            ),
      );

  static const _tokenKey = 'admin_auth_token';
  final http.Client _client;
  final Uri _baseUrl;
  String? _token;

  String? get token => _token;

  Future<void> loadSavedToken() async {
    final prefs = await SharedPreferences.getInstance();
    _token = prefs.getString(_tokenKey);
  }

  Future<void> login(String nickname, String password) async {
    final data = await _request<Map<String, dynamic>>(
      'POST',
      '/auth/login',
      body: {'nickname': nickname, 'password': password, 'role': 'ADMIN'},
      authenticated: false,
    );
    final token = data['token']?.toString() ?? '';
    if (token.isEmpty) {
      throw const ApiException('登录返回异常');
    }
    _token = token;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenKey, token);
  }

  Future<void> logout() async {
    _token = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
  }

  Future<List<UserAccount>> fetchUsers({
    required String role,
    String? keyword,
  }) async {
    final data = await _request<Map<String, dynamic>>(
      'GET',
      '/admin/users',
      query: {
        'role': role,
        'page': '1',
        'pageSize': '100',
        if (keyword != null && keyword.isNotEmpty) 'keyword': keyword,
      },
    );
    final records = data['records'];
    if (records is List) {
      return records
          .whereType<Map<String, dynamic>>()
          .map(UserAccount.fromJson)
          .toList();
    }
    return const [];
  }

  Future<void> createUser({
    required String nickname,
    required String password,
    required String role,
    String? shopName,
    String? phone,
  }) async {
    await _request<void>(
      'POST',
      '/admin/users',
      body: {
        'nickname': nickname,
        'password': password,
        'role': role,
        if (shopName != null && shopName.isNotEmpty) 'shopName': shopName,
        if (phone != null && phone.isNotEmpty) 'phone': phone,
      },
    );
  }

  Future<void> updateStatus(int userId, int status) async {
    await _request<void>(
      'PUT',
      '/admin/users/$userId/status',
      body: {'status': status},
    );
  }

  Future<T> _request<T>(
    String method,
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
    bool authenticated = true,
  }) async {
    final uri = _uri(path, query);
    final headers = <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    };
    if (authenticated && _token != null && _token!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $_token';
    }

    late final http.Response response;
    switch (method) {
      case 'GET':
        response = await _client.get(uri, headers: headers);
      case 'POST':
        response = await _client.post(
          uri,
          headers: headers,
          body: jsonEncode(body ?? const {}),
        );
      case 'PUT':
        response = await _client.put(
          uri,
          headers: headers,
          body: jsonEncode(body ?? const {}),
        );
      default:
        throw const ApiException('不支持的请求方法');
    }

    Object? decoded;
    if (response.body.isNotEmpty) {
      decoded = jsonDecode(utf8.decode(response.bodyBytes));
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(_message(decoded, '请求失败'));
    }
    if (decoded is Map<String, dynamic>) {
      if (decoded['code'] == 0) {
        return decoded['data'] as T;
      }
      throw ApiException(decoded['message']?.toString() ?? '请求失败');
    }
    return null as T;
  }

  String _message(Object? decoded, String fallback) {
    if (decoded is Map<String, dynamic>) {
      return decoded['message']?.toString() ?? fallback;
    }
    return fallback;
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    final normalizedPath = path.startsWith('/') ? path.substring(1) : path;
    final basePath = _baseUrl.path.endsWith('/')
        ? _baseUrl.path
        : '${_baseUrl.path}/';
    return _baseUrl.replace(
      path: '$basePath$normalizedPath',
      queryParameters: query,
    );
  }
}

class UserAccount {
  const UserAccount({
    required this.id,
    required this.nickname,
    required this.role,
    required this.status,
  });

  final int id;
  final String nickname;
  final String role;
  final int status;

  String get roleText => role == 'SHOP' ? '商家' : '顾客';

  factory UserAccount.fromJson(Map<String, dynamic> json) {
    return UserAccount(
      id: (json['id'] as num).toInt(),
      nickname: json['nickname']?.toString() ?? '',
      role: json['role']?.toString() ?? '',
      status: (json['status'] as num?)?.toInt() ?? 0,
    );
  }
}

class ApiException implements Exception {
  const ApiException(this.message);

  final String message;
}
