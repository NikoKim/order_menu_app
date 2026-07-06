import 'dart:async';

import 'package:flutter/material.dart';

import 'pages/dashboard_page.dart';
import 'pages/dish_manage_page.dart';
import 'pages/order_manage_page.dart';
import 'pages/shop_login_page.dart';
import 'pages/shop_profile_page.dart';
import 'services/shop_api_service.dart';
import 'shop_mock_data.dart';

void main() {
  runApp(const ShopFrontendApp());
}

class ShopFrontendApp extends StatelessWidget {
  const ShopFrontendApp({super.key});

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
      title: '商家端',
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
      home: const ShopShell(),
    );
  }
}

class ShopShell extends StatefulWidget {
  const ShopShell({super.key});

  @override
  State<ShopShell> createState() => _ShopShellState();
}

class _ShopShellState extends State<ShopShell> {
  int _index = 0;
  bool _checking = true;
  bool _loggedIn = false;
  int _pendingOrders = 0;
  int _pendingFamilyMembers = 0;
  Timer? _notificationTimer;

  @override
  void initState() {
    super.initState();
    _restore();
  }

  @override
  void dispose() {
    _notificationTimer?.cancel();
    super.dispose();
  }

  Future<void> _restore() async {
    await shopApi.loadSavedToken();
    if (mounted) {
      setState(() {
        _loggedIn = shopApi.token != null && shopApi.token!.isNotEmpty;
        _checking = false;
      });
      if (_loggedIn) {
        _startNotificationRefresh();
      }
    }
  }

  Future<void> _logout() async {
    await shopApi.logout();
    _notificationTimer?.cancel();
    if (mounted) {
      setState(() {
        _index = 0;
        _loggedIn = false;
        _pendingOrders = 0;
        _pendingFamilyMembers = 0;
      });
    }
  }

  void _startNotificationRefresh() {
    _loadNotifications();
    _notificationTimer?.cancel();
    _notificationTimer = Timer.periodic(const Duration(seconds: 8), (_) {
      _loadNotifications();
    });
  }

  Future<void> _loadNotifications() async {
    if (!_loggedIn) {
      return;
    }
    try {
      final info = await shopApi.fetchNotifications();
      if (mounted) {
        setState(() {
          _pendingOrders = info.pendingOrders;
          _pendingFamilyMembers = info.pendingFamilyMembers;
        });
      }
    } catch (_) {
      // Badge refresh is best-effort; page-level actions still show errors.
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_checking) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!_loggedIn) {
      return ShopLoginPage(
        onLoggedIn: () {
          setState(() => _loggedIn = true);
          _startNotificationRefresh();
        },
      );
    }

    final pages = [
      const DashboardPage(),
      const DishManagePage(),
      const OrderManagePage(),
      ShopProfilePage(onLogout: _logout),
    ];

    return Scaffold(
      body: SafeArea(
        child: IndexedStack(index: _index, children: pages),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        indicatorColor: const Color(0xFFFFE3C4),
        onDestinationSelected: (value) {
          setState(() => _index = value);
          _loadNotifications();
        },
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard),
            label: '工作台',
          ),
          const NavigationDestination(
            icon: Icon(Icons.restaurant_menu_outlined),
            selectedIcon: Icon(Icons.restaurant_menu),
            label: '菜品',
          ),
          NavigationDestination(
            icon: _BadgeIcon(
              icon: Icons.receipt_long_outlined,
              count: _pendingOrders,
            ),
            selectedIcon: _BadgeIcon(
              icon: Icons.receipt_long,
              count: _pendingOrders,
            ),
            label: '订单',
          ),
          NavigationDestination(
            icon: _BadgeIcon(
              icon: Icons.storefront_outlined,
              count: _pendingFamilyMembers,
            ),
            selectedIcon: _BadgeIcon(
              icon: Icons.storefront,
              count: _pendingFamilyMembers,
            ),
            label: '店铺',
          ),
        ],
      ),
    );
  }
}

class _BadgeIcon extends StatelessWidget {
  const _BadgeIcon({required this.icon, required this.count});

  final IconData icon;
  final int count;

  @override
  Widget build(BuildContext context) {
    if (count <= 0) {
      return Icon(icon);
    }
    return Badge(
      label: Text(count > 99 ? '99+' : '$count'),
      child: Icon(icon),
    );
  }
}
