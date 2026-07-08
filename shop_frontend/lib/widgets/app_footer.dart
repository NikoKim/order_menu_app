import 'package:flutter/material.dart';

import '../pages/development_log_page.dart';
import '../shop_mock_data.dart';

class AppFooter extends StatelessWidget {
  const AppFooter({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Wrap(
              alignment: WrapAlignment.center,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const Text(
                  '2026 @NikoKim NJU v1.2.3a',
                  style: TextStyle(color: mutedColor, fontSize: 11),
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
              style: TextStyle(color: mutedColor, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }
}
