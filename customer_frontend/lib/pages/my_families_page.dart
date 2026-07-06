import 'package:flutter/material.dart';

import '../app_mock_data.dart';
import '../models.dart';
import '../services/api_client.dart';
import '../widgets/app_footer.dart';

class MyFamiliesPage extends StatefulWidget {
  const MyFamiliesPage({super.key, required this.api});

  final CustomerApi api;

  @override
  State<MyFamiliesPage> createState() => _MyFamiliesPageState();
}

class _MyFamiliesPageState extends State<MyFamiliesPage> {
  final _codeController = TextEditingController();
  late Future<List<FamilyInfo>> _familiesFuture;
  bool _joining = false;

  @override
  void initState() {
    super.initState();
    _familiesFuture = widget.api.myFamilies();
  }

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  void _reload() {
    setState(() {
      _familiesFuture = widget.api.myFamilies();
    });
  }

  Future<void> _join() async {
    final code = _codeController.text.trim().toUpperCase();
    if (code.length != 6) {
      _show('请输入6位家庭号');
      return;
    }
    setState(() => _joining = true);
    try {
      final family = await widget.api.joinFamily(code);
      _codeController.clear();
      _show(family.isApproved ? '已加入家庭' : '申请已提交，等待商家审核');
      _reload();
    } catch (error) {
      _show(error.toString());
    } finally {
      if (mounted) setState(() => _joining = false);
    }
  }

  Future<void> _leave(FamilyInfo family) async {
    await widget.api.leaveFamily(family.shopId);
    _show('已退出家庭');
    _reload();
  }

  void _show(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('我的家庭')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: [
          TextField(
            controller: _codeController,
            textCapitalization: TextCapitalization.characters,
            maxLength: 6,
            decoration: InputDecoration(
              labelText: '家庭号',
              hintText: '请输入6位数字或大写字母',
              suffixIcon: IconButton(
                onPressed: _joining ? null : _join,
                icon: _joining
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.login),
              ),
            ),
          ),
          const SizedBox(height: 14),
          FutureBuilder<List<FamilyInfo>>(
            future: _familiesFuture,
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError) {
                return Text(snapshot.error.toString());
              }
              final families = snapshot.data ?? const [];
              if (families.isEmpty) {
                return const Padding(
                  padding: EdgeInsets.symmetric(vertical: 36),
                  child: Center(
                    child: Text(
                      '还没有加入家庭',
                      style: TextStyle(color: pageMutedColor),
                    ),
                  ),
                );
              }
              return Column(
                children: [
                  for (final family in families)
                    Card(
                      child: ListTile(
                        leading: const Icon(
                          Icons.home_outlined,
                          color: warmOrange,
                        ),
                        title: Text(family.familyName),
                        subtitle: Text(
                          '${family.familyCode} · ${family.isApproved ? "已加入" : "待审核"}',
                        ),
                        trailing: TextButton(
                          onPressed: () => _leave(family),
                          child: const Text('退出'),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
          const AppFooter(),
        ],
      ),
    );
  }
}
