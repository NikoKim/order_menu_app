import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../pages/development_log_page.dart';

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
                  '2026 @NikoKim NJU v2.2.1',
                  style: TextStyle(color: pageMutedColor, fontSize: 11),
                ),
                InkWell(
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => const DevelopmentLogPage(),
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
              style: TextStyle(color: pageMutedColor, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }
}
