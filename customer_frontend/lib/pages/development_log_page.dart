import 'package:flutter/material.dart';

import '../app_mock_data.dart';

class DevelopmentLogPage extends StatelessWidget {
  const DevelopmentLogPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('开发日志')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: const [
          _VersionCard(
            version: 'v1.2.1',
            title: '当前版本',
            items: [
              '优化点餐页面布局，首页布局',
              '加入检测新版本和下载新版本功能',
            ],
          ),
          SizedBox(height: 12),
          _VersionCard(
            version: 'v1.2',
            title: '前版本',
            items: [
              '点餐页改为左侧分类、右侧菜品卡片布局。',
              '菜品规格改为点餐页弹窗选择，并支持可选、多选、数量限制。',
              '购物车按菜品规格分行展示，不同规格不再合并。',
              '购物车和结算页展示已选规格，数量支持手动输入。',
              '首页家庭标签优化为更轻的横向标签布局。',
            ],
          ),
          SizedBox(height: 12),
          _VersionCard(
            version: 'v1.1',
            title: '前版本',
            items: [
              '餐品属性：支持规格、口味等餐品可选项。',
              '家庭店铺：支持家庭编号、家庭图片和家庭成员管理。',
              '家庭筛选：顾客端可按加入的家庭查看店铺。',
              '库存扣减：下单后自动扣减餐品库存。',
            ],
          ),
          SizedBox(height: 12),
          _VersionCard(
            version: 'v1.0',
            title: '基础版本',
            items: [
              '基础点餐流程：浏览店铺、查看餐品、加入购物车并下单。',
              '三端角色：顾客端、商家端、管理员端基础能力。',
              '购物车：支持餐品数量调整和结算。',
            ],
          ),
        ],
      ),
    );
  }
}

class _VersionCard extends StatelessWidget {
  const _VersionCard({
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
        side: const BorderSide(color: pageLineColor),
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
                    color: pageTextColor,
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
                        style: const TextStyle(
                          color: pageTextColor,
                          height: 1.45,
                        ),
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
