import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:shop_frontend/services/websocket_service.dart';
import 'package:shop_frontend/services/latest_request.dart';

import 'models/shop_api_models.dart';
import 'pages/dashboard_page.dart';
import 'pages/chat_list_page.dart';
import 'pages/dish_manage_page.dart';
import 'pages/order_manage_page.dart';
import 'pages/shop_login_page.dart';
import 'pages/shop_profile_page.dart';
import 'services/shop_api_service.dart';
import 'services/push_token_service.dart';
import 'widgets/app_notice.dart';
import 'shop_mock_data.dart';
import 'package:package_info_plus/package_info_plus.dart'; // 新增
import 'package:dio/dio.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

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
  int _pendingFeedback = 0;
  int _unreadChats = 0;
  int _unreadMessageNotifications = 0;
  StreamSubscription? _wsSubscription;
  StreamSubscription<bool>? _connectionSubscription;
  final _chatBadgeRequests = LatestRequest();
  final _notificationBadgeRequests = LatestRequest();

  bool _hasCheckedUpdate = false;

  @override
  void initState() {
    super.initState();
    _restore();

    if (!_hasCheckedUpdate) {
      _hasCheckedUpdate = true;
      _checkAppUpdate();
    }

    _wsSubscription = ShopWebSocketService.instance.messageStream.listen((msg) {
      if (!_loggedIn) return;

      if (msg.module == 'NOTIFICATION') {
        final unread = int.tryParse('${msg.data['unreadCount'] ?? ''}');
        if (unread != null && mounted) {
          setState(() => _unreadMessageNotifications = unread);
        }
      }

      final needRefreshBadge = {
        'ORDER',
        'FEEDBACK',
        'FAMILY',
        'NOTIFICATION',
        'CHAT',
      }.contains(msg.module);

      if (needRefreshBadge) {
        _loadNotifications();
        _loadChatBadge();
      }
    });
    _connectionSubscription = ShopWebSocketService.instance.connectionStream
        .where((connected) => connected)
        .listen((_) {
          if (_loggedIn) {
            _loadNotifications();
            _loadChatBadge();
            unawaited(startShopPushTokens().catchError((_) {}));
          }
        });
  }

  Future<void> _checkAppUpdate() async {
    try {
      // 获取当前设备的 App 内部版本号 (例如 1, 2)
      PackageInfo packageInfo = await PackageInfo.fromPlatform();
      int currentCode = int.tryParse(packageInfo.buildNumber) ?? 1;

      // 向服务器查询 'shop' 端是否有新版
      final updateInfo = await shopApi.checkUpdate('shop', currentCode);

      if (updateInfo['hasUpdate'] == true && mounted) {
        _showUpdateDialog(updateInfo);
      }
    } catch (e) {
      debugPrint('版本检测失败: $e');
    }
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
        String statusText = '为了更好的商家发布，修改餐品体验，请更新到最新版本。';

        return StatefulBuilder(
          builder: (context, setDialogState) {
            Future<void> installApk() async {
              final path = apkPath;
              if (path == null || path.isEmpty) {
                AppNotice.show(context, '安装包不存在，请先下载');
                return;
              }

              final file = File(path);
              if (!await file.exists()) {
                setDialogState(() {
                  downloaded = false;
                  apkPath = null;
                  statusText = '安装包已丢失，请重新下载';
                });
                return;
              }

              final result = await OpenFilex.open(
                path,
                type: 'application/vnd.android.package-archive',
              );

              debugPrint('打开 APK 结果: ${result.type}, ${result.message}');

              if (result.type != ResultType.done && mounted) {
                AppNotice.show(context, '打开安装程序失败：${result.message}');
              }
            }

            Future<void> startDownload() async {
              if (url.isEmpty) {
                AppNotice.show(context, '下载链接为空');
                return;
              }

              if (!Platform.isAndroid) {
                AppNotice.show(context, '当前仅支持 Android APK 更新');
                return;
              }

              setDialogState(() {
                downloading = true;
                downloaded = false;
                progress = 0;
                apkPath = null;
                statusText = '正在准备下载...';
              });

              try {
                final uri = Uri.parse(url);
                final fileName = uri.pathSegments.isNotEmpty
                    ? uri.pathSegments.last
                    : 'shop_frontend.apk';
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

                    final value = (received / total).clamp(0.0, 1.0);
                    setDialogState(() {
                      progress = value;
                      statusText = '正在下载 ${(value * 100).toStringAsFixed(0)}%';
                    });
                  },
                );

                setDialogState(() {
                  downloading = false;
                  downloaded = true;
                  progress = 1;
                  apkPath = savePath;
                  statusText = '下载完成，请点击安装。如果系统已弹出安装界面，可直接继续安装。';
                });

                await installApk();
              } catch (e, st) {
                debugPrint('下载更新失败: $e');
                debugPrint('$st');

                setDialogState(() {
                  downloading = false;
                  downloaded = false;
                  statusText = '下载失败，请检查网络后重试';
                });

                if (mounted) {
                  AppNotice.show(context, '下载失败：$e');
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
                      // 增加判断：只有当后端传了更新说明时，才渲染这部分 UI
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
                      onPressed: () => Navigator.pop(dialogContext),
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
  void dispose() {
    _wsSubscription?.cancel();
    _connectionSubscription?.cancel();
    ShopWebSocketService.instance.disconnect();
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
        _loadNotifications();
        _loadChatBadge();
        ShopWebSocketService.instance.connect();
        unawaited(startShopPushTokens().catchError((_) {}));
      }
    }
  }

  Future<void> _logout() async {
    await shopPushTokens.clear().catchError((_) {});
    ShopWebSocketService.instance.disconnect();
    await shopApi.logout();
    if (mounted) {
      setState(() {
        _index = 0;
        _loggedIn = false;
        _pendingOrders = 0;
        _pendingFamilyMembers = 0;
        _pendingFeedback = 0;
        _unreadChats = 0;
        _unreadMessageNotifications = 0;
      });
    }
  }

  Future<void> _loadNotifications() async {
    if (!_loggedIn) return;
    final generation = _notificationBadgeRequests.begin();
    try {
      final results = await Future.wait([
        shopApi.fetchNotifications(),
        shopApi.fetchFeedback(status: 'PENDING', pageSize: 1),
        shopApi.fetchUnreadMessageNotificationCount(),
      ]);
      if (mounted && _notificationBadgeRequests.isLatest(generation)) {
        setState(() {
          final info = results[0] as ShopNotificationInfo;
          final feedbackRes = results[1] as PageResult<FeedbackInfo>;
          _pendingOrders = info.pendingOrders;
          _pendingFamilyMembers = info.pendingFamilyMembers;
          _pendingFeedback = feedbackRes.total; // 获取真实数量
          _unreadMessageNotifications = results[2] as int;
        });
      }
    } catch (_) {}
  }

  Future<void> _loadChatBadge() async {
    if (!_loggedIn) return;
    final generation = _chatBadgeRequests.begin();
    try {
      final conversations = await shopApi.fetchConversations();
      final count = conversations
          .where((item) => !item.muted)
          .fold<int>(0, (total, item) => total + item.unreadCount);
      if (mounted && _chatBadgeRequests.isLatest(generation)) {
        setState(() => _unreadChats = count);
      }
    } catch (_) {}
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
          _loadNotifications();
          _loadChatBadge();
          ShopWebSocketService.instance.connect(force: true);
          unawaited(startShopPushTokens().catchError((_) {}));
        },
      );
    }

    final pages = [
      DashboardPage(onBadgeChanged: _loadNotifications),
      const DishManagePage(),
      const ChatListPage(),
      const OrderManagePage(),
      ShopProfilePage(onLogout: _logout, onBadgeChanged: _loadNotifications),
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
          if (value == 2) _loadChatBadge();
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
              icon: Icons.chat_bubble_outline,
              count: _unreadChats,
            ),
            selectedIcon: _BadgeIcon(
              icon: Icons.chat_bubble,
              count: _unreadChats,
            ),
            label: '聊天',
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
              count:
                  _pendingFamilyMembers +
                  _pendingFeedback +
                  _unreadMessageNotifications,
            ),
            selectedIcon: _BadgeIcon(
              icon: Icons.storefront,
              count:
                  _pendingFamilyMembers +
                  _pendingFeedback +
                  _unreadMessageNotifications,
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
    if (count <= 0) return Icon(icon);

    return Badge(
      isLabelVisible: true,
      label: Text(count > 9 ? '...' : '$count'),
      offset: const Offset(8, -4),
      backgroundColor: Colors.redAccent,
      child: Icon(icon),
    );
  }
}
