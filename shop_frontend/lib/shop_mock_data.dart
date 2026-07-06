import 'package:flutter/material.dart';

const warmOrange = Color(0xFFF28C28);
const softOrange = Color(0xFFFFF4E8);
const lineColor = Color(0xFFF0E7DE);
const textColor = Color(0xFF242424);
const mutedColor = Color(0xFF77716B);

class ShopDish {
  const ShopDish({
    required this.id,
    required this.name,
    required this.category,
    required this.price,
    required this.status,
    required this.sales,
    required this.icon,
    required this.color,
  });

  final int id;
  final String name;
  final String category;
  final double price;
  final String status;
  final int sales;
  final IconData icon;
  final Color color;
}

class ShopOrder {
  const ShopOrder({
    required this.id,
    required this.orderNo,
    required this.customerName,
    required this.status,
    required this.createdAt,
    required this.totalAmount,
    required this.items,
    required this.remark,
  });

  final int id;
  final String orderNo;
  final String customerName;
  final String status;
  final String createdAt;
  final double totalAmount;
  final List<String> items;
  final String remark;
}

class ShopFeedback {
  const ShopFeedback({
    required this.id,
    required this.customerName,
    required this.content,
    required this.status,
    required this.createdAt,
  });

  final int id;
  final String customerName;
  final String content;
  final String status;
  final String createdAt;
}

const shopName = '暖橙咖喱饭';
const shopCategories = ['全部', '盖饭', '炸物', '饮品'];

const shopDishes = [
  ShopDish(
    id: 1,
    name: '招牌牛肉饭',
    category: '盖饭',
    price: 26,
    status: '上架中',
    sales: 128,
    icon: Icons.rice_bowl,
    color: Color(0xFFFFD2B8),
  ),
  ShopDish(
    id: 2,
    name: '炸鸡咖喱饭',
    category: '炸物',
    price: 28,
    status: '上架中',
    sales: 92,
    icon: Icons.set_meal,
    color: Color(0xFFFFC66E),
  ),
  ShopDish(
    id: 3,
    name: '柠檬红茶',
    category: '饮品',
    price: 9,
    status: '已下架',
    sales: 31,
    icon: Icons.local_cafe,
    color: Color(0xFFFFE1A8),
  ),
];

const shopOrders = [
  ShopOrder(
    id: 1,
    orderNo: '202607041030000001',
    customerName: '小明',
    status: '待接单',
    createdAt: '2026-07-04 10:30',
    totalAmount: 54,
    items: ['招牌牛肉饭 x1', '炸鸡咖喱饭 x1'],
    remark: '少放辣',
  ),
  ShopOrder(
    id: 2,
    orderNo: '202607041015000002',
    customerName: '小李',
    status: '制作中',
    createdAt: '2026-07-04 10:15',
    totalAmount: 26,
    items: ['招牌牛肉饭 x1'],
    remark: '不要洋葱',
  ),
  ShopOrder(
    id: 3,
    orderNo: '202607031845000003',
    customerName: '小陈',
    status: '已完成',
    createdAt: '2026-07-03 18:45',
    totalAmount: 37,
    items: ['炸鸡咖喱饭 x1', '柠檬红茶 x1'],
    remark: '餐具一份',
  ),
];

const shopFeedbackList = [
  ShopFeedback(
    id: 1,
    customerName: '小明',
    content: '咖喱很好吃，希望增加米饭加量选项。',
    status: '待回复',
    createdAt: '2026-07-04 09:50',
  ),
  ShopFeedback(
    id: 2,
    customerName: '小李',
    content: '炸鸡外皮很脆，配送也很快。',
    status: '已回复',
    createdAt: '2026-07-03 19:20',
  ),
];

ShopDish dishById(int id) {
  return shopDishes.firstWhere(
    (dish) => dish.id == id,
    orElse: () => shopDishes.first,
  );
}

ShopOrder orderById(int id) {
  return shopOrders.firstWhere(
    (order) => order.id == id,
    orElse: () => shopOrders.first,
  );
}
