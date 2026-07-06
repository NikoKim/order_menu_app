import 'package:flutter/material.dart';

const warmOrange = Color(0xFFF28C28);
const softOrange = Color(0xFFFFF4E8);
const pageLineColor = Color(0xFFF0E7DE);
const pageTextColor = Color(0xFF242424);
const pageMutedColor = Color(0xFF77716B);

class MockShop {
  const MockShop({
    required this.id,
    required this.name,
    required this.category,
    required this.distance,
    required this.rating,
    required this.deliveryTime,
    required this.minOrder,
    required this.promo,
    required this.color,
    required this.icon,
  });

  final int id;
  final String name;
  final String category;
  final String distance;
  final String rating;
  final String deliveryTime;
  final String minOrder;
  final String promo;
  final Color color;
  final IconData icon;
}

class MockDish {
  const MockDish({
    required this.id,
    required this.shopId,
    required this.name,
    required this.category,
    required this.description,
    required this.price,
    required this.color,
    required this.icon,
    this.sales = 0,
  });

  final int id;
  final int shopId;
  final String name;
  final String category;
  final String description;
  final double price;
  final Color color;
  final IconData icon;
  final int sales;
}

class MockOrder {
  const MockOrder({
    required this.id,
    required this.orderNo,
    required this.shopName,
    required this.status,
    required this.createdAt,
    required this.totalAmount,
    required this.items,
    required this.remark,
  });

  final int id;
  final String orderNo;
  final String shopName;
  final String status;
  final String createdAt;
  final double totalAmount;
  final List<MockOrderItem> items;
  final String remark;
}

class MockOrderItem {
  const MockOrderItem({
    required this.name,
    required this.quantity,
    required this.price,
  });

  final String name;
  final int quantity;
  final double price;
}

const mockShops = [
  MockShop(
    id: 1,
    name: '晨光早点铺',
    category: '包子 粥粉 面点',
    distance: '0.6km',
    rating: '4.8',
    deliveryTime: '18分钟',
    minOrder: '起送 ¥15',
    promo: '新客立减 ¥6',
    color: Color(0xFFFFD9A8),
    icon: Icons.breakfast_dining,
  ),
  MockShop(
    id: 2,
    name: '巷口川味小馆',
    category: '川菜 下饭菜 米饭',
    distance: '1.2km',
    rating: '4.7',
    deliveryTime: '28分钟',
    minOrder: '起送 ¥25',
    promo: '满 ¥45 减 ¥8',
    color: Color(0xFFFFB7A1),
    icon: Icons.local_fire_department,
  ),
  MockShop(
    id: 3,
    name: '轻食能量站',
    category: '沙拉 鸡胸肉 果汁',
    distance: '900m',
    rating: '4.9',
    deliveryTime: '22分钟',
    minOrder: '起送 ¥20',
    promo: '低脂套餐 8 折',
    color: Color(0xFFCFE8C9),
    icon: Icons.eco,
  ),
  MockShop(
    id: 4,
    name: '暖橙咖喱饭',
    category: '咖喱 盖饭 炸物',
    distance: '1.8km',
    rating: '4.6',
    deliveryTime: '32分钟',
    minOrder: '起送 ¥18',
    promo: '招牌饭赠汤',
    color: Color(0xFFFFC66E),
    icon: Icons.rice_bowl,
  ),
];

const mockDishes = [
  MockDish(
    id: 101,
    shopId: 1,
    name: '鲜虾云吞面',
    category: '面点',
    description: '清爽汤底配鲜虾云吞，适合早餐和午餐。',
    price: 22,
    color: Color(0xFFFFE1A8),
    icon: Icons.ramen_dining,
    sales: 86,
  ),
  MockDish(
    id: 102,
    shopId: 1,
    name: '南瓜小米粥',
    category: '粥粉',
    description: '口感绵密，甜度温和。',
    price: 9,
    color: Color(0xFFFFD9A8),
    icon: Icons.soup_kitchen,
    sales: 64,
  ),
  MockDish(
    id: 201,
    shopId: 2,
    name: '麻婆豆腐盖饭',
    category: '下饭菜',
    description: '微麻微辣，配一份米饭。',
    price: 24,
    color: Color(0xFFFFB7A1),
    icon: Icons.local_fire_department,
    sales: 112,
  ),
  MockDish(
    id: 301,
    shopId: 3,
    name: '低脂鸡胸沙拉',
    category: '沙拉',
    description: '鸡胸肉、玉米、番茄和生菜，清爽不腻。',
    price: 29,
    color: Color(0xFFD8EBCB),
    icon: Icons.lunch_dining,
    sales: 73,
  ),
  MockDish(
    id: 401,
    shopId: 4,
    name: '招牌牛肉饭',
    category: '盖饭',
    description: '牛肉软烂，咖喱浓郁，默认配汤。',
    price: 26,
    color: Color(0xFFFFD2B8),
    icon: Icons.rice_bowl,
    sales: 128,
  ),
  MockDish(
    id: 402,
    shopId: 4,
    name: '炸鸡咖喱饭',
    category: '炸物',
    description: '外酥里嫩的炸鸡排，搭配暖橙咖喱。',
    price: 28,
    color: Color(0xFFFFC66E),
    icon: Icons.set_meal,
    sales: 92,
  ),
];

const mockOrders = [
  MockOrder(
    id: 1,
    orderNo: '202607041030000001',
    shopName: '暖橙咖喱饭',
    status: '已完成',
    createdAt: '2026-07-04 10:30',
    totalAmount: 54,
    remark: '少放辣',
    items: [
      MockOrderItem(name: '招牌牛肉饭', quantity: 1, price: 26),
      MockOrderItem(name: '炸鸡咖喱饭', quantity: 1, price: 28),
    ],
  ),
  MockOrder(
    id: 2,
    orderNo: '202607031845000002',
    shopName: '晨光早点铺',
    status: '已下单',
    createdAt: '2026-07-03 18:45',
    totalAmount: 31,
    remark: '餐具一份',
    items: [
      MockOrderItem(name: '鲜虾云吞面', quantity: 1, price: 22),
      MockOrderItem(name: '南瓜小米粥', quantity: 1, price: 9),
    ],
  ),
];

List<MockDish> dishesForShop(int shopId) {
  return mockDishes.where((dish) => dish.shopId == shopId).toList();
}

MockShop shopById(int shopId) {
  return mockShops.firstWhere(
    (shop) => shop.id == shopId,
    orElse: () => mockShops.first,
  );
}

MockDish dishById(int dishId) {
  return mockDishes.firstWhere(
    (dish) => dish.id == dishId,
    orElse: () => mockDishes.first,
  );
}

MockOrder orderById(int orderId) {
  return mockOrders.firstWhere(
    (order) => order.id == orderId,
    orElse: () => mockOrders.first,
  );
}
