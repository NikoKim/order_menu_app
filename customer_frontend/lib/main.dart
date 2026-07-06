import 'package:flutter/material.dart';

import 'pages/auth_page.dart';
import 'pages/home_page.dart';
import 'pages/profile_page.dart';
import 'services/api_client.dart';

void main() {
  runApp(const OrderMenuCustomerApp());
}

class OrderMenuCustomerApp extends StatefulWidget {
  const OrderMenuCustomerApp({super.key});

  static const warmOrange = Color(0xFFF28C28);

  @override
  State<OrderMenuCustomerApp> createState() => _OrderMenuCustomerAppState();
}

class _OrderMenuCustomerAppState extends State<OrderMenuCustomerApp> {
  late final AppServices _services;

  @override
  void initState() {
    super.initState();
    _services = AppServices.shared();
    _services.init();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: OrderMenuCustomerApp.warmOrange,
      primary: OrderMenuCustomerApp.warmOrange,
      secondary: const Color(0xFFFFB15C),
      surface: Colors.white,
    );

    return AppScope(
      services: _services,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        title: '点餐',
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: colorScheme,
          scaffoldBackgroundColor: Colors.white,
          appBarTheme: const AppBarTheme(
            centerTitle: false,
            elevation: 0,
            backgroundColor: Colors.white,
            foregroundColor: Color(0xFF242424),
            surfaceTintColor: Colors.white,
          ),
          cardTheme: CardThemeData(
            elevation: 0,
            color: Colors.white,
            margin: EdgeInsets.zero,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
              side: const BorderSide(color: Color(0xFFF0E7DE)),
            ),
          ),
          filledButtonTheme: FilledButtonThemeData(
            style: FilledButton.styleFrom(
              backgroundColor: OrderMenuCustomerApp.warmOrange,
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
              borderSide: const BorderSide(color: Color(0xFFF0E1D0)),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: const BorderSide(color: Color(0xFFF0E1D0)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: const BorderSide(
                color: OrderMenuCustomerApp.warmOrange,
                width: 1.5,
              ),
            ),
          ),
        ),
        home: const CustomerShell(),
      ),
    );
  }
}

class AppScope extends InheritedWidget {
  const AppScope({super.key, required this.services, required super.child});

  final AppServices services;

  static AppServices of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope not found');
    return scope!.services;
  }

  @override
  bool updateShouldNotify(AppScope oldWidget) => services != oldWidget.services;
}

class CustomerShell extends StatefulWidget {
  const CustomerShell({super.key});

  @override
  State<CustomerShell> createState() => _CustomerShellState();
}

class _CustomerShellState extends State<CustomerShell> {
  int _currentIndex = 0;
  bool _autoSelectedLogin = false;
  late CustomerApi _customerApi;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _customerApi = CustomerApi(AppScope.of(context).api);
  }

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    return AnimatedBuilder(
      animation: services.auth,
      builder: (context, _) {
        final isReady = services.auth.isReady;
        final loggedIn = services.auth.isLoggedIn;
        if (isReady && !loggedIn && !_autoSelectedLogin) {
          _autoSelectedLogin = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) setState(() => _currentIndex = 1);
          });
        }

        final pages = [
          HomePage(api: _customerApi),
          loggedIn
              ? ProfilePage(api: _customerApi)
              : AuthPage(onLoggedIn: () => setState(() => _currentIndex = 1)),
        ];

        return Scaffold(
          body: SafeArea(
            child: isReady
                ? IndexedStack(index: _currentIndex, children: pages)
                : const Center(child: CircularProgressIndicator()),
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _currentIndex,
            indicatorColor: const Color(0xFFFFE3C4),
            backgroundColor: Colors.white,
            surfaceTintColor: Colors.white,
            onDestinationSelected: (index) {
              setState(() {
                _currentIndex = index;
              });
            },
            destinations: [
              const NavigationDestination(
                icon: Icon(Icons.storefront_outlined),
                selectedIcon: Icon(Icons.storefront),
                label: '首页',
              ),
              NavigationDestination(
                icon: Icon(
                  loggedIn ? Icons.person_outline : Icons.login_outlined,
                ),
                selectedIcon: Icon(loggedIn ? Icons.person : Icons.login),
                label: loggedIn ? '我的' : '登录',
              ),
            ],
          ),
        );
      },
    );
  }
}
