import 'package:flutter/material.dart';

import '../shop_mock_data.dart';

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
            version: 'v1.2',
            title: '当前版本',
            items: [
              '菜品属性支持单选、多选、最少选择和最多选择。',
              '订单新增不接单操作，不接单后库存自动退回。',
              '底部订单和店铺入口显示待处理红点数量。',
              '家庭管理入口显示待审核顾客数量。',
              '订单详情展示顾客选择的菜品规格。',
            ],
          ),
          SizedBox(height: 12),
          _VersionCard(
            version: 'v1.1',
            title: '上一个版本',
            items: [
              '餐品属性：支持规格、口味等餐品可选项。',
              '家庭店铺：支持家庭编号、家庭图片和家庭成员管理。',
              '家庭用户订单标识：订单中区分家庭用户来源。',
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
              '商家端支持菜品、分类、订单基础管理。',
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
