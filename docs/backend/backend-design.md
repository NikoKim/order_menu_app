# 后端设计总览

## 1. 目标

后端为顾客端 `customer_frontend` 和商家端 `shop_frontend` 提供同一套 Spring Boot API。系统不接真实支付，只完成点餐、订单流转、商家维护菜品、顾客留言建议、图片上传与静态访问。

## 2. 模块拆分

后端按业务边界拆成以下模块：

- 认证与用户：注册、登录、JWT、当前用户、修改资料
- 商家与菜单：店铺、菜品分类、菜品、上下架、顾客浏览
- 订单：下单、订单列表、订单详情、取消、接单、完成
- 留言建议：顾客提交、顾客查看、商家查看和回复
- 文件上传：头像、商家图片、菜品图片、静态资源映射
- 基础设施：统一响应、异常处理、参数校验、分页、CORS、环境配置

## 3. 推荐后端结构

```text
backend
  src/main/java/com/order/menu
    OrderMenuApplication.java
    common
      ApiResponse.java
      PageResponse.java
      BusinessException.java
      GlobalExceptionHandler.java
      ErrorCode.java
    config
      WebConfig.java
      SecurityConfig.java
      FileStorageProperties.java
    security
      JwtTokenProvider.java
      JwtAuthenticationFilter.java
      LoginUser.java
    auth
      AuthController.java
      AuthService.java
      dto
    user
      UserController.java
      UserService.java
      UserRepository.java
      entity
      dto
    shop
    menu
    order
    feedback
    upload
  src/main/resources
    application.yml
    application-dev.yml
    application-prod.yml
    db/schema.sql
    db/data.sql
```

## 4. 设计文档索引

- `auth-user-design.md`：认证与用户模块
- `shop-menu-design.md`：商家、分类、菜品模块
- `order-feedback-upload-design.md`：订单、留言、上传模块
- `infrastructure-design.md`：工程基础设施和部署配置

## 5. 实现顺序

1. 搭建 Spring Boot 工程、配置 MySQL、统一响应和异常处理。
2. 建库建表，完成数据库连接和基础实体。
3. 实现认证、JWT、安全拦截和当前用户。
4. 实现商家、分类、菜品的读写接口。
5. 实现图片上传和静态资源访问。
6. 实现顾客下单、订单查询、商家接单和完成。
7. 实现留言建议提交、查看和回复。
8. 与顾客端、商家端 Flutter 页面逐步联调。

## 6. 关键约定

- API 前缀统一为 `/api`。
- 登录后使用 `Authorization: Bearer {token}`。
- 金额以后端重新计算为准，前端传菜品和数量，不传最终可信总价。
- 图片数据库字段存 URL，不存二进制文件。
- 商家端接口必须校验当前 `SHOP` 用户只能操作自己的店铺数据。
- 顾客端接口必须校验当前 `CUSTOMER` 用户只能访问自己的订单和留言。

