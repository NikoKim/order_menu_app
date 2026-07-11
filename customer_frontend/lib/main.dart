import 'package:flutter/material.dart';
import 'dart:io';

import 'pages/auth_page.dart';
import 'pages/home_page.dart';
import 'pages/profile_page.dart';
import 'services/api_client.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:open_filex/open_filex.dart';

import 'services/customer_websocket_service.dart';

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
  bool _hasCheckedUpdate = false;
  late CustomerApi _customerApi;
  String? _webSocketToken;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _customerApi = CustomerApi(AppScope.of(context).api);

    if (!_hasCheckedUpdate) {
      _hasCheckedUpdate = true;
      _checkAppUpdate();
    }
  }

  Future<void> _checkAppUpdate() async {
    try {
      // 获取当前设备的 App 内部版本号 (例如 1, 2)
      PackageInfo packageInfo = await PackageInfo.fromPlatform();
      int currentCode = int.tryParse(packageInfo.buildNumber) ?? 1;

      // 向服务器查询 'customer' 端是否有新版 (注意这里传的是 'customer')
      final updateInfo = await _customerApi.checkUpdate(
        'customer',
        currentCode,
      );

      if (updateInfo['hasUpdate'] == true && mounted) {
        _showUpdateDialog(updateInfo);
      }
    } catch (e) {
      debugPrint('版本检测失败: $e');
    }
  }

  void _syncWebSocket(AuthController auth) {
    final token = auth.api.token;
    if (auth.isReady && auth.isLoggedIn && token != null && token.isNotEmpty) {
      if (_webSocketToken != token) {
        _webSocketToken = token;
        CustomerWebSocketService.instance.connect(token, force: true);
      }
      return;
    }

    if (_webSocketToken != null) {
      _webSocketToken = null;
      CustomerWebSocketService.instance.disconnect();
    }
  }

  @override
  void dispose() {
    CustomerWebSocketService.instance.disconnect();
    super.dispose();
  }

  void _showUpdateDialog(Map<String, dynamic> updateInfo) {
    final bool isForce = updateInfo['isForce'] == true;
    final String versionName = updateInfo['versionName']?.toString() ?? '';
    final String url = updateInfo['downloadUrl']?.toString() ?? '';
    final String rawUpdateContent =
        updateInfo['updateContent']?.toString() ?? '';
    final String updateContent = rawUpdateContent
        .replaceAll(r'\r\n', '\n')
        .replaceAll(r'\n', '\n');

    showDialog(
      context: context,
      barrierDismissible: !isForce,
      builder: (dialogContext) {
        bool downloading = false;
        bool downloaded = false;
        double progress = 0;
        String? apkPath;
        String statusText = '为了更好的点餐体验，请更新到最新版本。';

        return StatefulBuilder(
          builder: (context, setDialogState) {
            Future<void> installApk() async {
              final path = apkPath;
              if (path == null || path.isEmpty) {
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(const SnackBar(content: Text('安装包不存在，请先下载')));
                return;
              }

              try {
                final result = await OpenFilex.open(
                  path,
                  type: 'application/vnd.android.package-archive',
                );
                debugPrint('打开 APK 结果: ${result.type}, ${result.message}');

                if (result.type != ResultType.done && mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('打开安装程序失败：${result.message}')),
                  );
                }
              } catch (e, st) {
                debugPrint('打开安装程序异常: $e');
                debugPrint('$st');

                if (mounted) {
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text('打开安装程序失败：$e')));
                }
              }
            }

            Future<void> startDownload() async {
              if (url.isEmpty) {
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(const SnackBar(content: Text('下载链接为空')));
                return;
              }

              if (!Platform.isAndroid) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('当前仅支持 Android APK 更新')),
                );
                return;
              }

              setDialogState(() {
                downloading = true;
                downloaded = false;
                progress = 0;
                statusText = '正在准备下载...';
              });

              try {
                final uri = Uri.parse(url);
                final fileName = uri.pathSegments.isNotEmpty
                    ? uri.pathSegments.last
                    : 'customer_frontend.apk';

                final tempDir = await getTemporaryDirectory();
                final savePath = '${tempDir.path}/$fileName';

                final file = File(savePath);
                if (await file.exists()) {
                  await file.delete();
                }

                final dio = Dio(
                  BaseOptions(
                    connectTimeout: const Duration(seconds: 15),
                    sendTimeout: const Duration(seconds: 15),
                    receiveTimeout: const Duration(minutes: 5),
                  ),
                );

                await dio.download(
                  url,
                  savePath,
                  onReceiveProgress: (received, total) {
                    if (total <= 0) {
                      setDialogState(() {
                        statusText = '正在下载...';
                      });
                      return;
                    }

                    final nextProgress = (received / total).clamp(0.0, 1.0);
                    setDialogState(() {
                      progress = nextProgress;
                      statusText =
                          '正在下载 ${(progress * 100).toStringAsFixed(0)}%';
                    });
                  },
                );

                setDialogState(() {
                  downloading = false;
                  downloaded = true;
                  progress = 1;
                  apkPath = savePath;
                  statusText = '下载完成，点击“安装”继续更新。';
                });

                // 下载完成后自动尝试打开一次安装程序；如果用户取消，按钮仍会保留为“安装”。
                await installApk();
              } catch (e, st) {
                debugPrint('下载更新失败: $e');
                debugPrint('$st');

                setDialogState(() {
                  downloading = false;
                  downloaded = false;
                  statusText = '下载失败，请检查网络后重试。';
                });

                if (mounted) {
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text('下载失败：$e')));
                }
              }
            }

            return PopScope(
              canPop: !isForce && !downloading,
              child: AlertDialog(
                title: Text('发现新版本 V$versionName'),
                content: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // 动态渲染更新内容
                      if (updateContent.isNotEmpty) ...[
                        const Text(
                          '更新内容：',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          updateContent,
                          style: const TextStyle(fontSize: 14, height: 1.4),
                        ),
                        const SizedBox(height: 16),
                      ],

                      // 防锁屏/切后台提示框
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 8,
                        ),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFFF4E8),
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: const Color(0xFFFFB15C)),
                        ),
                        child: const Text(
                          '更新期间请不要锁屏或跳出软件外，否则会导致下载失败。',
                          style: TextStyle(
                            color: Color(0xFFF28C28),
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),

                      Text(statusText),
                      if (downloading || downloaded) ...[
                        const SizedBox(height: 16),
                        LinearProgressIndicator(value: progress),
                        const SizedBox(height: 8),
                        Text('${(progress * 100).toStringAsFixed(0)}%'),
                      ],
                    ],
                  ),
                ),
                actions: [
                  if (!isForce && !downloading)
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('稍后更新'),
                    ),
                  FilledButton(
                    onPressed: downloading
                        ? null
                        : downloaded
                        ? installApk
                        : startDownload,
                    child: Text(
                      downloading
                          ? '下载中...'
                          : downloaded
                          ? '安装'
                          : '立即下载',
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    return AnimatedBuilder(
      animation: services.auth,
      builder: (context, _) {
        final isReady = services.auth.isReady;
        final loggedIn = services.auth.isLoggedIn;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _syncWebSocket(services.auth);
        });
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
