import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models/shop_api_models.dart';
import '../services/shop_api_service.dart';
import '../shop_mock_data.dart';

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
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: textColor,
        elevation: 0,
        title: Text(_isEditing ? '编辑菜品' : '新增菜品'),
        actions: [
          if (_isEditing)
            IconButton(
              tooltip: '删除菜品',
              onPressed: _saving ? null : _delete,
              icon: const Icon(Icons.delete_outline),
              color: Colors.redAccent,
            ),
        ],
      ),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _ImagePickerCard(imageUrl: _imageUrl, onTap: _uploadImage),
              const SizedBox(height: 12),
              _Section(
                children: [
                  Row(
                    children: [
                      const Expanded(
                        child: Text(
                          '菜品属性',
                          style: TextStyle(
                            color: textColor,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      TextButton.icon(
                        onPressed: _addOptionGroup,
                        icon: const Icon(Icons.add),
                        label: const Text('新增属性'),
                      ),
                    ],
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
              const SizedBox(height: 12),
              _Section(
                children: [
                  _LabeledField(
                    label: '菜名',
                    child: TextFormField(
                      controller: _nameController,
                      decoration: _inputDecoration('请输入菜品名称'),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return '请填写菜名';
                        }
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '分类',
                    child: DropdownButtonFormField<int>(
                      initialValue: _categoryId,
                      decoration: _inputDecoration('请选择分类'),
                      borderRadius: BorderRadius.circular(8),
                      items: [
                        for (final category in widget.categories)
                          DropdownMenuItem<int>(
                            value: category.id,
                            child: Text(category.name),
                          ),
                      ],
                      onChanged: (value) {
                        if (value != null) {
                          setState(() => _categoryId = value);
                        }
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '价格',
                    child: TextFormField(
                      controller: _priceController,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: _inputDecoration(
                        '请输入价格',
                      ).copyWith(prefixText: '¥ '),
                      validator: (value) {
                        final price = double.tryParse(value ?? '');
                        if (price == null || price < 0) {
                          return '请输入有效价格';
                        }
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '库存',
                    child: TextFormField(
                      controller: _stockController,
                      keyboardType: TextInputType.number,
                      decoration: _inputDecoration('请输入库存数量'),
                      validator: (value) {
                        final stock = int.tryParse(value ?? '');
                        if (stock == null || stock < 0) {
                          return '请输入有效库存';
                        }
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                  _LabeledField(
                    label: '描述',
                    child: TextFormField(
                      controller: _descriptionController,
                      maxLines: 3,
                      decoration: _inputDecoration('请输入菜品描述'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              _Section(
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _isAvailable,
                    activeThumbColor: warmOrange,
                    title: const Text(
                      '状态',
                      style: TextStyle(
                        color: textColor,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    subtitle: Text(
                      _isAvailable ? '上架中' : '已下架',
                      style: const TextStyle(color: mutedColor),
                    ),
                    onChanged: (value) {
                      setState(() => _isAvailable = value);
                    },
                  ),
                ],
              ),
              const SizedBox(height: 24),
              SizedBox(
                height: 48,
                child: FilledButton(
                  onPressed: _saving ? null : _save,
                  style: FilledButton.styleFrom(
                    backgroundColor: warmOrange,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    _saving ? '保存中...' : '保存',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _uploadImage() async {
    final image = await _imagePicker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1200,
      imageQuality: 85,
    );
    if (image == null) {
      return;
    }

    setState(() => _saving = true);
    try {
      final shop = await shopApi.fetchMyShop();
      final url = await shopApi.uploadMenuImage(
        shopId: shop.id,
        filePath: image.path,
      );
      if (mounted) {
        setState(() => _imageUrl = url);
        _showSnackBar('图片已上传');
      }
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

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
      if (mounted) {
        Navigator.pop(context, true);
      }
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _delete() async {
    final dish = widget.dish;
    if (dish == null) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除菜品'),
        content: Text('确定删除「${dish.name}」吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }

    setState(() => _saving = true);
    try {
      await shopApi.deleteDish(dish.id);
      if (mounted) {
        Navigator.pop(context, true);
      }
    } on ApiException catch (error) {
      if (mounted) {
        _showSnackBar(error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), behavior: SnackBarBehavior.floating),
      );
  }

  void _addOptionGroup() {
    setState(() {
      _optionGroups.add(_OptionGroupDraft());
    });
  }
}

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
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFCF8),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: lineColor),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: group.name,
                  decoration: _inputDecoration('例如：辣度 / 加菜'),
                ),
              ),
              IconButton(onPressed: onRemove, icon: const Icon(Icons.close)),
            ],
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: group.selectionType,
            decoration: _inputDecoration('选择方式'),
            items: const [
              DropdownMenuItem(value: 'SINGLE', child: Text('单选')),
              DropdownMenuItem(value: 'MULTIPLE', child: Text('多选')),
            ],
            onChanged: (value) {
              if (value == null) return;
              group.selectionType = value;
              if (value == 'SINGLE' && group.minSelect > 1) {
                group.minSelect = 1;
              }
              onChanged();
            },
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              FilterChip(
                label: const Text('可不选'),
                selected: group.minSelect == 0,
                onSelected: (_) {
                  group.minSelect = 0;
                  onChanged();
                },
              ),
              FilterChip(
                label: const Text('至少选1个'),
                selected: group.minSelect == 1,
                onSelected: (_) {
                  group.minSelect = 1;
                  onChanged();
                },
              ),
              if (group.selectionType == 'MULTIPLE')
                SizedBox(
                  width: 130,
                  child: TextField(
                    controller: group.maxSelect,
                    keyboardType: TextInputType.number,
                    decoration: _inputDecoration('最多选几个'),
                  ),
                ),
            ],
          ),
          for (final option in group.options)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: option.name,
                      decoration: _inputDecoration('选项名'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  SizedBox(
                    width: 92,
                    child: TextField(
                      controller: option.price,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: _inputDecoration('加价'),
                    ),
                  ),
                  IconButton(
                    onPressed: () {
                      group.options.remove(option);
                      option.dispose();
                      onChanged();
                    },
                    icon: const Icon(Icons.remove_circle_outline),
                  ),
                ],
              ),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () {
                group.options.add(_OptionItemDraft());
                onChanged();
              },
              icon: const Icon(Icons.add),
              label: const Text('添加选项'),
            ),
          ),
        ],
      ),
    );
  }
}

class _ImagePickerCard extends StatelessWidget {
  const _ImagePickerCard({required this.imageUrl, required this.onTap});

  final String? imageUrl;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final url = imageUrl;
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: onTap,
      child: Container(
        height: 144,
        decoration: BoxDecoration(
          color: softOrange,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: lineColor),
          image: url == null || url.isEmpty
              ? null
              : DecorationImage(
                  image: NetworkImage(_absoluteImageUrl(url)),
                  fit: BoxFit.cover,
                ),
        ),
        child: const Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.add_photo_alternate_outlined, color: warmOrange),
              SizedBox(height: 8),
              Text('点击上传菜品图', style: TextStyle(color: mutedColor)),
            ],
          ),
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: lineColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
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
          style: const TextStyle(color: textColor, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
}

InputDecoration _inputDecoration(String hintText) {
  return InputDecoration(
    hintText: hintText,
    filled: true,
    fillColor: const Color(0xFFFFFCF8),
    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: lineColor),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: const BorderSide(color: warmOrange),
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
  if (url.startsWith('http')) {
    return url;
  }
  final api = Uri.parse(
    const String.fromEnvironment(
      'SHOP_API_BASE_URL',
      defaultValue: 'https://order-menu-backend.zsj-unknown.top/api',
    ),
  );
  return api.replace(path: url).toString();
}
