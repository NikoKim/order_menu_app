import 'package:flutter/material.dart';
import '../widgets/app_notice.dart';
import 'package:image_picker/image_picker.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';

const _warmOrange = Color(0xFFF28C28);
const _softOrange = Color(0xFFFFF4E8);
const _pageBgColor = Color(0xFFF5F6F8);
const _lineColor = Color(0xFFF0E7DE);
const _textColor = Color(0xFF242424);
const _mutedColor = Color(0xFF77716B);

class DishEditPage extends StatefulWidget {
  const DishEditPage({super.key, this.dish, required this.categories});

  final DishInfo? dish;
  final List<CategoryInfo> categories;

  @override
  State<DishEditPage> createState() => _DishEditPageState();
}

class _DishEditPageState extends State<DishEditPage> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _priceController = TextEditingController();
  final _stockController = TextEditingController();
  final _imagePicker = ImagePicker();

  late int _categoryId;
  late bool _isAvailable;
  String? _imageUrl;
  bool _saving = false;
  final List<_OptionGroupDraft> _optionGroups = [];

  bool get _isEditing => widget.dish != null;

  @override
  void initState() {
    super.initState();
    final dish = widget.dish;
    _nameController.text = dish?.name ?? '';
    _descriptionController.text = dish?.description ?? '';
    _priceController.text = dish == null ? '' : dish.price.toStringAsFixed(2);
    _stockController.text = dish == null ? '999' : '${dish.stock}';
    _categoryId = dish?.categoryId ?? widget.categories.first.id;
    _isAvailable = dish?.isOnSale ?? true;
    _imageUrl = dish?.imageUrl;
    _optionGroups.addAll(
      dish?.optionGroups.map(_OptionGroupDraft.fromInfo) ?? const [],
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    _descriptionController.dispose();
    _priceController.dispose();
    _stockController.dispose();
    for (final group in _optionGroups) {
      group.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _pageBgColor,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: _textColor,
        elevation: 0,
        title: Text(
          _isEditing ? '编辑菜品' : '新增菜品',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _buildImagePicker(),
            const SizedBox(height: 16),

            // 基础信息区
            _BaseCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle(title: '基本信息', icon: Icons.info_outline),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '菜品名称',
                    child: TextFormField(
                      controller: _nameController,
                      decoration: _inputDecoration('例如：招牌牛肉饭'),
                      validator: (value) =>
                          (value == null || value.trim().isEmpty)
                          ? '请填写菜名'
                          : null,
                    ),
                  ),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '所属分类',
                    child: DropdownButtonFormField<int>(
                      initialValue: _categoryId,
                      decoration: _inputDecoration('请选择分类'),
                      icon: const Icon(Icons.expand_more, color: _mutedColor),
                      items: widget.categories
                          .map(
                            (c) => DropdownMenuItem(
                              value: c.id,
                              child: Text(c.name),
                            ),
                          )
                          .toList(),
                      onChanged: (value) {
                        if (value != null) setState(() => _categoryId = value);
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: _LabeledField(
                          label: '售卖价格',
                          child: TextFormField(
                            controller: _priceController,
                            keyboardType: const TextInputType.numberWithOptions(
                              decimal: true,
                            ),
                            decoration: _inputDecoration(
                              '0.00',
                            ).copyWith(prefixText: '¥ '),
                            validator: (value) {
                              final price = double.tryParse(value ?? '');
                              return (price == null || price < 0)
                                  ? '有效价格'
                                  : null;
                            },
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: _LabeledField(
                          label: '当前库存',
                          child: TextFormField(
                            controller: _stockController,
                            keyboardType: TextInputType.number,
                            decoration: _inputDecoration('999'),
                            validator: (value) {
                              final stock = int.tryParse(value ?? '');
                              return (stock == null || stock < 0)
                                  ? '有效库存'
                                  : null;
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '菜品描述 (选填)',
                    child: TextFormField(
                      controller: _descriptionController,
                      maxLines: 3,
                      decoration: _inputDecoration('描述一下菜品的特色或用料...'),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // 规格属性区
            _BaseCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const _SectionTitle(title: '菜品属性', icon: Icons.tune),
                      const Spacer(),
                      TextButton.icon(
                        onPressed: _addOptionGroup,
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('新增属性组'),
                        style: TextButton.styleFrom(
                          foregroundColor: _warmOrange,
                        ),
                      ),
                    ],
                  ),
                  if (_optionGroups.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 12),
                      child: Text(
                        '暂无特殊属性，如需设置口味、加料等，请点击上方添加。',
                        style: TextStyle(color: _mutedColor, fontSize: 13),
                      ),
                    ),
                  for (final group in _optionGroups)
                    _OptionGroupEditor(
                      group: group,
                      onChanged: () => setState(() {}),
                      onRemove: () {
                        setState(() {
                          _optionGroups.remove(group);
                          group.dispose();
                        });
                      },
                    ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // 售卖状态区
            _BaseCard(
              child: SwitchListTile(
                contentPadding: EdgeInsets.zero,
                value: _isAvailable,
                activeColor: Colors.white,
                activeTrackColor: _warmOrange,
                title: const Text(
                  '上架状态',
                  style: TextStyle(
                    color: _textColor,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                subtitle: Text(
                  _isAvailable ? '顾客可点单' : '已隐藏，顾客不可见',
                  style: const TextStyle(color: _mutedColor, fontSize: 13),
                ),
                onChanged: (value) => setState(() => _isAvailable = value),
              ),
            ),
          ],
        ),
      ),
      // 底部悬浮操作栏
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: Colors.white,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 12,
                offset: const Offset(0, -4),
              ),
            ],
          ),
          child: Row(
            children: [
              if (_isEditing) ...[
                Expanded(
                  flex: 1,
                  child: OutlinedButton(
                    onPressed: _saving ? null : _delete,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.redAccent,
                      side: const BorderSide(color: Colors.redAccent),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    child: const Text(
                      '删除',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
              ],
              Expanded(
                flex: 2,
                child: FilledButton(
                  onPressed: _saving ? null : _save,
                  style: FilledButton.styleFrom(
                    backgroundColor: _warmOrange,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    _saving ? '保存中...' : '保存菜品',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildImagePicker() {
    final url = _imageUrl;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: _uploadImage,
      child: Container(
        height: 180,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: url != null ? Colors.transparent : _lineColor,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: url != null && url.isNotEmpty
            ? Image.network(
                _absoluteImageUrl(url),
                fit: BoxFit.cover,
                loadingBuilder: (context, child, loadingProgress) {
                  if (loadingProgress == null) return child;
                  return const Center(
                    child: CircularProgressIndicator(color: _warmOrange),
                  );
                },
                errorBuilder: (_, __, ___) => _buildPlaceholder(),
              )
            : _buildPlaceholder(),
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: const BoxDecoration(
            color: _softOrange,
            shape: BoxShape.circle,
          ),
          child: const Icon(
            Icons.add_photo_alternate_outlined,
            color: _warmOrange,
            size: 32,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          '点击上传高清菜品图',
          style: TextStyle(color: _mutedColor, fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  Future<void> _uploadImage() async {
    final image = await _imagePicker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1200,
      imageQuality: 85,
    );
    if (image == null) return;

    setState(() => _saving = true);
    try {
      final shop = await shopApi.fetchMyShop();
      final url = await shopApi.uploadMenuImage(
        shopId: shop.id,
        filePath: image.path,
      );
      if (mounted) {
        setState(() => _imageUrl = url);
        _showSnackBar('图片上传成功');
      }
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);

    final body = {
      'categoryId': _categoryId,
      'name': _nameController.text.trim(),
      'description': _descriptionController.text.trim(),
      'price': double.parse(_priceController.text),
      'imageUrl': _imageUrl,
      'stock': int.parse(_stockController.text),
      'status': _isAvailable ? 'ON_SALE' : 'OFF_SALE',
      'sortOrder': widget.dish?.sortOrder ?? 0,
      'optionGroups': [
        for (final group in _optionGroups)
          if (group.name.text.trim().isNotEmpty)
            {
              'name': group.name.text.trim(),
              'required': group.minSelect > 0,
              'selectionType': group.selectionType,
              'minSelect': group.minSelect,
              'maxSelect': group.selectionType == 'SINGLE'
                  ? 1
                  : int.tryParse(group.maxSelect.text),
              'sortOrder': _optionGroups.indexOf(group),
              'options': [
                for (final option in group.options)
                  if (option.name.text.trim().isNotEmpty)
                    {
                      'name': option.name.text.trim(),
                      'priceDelta': double.tryParse(option.price.text) ?? 0,
                      'sortOrder': group.options.indexOf(option),
                    },
              ],
            },
      ],
    };

    try {
      if (_isEditing) {
        await shopApi.updateDish(widget.dish!.id, body);
      } else {
        await shopApi.createDish(body);
      }
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _delete() async {
    if (widget.dish == null) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除菜品'),
        content: Text('删除后无法恢复，确定删除「${widget.dish!.name}」吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消', style: TextStyle(color: _mutedColor)),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() => _saving = true);
    try {
      await shopApi.deleteDish(widget.dish!.id);
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (error) {
      if (mounted) _showSnackBar(error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showSnackBar(String message) {
    AppNotice.show(context, message);
  }

  void _addOptionGroup() {
    setState(() => _optionGroups.add(_OptionGroupDraft()));
  }
}

// --------------------------------------------------------------------------
// 视图组件抽取
// --------------------------------------------------------------------------

class _BaseCard extends StatelessWidget {
  const _BaseCard({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.02),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: child,
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, required this.icon});
  final String title;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 20, color: _warmOrange),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            color: _textColor,
            fontSize: 16,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    );
  }
}

class _LabeledField extends StatelessWidget {
  const _LabeledField({required this.label, required this.child});
  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: _textColor,
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
}

class _OptionGroupEditor extends StatelessWidget {
  const _OptionGroupEditor({
    required this.group,
    required this.onChanged,
    required this.onRemove,
  });
  final _OptionGroupDraft group;
  final VoidCallback onChanged;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFAFAFA),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _lineColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: group.name,
                  decoration: _inputDecoration('属性名称，如：辣度 / 加配菜'),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: _lineColor),
                ),
                child: IconButton(
                  onPressed: onRemove,
                  icon: const Icon(
                    Icons.delete_outline,
                    color: Colors.redAccent,
                    size: 20,
                  ),
                  tooltip: '删除此属性组',
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  initialValue: group.selectionType,
                  decoration: _inputDecoration('选择方式'),
                  icon: const Icon(Icons.expand_more, color: _mutedColor),
                  items: const [
                    DropdownMenuItem(value: 'SINGLE', child: Text('单选')),
                    DropdownMenuItem(value: 'MULTIPLE', child: Text('多选')),
                  ],
                  onChanged: (value) {
                    if (value == null) return;
                    group.selectionType = value;
                    if (value == 'SINGLE' && group.minSelect > 1)
                      group.minSelect = 1;
                    onChanged();
                  },
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: DropdownButtonFormField<int>(
                  initialValue: group.minSelect,
                  decoration: _inputDecoration('必选设置'),
                  icon: const Icon(Icons.expand_more, color: _mutedColor),
                  items: const [
                    DropdownMenuItem(value: 0, child: Text('可不选')),
                    DropdownMenuItem(value: 1, child: Text('必选项')),
                  ],
                  onChanged: (value) {
                    if (value != null) {
                      group.minSelect = value;
                      onChanged();
                    }
                  },
                ),
              ),
            ],
          ),
          if (group.selectionType == 'MULTIPLE') ...[
            const SizedBox(height: 12),
            TextField(
              controller: group.maxSelect,
              keyboardType: TextInputType.number,
              decoration: _inputDecoration('最多可选几项 (留空不限)'),
            ),
          ],
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Divider(height: 1, color: _lineColor),
          ),
          for (final option in group.options)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                children: [
                  Expanded(
                    flex: 3,
                    child: TextField(
                      controller: option.name,
                      decoration: _inputDecoration('子选项名'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: option.price,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: _inputDecoration(
                        '加价',
                      ).copyWith(prefixText: '+¥'),
                    ),
                  ),
                  IconButton(
                    onPressed: () {
                      group.options.remove(option);
                      option.dispose();
                      onChanged();
                    },
                    icon: const Icon(Icons.remove_circle, color: _mutedColor),
                  ),
                ],
              ),
            ),
          TextButton.icon(
            onPressed: () {
              group.options.add(_OptionItemDraft());
              onChanged();
            },
            icon: const Icon(Icons.add_circle_outline, size: 18),
            label: const Text('添加子选项'),
            style: TextButton.styleFrom(foregroundColor: _warmOrange),
          ),
        ],
      ),
    );
  }
}

// --------------------------------------------------------------------------
// 状态管理类与工具方法
// --------------------------------------------------------------------------

class _OptionGroupDraft {
  _OptionGroupDraft();
  factory _OptionGroupDraft.fromInfo(DishOptionGroupInfo info) {
    return _OptionGroupDraft()
      ..name.text = info.name
      ..selectionType = info.selectionType
      ..minSelect = info.minSelect
      ..maxSelect.text = info.maxSelect?.toString() ?? ''
      ..options.addAll(info.options.map(_OptionItemDraft.fromInfo));
  }

  final TextEditingController name = TextEditingController();
  final TextEditingController maxSelect = TextEditingController();
  String selectionType = 'SINGLE';
  int minSelect = 0;
  final List<_OptionItemDraft> options = [];

  void dispose() {
    name.dispose();
    maxSelect.dispose();
    for (final option in options) {
      option.dispose();
    }
  }
}

class _OptionItemDraft {
  _OptionItemDraft();
  factory _OptionItemDraft.fromInfo(DishOptionItemInfo info) {
    return _OptionItemDraft()
      ..name.text = info.name
      ..price.text = info.priceDelta.toStringAsFixed(2);
  }

  final TextEditingController name = TextEditingController();
  final TextEditingController price = TextEditingController(text: '0');

  void dispose() {
    name.dispose();
    price.dispose();
  }
}

InputDecoration _inputDecoration(String hintText) {
  return InputDecoration(
    hintText: hintText,
    hintStyle: const TextStyle(color: Color(0xFFB8AFA7)),
    filled: true,
    fillColor: const Color(0xFFF9FAFB),
    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: Color(0xFFE8E8E8)),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: _warmOrange),
    ),
    errorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: Colors.redAccent),
    ),
    focusedErrorBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: Colors.redAccent),
    ),
  );
}

String _absoluteImageUrl(String url) {
  if (url.startsWith('http')) return url;
  final api = Uri.parse(
    const String.fromEnvironment(
      'SHOP_API_BASE_URL',
      defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
    ),
  );
  return api.replace(path: url).toString();
}
