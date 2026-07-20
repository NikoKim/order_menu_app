# Order Menu App 后端接口文档（网页前端开发版）

- **依据分支**：`feature/20260715_动态模块`
- **生成日期**：2026-07-17
- **默认线上 Base URL**：`https://order-menu-backend.zsj-unknown.top`
- **本地默认 Base URL**：`http://localhost:8080`
- **后端技术**：Spring Boot + Spring Security + JWT + MyBatis-Plus + WebSocket

> 本文档按后端 Controller、DTO、SecurityConfig、全局异常处理与 WebSocket 实现整理。服务层中的条件权限仍可能让“已登录”的接口返回 403/409，请前端统一处理错误响应。

## 1. 全局约定

### 1.1 认证
除标记为“公开”的接口外，请发送：
```http
Authorization: Bearer <JWT>
Content-Type: application/json
```
JWT 默认有效期为 604800 秒（7 天）。浏览器 WebSocket 推荐使用查询参数传 token。

### 1.2 统一响应
```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```
失败示例：
```json
{
  "code": 401,
  "message": "未登录或登录已过期",
  "data": null
}
```
前端判定成功应使用 `code === 0`，同时保留 HTTP 状态码处理。

### 1.3 分页
普通分页 `data`：`records/page/pageSize/total`；动态分页额外返回 `hasMore`。页码从 1 开始。

### 1.4 错误码
| HTTP / code | 含义 | 前端建议 |
|---:|---|---|
| 400 | 参数错误或业务请求非法 | 显示 message，表单定位字段 |
| 401 | 未登录或登录过期 | 清理 token，跳转登录 |
| 403 | 角色或资源权限不足 | 显示 message，不要盲目重试 |
| 404 | 资源不存在 | 展示空状态或返回上一页 |
| 405 | 方法不支持 | 检查 method |
| 409 | 业务状态冲突 | 刷新资源后提示用户 |
| 413 | 上传过大 | 压缩/减小图片 |
| 415 | 媒体类型不支持 | 检查 Content-Type 和扩展名 |
| 500 | 服务器错误 | 提示稍后重试并记录请求信息 |

### 1.5 状态枚举
| 分类 | 值 | 使用位置 |
|---|---|---|
| 用户角色 | `CUSTOMER, SHOP, ADMIN` | 登录、权限控制 |
| 营业状态 | `OPEN, CLOSED` | Shop.businessStatus |
| 菜品状态 | `ON_SALE, OFF_SALE（数据库删除态另有 DELETED）` | Dish.status |
| 订单状态 | `SUBMITTED, ACCEPTED, COMPLETED, CANCELLED, REJECTED` | Order.status |
| 留言状态 | `PENDING, REPLIED, CLOSED` | Feedback.status |
| 动态可见性 | `PUBLIC, RELATED, PRIVATE` | Moment.visibility |
| 动态状态 | `PUBLISHED, DRAFT` | Moment.status |
| 动态主页可见性 | `PUBLIC, CHAT_FRIENDS, FAMILY, PRIVATE` | User.momentProfileVisibility |
| 会话类型 | `DIRECT, FAMILY_GROUP` | Conversation.type |
| 会话/群状态 | `ACTIVE, DISSOLVED` | Conversation.status |
| 群成员角色 | `OWNER, MEMBER` | ConversationMember.role |
| 群成员状态 | `ACTIVE, PENDING, LEFT, REMOVED` | ConversationMember.memberStatus |
| 消息投递状态 | `DELIVERED, FAILED_BLOCKED` | ChatMessage.deliveryStatus |
| 推送平台 | `ANDROID, IOS, OTHER` | PushDeviceRequest.platform |

### 1.6 图片上传与 URL
- 通用图片扩展名：`jpg/jpeg/png/webp`。Spring 全局单文件上限 30MB、请求上限 32MB。
- 动态图片最终限制：动态约 10MB，评论约 5MB；聊天媒体服务还会执行像素/大小检查。网页端建议上传前统一压缩到 5MB 内。
- 普通图片通过 `/images/**` 暴露；聊天图片必须通过受鉴权的消息图片接口读取，不应把 URL 当成永久公开资源。

## 2. 接口总览

### 2.1 认证
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/auth/customer/register` | 公开 | CustomerRegisterRequest | `RegisterResponse` | 顾客注册 |
| `POST` | `/api/auth/login` | 公开 | LoginRequest | `LoginResponse` | 登录；role 必须与账号角色一致；成功后保存 data.token。 |

### 2.2 用户
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/users/me` | 已登录 | — | `CurrentUserResponse` | 获取当前用户 |
| `PUT` | `/api/users/me` | 已登录 | UpdateMeRequest | `CurrentUserResponse` | 修改当前用户 |

### 2.3 管理员
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/admin/users` | ADMIN | Query: role,page,pageSize,keyword | `PageResponse<UserAccountResponse>` | 账号分页列表 |
| `POST` | `/api/admin/users` | ADMIN | CreateUserRequest | `UserAccountResponse` | 创建顾客或商家账号 |
| `PUT` | `/api/admin/users/{userId}/status` | ADMIN | StatusRequest | `UserAccountResponse` | 启用/禁用账号 |

### 2.4 店铺（公开）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/shops` | 公开 | Query: page,pageSize,keyword,familyCode | `PageResponse<ShopResponse>` | 店铺分页列表 |
| `GET` | `/api/shops/{shopId}` | 公开 | — | `ShopResponse` | 店铺详情 |
| `GET` | `/api/shops/{shopId}/menu` | 公开 | — | `MenuResponse` | 店铺菜单 |

### 2.5 店铺（商家）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/shop/me` | SHOP | — | `ShopResponse` | 获取我的店铺 |
| `PUT` | `/api/shop/me` | SHOP | ShopUpdateRequest | `ShopResponse` | 修改我的店铺 |
| `GET` | `/api/shop/notifications` | SHOP | — | `ShopNotificationResponse` | 商家待处理角标统计 |

### 2.6 分类与菜品（商家）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/shop/categories` | SHOP | — | `List<CategoryResponse>` | 分类列表 |
| `POST` | `/api/shop/categories` | SHOP | CategoryRequest | `CategoryResponse` | 新增分类 |
| `PUT` | `/api/shop/categories/{categoryId}` | SHOP | CategoryRequest | `CategoryResponse` | 修改分类 |
| `DELETE` | `/api/shop/categories/{categoryId}` | SHOP | — | `null` | 删除分类 |
| `GET` | `/api/shop/dishes` | SHOP | Query: page,pageSize,categoryId,keyword,status | `PageResponse<DishResponse>` | 商家菜品分页列表 |
| `POST` | `/api/shop/dishes` | SHOP | DishRequest | `DishResponse` | 新增菜品 |
| `PUT` | `/api/shop/dishes/{dishId}` | SHOP | DishRequest | `DishResponse` | 修改菜品 |
| `PUT` | `/api/shop/dishes/{dishId}/status` | SHOP | DishStatusRequest | `DishResponse` | 修改菜品状态 |
| `DELETE` | `/api/shop/dishes/{dishId}` | SHOP | — | `null` | 删除菜品 |
| `PUT` | `/api/shop/dishes/showcase` | SHOP | JSON 数组 List<Long> | `null` | 设置店铺展示菜品；请求体直接是菜品 ID 数组，例如 [1,2,3]。 |

### 2.7 菜品（公开）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/dishes/{dishId}` | 公开 | — | `DishResponse` | 菜品详情 |

### 2.8 文件上传
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/upload/avatar` | 已登录 | multipart: file | `UploadResponse` | 上传用户头像 |
| `POST` | `/api/upload/shop` | SHOP | multipart: file | `UploadResponse` | 上传店铺图片 |
| `POST` | `/api/upload/menu` | SHOP | multipart: shopId,file | `UploadResponse` | 上传菜品图片 |
| `POST` | `/api/upload/family` | SHOP | multipart: file | `UploadResponse` | 上传家庭图片 |

### 2.9 订单（顾客）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/orders` | CUSTOMER | CreateOrderRequest | `OrderSubmitResponse` | 提交订单 |
| `GET` | `/api/orders/my` | CUSTOMER | Query: page,pageSize,status | `PageResponse<OrderSummaryResponse>` | 我的订单分页列表 |
| `GET` | `/api/orders/{orderId}` | CUSTOMER | — | `OrderDetailResponse` | 顾客订单详情 |
| `PUT` | `/api/orders/{orderId}/cancel` | CUSTOMER | — | `null` | 取消订单 |

### 2.10 订单（商家）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/shop/orders` | SHOP | Query: page,pageSize,status,date | `PageResponse<OrderSummaryResponse>` | 商家订单分页列表 |
| `GET` | `/api/shop/orders/{orderId}` | SHOP | — | `OrderDetailResponse` | 商家订单详情 |
| `PUT` | `/api/shop/orders/{orderId}/accept` | SHOP | — | `null` | 接单 |
| `PUT` | `/api/shop/orders/{orderId}/reject` | SHOP | — | `null` | 拒单 |
| `PUT` | `/api/shop/orders/{orderId}/complete` | SHOP | — | `null` | 完成订单 |

### 2.11 留言反馈（顾客）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/feedback` | CUSTOMER | FeedbackRequest | `FeedbackResponse` | 提交留言 |
| `GET` | `/api/feedback/my` | CUSTOMER | Query: page,pageSize | `PageResponse<FeedbackResponse>` | 我的留言分页列表 |

### 2.12 留言反馈（商家）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/shop/feedback` | SHOP | Query: page,pageSize,status | `PageResponse<FeedbackResponse>` | 店铺留言分页列表 |
| `PUT` | `/api/shop/feedback/{feedbackId}/reply` | SHOP | ReplyRequest | `FeedbackResponse` | 回复留言 |

### 2.13 家庭（顾客）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/families/my` | CUSTOMER | — | `MyFamiliesResponse` | 我的家庭 |
| `POST` | `/api/families/join` | CUSTOMER | JoinFamilyRequest | `FamilyResponse` | 通过家庭号加入 |
| `DELETE` | `/api/families/{shopId}` | CUSTOMER | — | `null` | 退出家庭 |

### 2.14 家庭（商家）
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/shop/family/members` | SHOP | Query: status | `List<FamilyMemberResponse>` | 家庭成员列表 |
| `PUT` | `/api/shop/family/members/{memberId}/approve` | SHOP | — | `FamilyMemberResponse` | 通过家庭成员申请 |
| `DELETE` | `/api/shop/family/members/{memberId}` | SHOP | — | `null` | 拒绝/移除家庭成员 |

### 2.15 消息与通知
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/messages/push-devices` | 已登录 | PushDeviceRequest | `null` | 注册移动推送设备 |
| `DELETE` | `/api/messages/push-devices/{token}` | 已登录 | — | `null` | 注销移动推送设备 |
| `POST` | `/api/messages/direct/users/{userId}` | 已登录 | — | `ConversationResponse` | 创建/获取与用户的私聊 |
| `POST` | `/api/messages/direct/shops/{shopId}` | 已登录 | — | `ConversationResponse` | 创建/获取与商家的私聊 |
| `GET` | `/api/messages/users` | 已登录 | Query: nickname | `List<CustomerSearchResponse>` | 搜索可聊天顾客 |
| `GET` | `/api/messages/conversations` | 已登录 | — | `List<ConversationResponse>` | 会话列表 |
| `GET` | `/api/messages/conversations/{conversationId}/preferences` | 已登录 | — | `ConversationPreferenceResponse` | 获取会话偏好 |
| `PUT` | `/api/messages/conversations/{conversationId}/preferences` | 已登录 | ConversationPreferenceRequest | `ConversationPreferenceResponse` | 修改会话偏好 |
| `PUT` | `/api/messages/conversations/{conversationId}/mute` | 已登录 | SetMuteRequest | `ConversationPreferenceResponse` | 设置会话免打扰 |
| `DELETE` | `/api/messages/conversations/{conversationId}` | 已登录 | Query: guarded,expectedMessageVersion | `ConversationHideResponse` | 隐藏会话 |
| `GET` | `/api/messages/users/{userId}/profile` | 已登录 | Query: conversationId | `ChatUserProfileResponse` | 聊天用户资料 |
| `GET` | `/api/messages/notifications` | 已登录 | — | `NotificationListResponse` | 通知列表 |
| `GET` | `/api/messages/notifications/unread-count` | 已登录 | — | `integer` | 未读通知数 |
| `PUT` | `/api/messages/notifications/{notificationId}/read` | 已登录 | — | `null` | 标记单条通知已读 |
| `PUT` | `/api/messages/notifications/read-all` | 已登录 | — | `null` | 全部通知已读 |
| `GET` | `/api/messages/conversations/{conversationId}/messages` | 已登录 | — | `ConversationMessagesResponse` | 会话消息详情 |
| `GET` | `/api/messages/blocks/users/{userId}` | 已登录 | — | `ChatBlockResponse` | 获取拉黑状态 |
| `POST` | `/api/messages/blocks/users/{userId}` | 已登录 | — | `null` | 拉黑用户 |
| `DELETE` | `/api/messages/blocks/users/{userId}` | 已登录 | — | `null` | 取消拉黑 |
| `POST` | `/api/messages/conversations/{conversationId}/messages` | 已登录 | SendMessageRequest | `ChatMessageResponse` | 发送文本消息 |
| `POST` | `/api/messages/conversations/{conversationId}/images` | 已登录 | multipart: image,content?,quotedMessageId? | `ChatMessageResponse` | 发送图片消息 |
| `GET` | `/api/messages/conversations/{conversationId}/images/{filename}` | 已登录 | — | `binary image` | 读取聊天图片二进制 |
| `POST` | `/api/messages/conversations/{conversationId}/messages/{messageId}/recall` | 已登录 | — | `ChatMessageResponse` | 撤回消息 |
| `DELETE` | `/api/messages/conversations/{conversationId}/messages/{messageId}` | 已登录 | — | `null` | 删除自己的消息视图 |

### 2.16 家庭群聊
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/messages/family-groups` | 已登录（业务上商家） | — | `FamilyGroupResponse` | 创建家庭群聊 |
| `GET` | `/api/messages/family-groups/{conversationId}` | 已登录 | — | `FamilyGroupResponse` | 家庭群聊详情 |
| `GET` | `/api/messages/family-groups/by-shop/{shopId}` | 已登录 | — | `FamilyGroupResponse` | 按店铺获取家庭群聊 |
| `PUT` | `/api/messages/family-groups/{conversationId}` | 群主 | FamilyGroupProfileRequest | `FamilyGroupResponse` | 修改家庭群聊资料 |
| `POST` | `/api/messages/family-groups/{conversationId}/dissolve` | 群主 | — | `null` | 解散家庭群聊 |
| `POST` | `/api/messages/family-groups/{conversationId}/restore` | 群主 | — | `FamilyGroupResponse` | 恢复家庭群聊 |
| `POST` | `/api/messages/family-groups/{conversationId}/join` | 家庭成员 | — | `null` | 重新加入/申请加入群聊 |
| `POST` | `/api/messages/family-groups/{conversationId}/approve-membership` | 家庭成员 | — | `null` | 用户接受群聊邀请 |
| `POST` | `/api/messages/family-groups/{conversationId}/decline-membership` | 家庭成员 | — | `null` | 用户拒绝群聊邀请 |
| `POST` | `/api/messages/family-groups/{conversationId}/members/{userId}/approve` | 群主 | — | `null` | 群主通过成员申请 |
| `POST` | `/api/messages/family-groups/{conversationId}/members/{userId}/reject` | 群主 | — | `null` | 群主拒绝成员申请 |
| `POST` | `/api/messages/family-groups/{conversationId}/leave` | 群成员 | — | `null` | 退出家庭群聊 |
| `POST` | `/api/messages/family-groups/{conversationId}/members/{userId}/remove` | 群主 | — | `null` | 群主移除群成员 |
| `PUT` | `/api/messages/family-groups/{conversationId}/mute` | 群成员 | SetMuteRequest | `null` | 设置家庭群聊免打扰 |

### 2.17 消息分享
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/messages/dish-shares` | 已登录 | ShareDishRequest | `ChatMessageResponse` | 分享菜品到聊天 |
| `POST` | `/api/messages/order-shares` | 已登录 | ShareOrderRequest | `ChatMessageResponse` | 分享订单到聊天 |
| `POST` | `/api/messages/shop-shares` | 已登录 | ShareShopRequest | `ChatMessageResponse` | 分享店铺到聊天 |
| `POST` | `/api/messages/moment-shares` | 已登录 | MomentShareAliasRequest | `ChatMessageResponse` | 分享动态或评论到聊天 |

### 2.18 群精华
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/messages/family-groups/{conversationId}/essences/{messageId}` | 群主 | — | `EssenceItemResponse` | 设为精华 |
| `DELETE` | `/api/messages/family-groups/{conversationId}/essences/{messageId}` | 群主 | — | `null` | 取消精华 |
| `GET` | `/api/messages/family-groups/{conversationId}/essences` | 群成员 | — | `EssenceListResponse` | 精华列表 |
| `GET` | `/api/messages/family-groups/{conversationId}/essences/latest-unread` | 群成员 | — | `ChatMessageResponse\|null` | 最新未读精华 |
| `PUT` | `/api/messages/family-groups/{conversationId}/essences/{messageId}/pin` | 群主 | EssenceToggleRequest | `EssenceItemResponse` | 置顶/取消置顶精华 |
| `PUT` | `/api/messages/family-groups/{conversationId}/essences/{messageId}/like` | 群成员 | LikeToggleRequest | `EssenceItemResponse` | 点赞/取消点赞精华 |
| `PUT` | `/api/messages/family-groups/{conversationId}/essences/read` | 群成员 | EssenceReadRequest（可空） | `null` | 标记精华已读 |

### 2.19 动态
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/moments` | 已登录 | Query: feed,page,pageSize\|size,consumeExposure,keyword,authorId,topicId | `MomentPageResponse` | 动态列表/搜索/用户/话题统一入口；优先级：keyword > authorId > topicId > feed。 |
| `GET` | `/api/moments/{id}` | 已登录 | — | `MomentResponse` | 动态详情 |
| `GET` | `/api/moments/{id}/availability` | 已登录 | — | `AvailabilityResponse` | 动态可用性检查 |
| `POST` | `/api/moments` | 已登录 | UpsertMomentRequest | `MomentResponse` | 发布动态/保存草稿 |
| `PUT` | `/api/moments/{id}` | 作者 | UpsertMomentRequest | `MomentResponse` | 编辑动态/草稿 |
| `DELETE` | `/api/moments/{id}` | 作者 | — | `null` | 删除动态 |
| `GET` | `/api/moments/drafts` | 已登录 | Query: page,pageSize | `MomentPageResponse` | 我的草稿分页列表 |

### 2.20 动态互动
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `PUT` | `/api/moments/{id}/like` | 已登录 | ToggleRequest（可空；空表示点赞） | `MomentResponse` | 设置点赞状态 |
| `POST` | `/api/moments/{id}/likes` | 已登录 | — | `MomentResponse` | 点赞（REST 别名） |
| `DELETE` | `/api/moments/{id}/likes` | 已登录 | — | `MomentResponse` | 取消点赞（REST 别名） |
| `PUT` | `/api/moments/{id}/reactions` | 已登录 | ReactionRequest | `MomentResponse` | 设置表情回应状态 |
| `POST` | `/api/moments/{id}/reactions` | 已登录 | ReactionRequest | `MomentResponse` | 添加表情回应 |
| `DELETE` | `/api/moments/{id}/reactions` | 已登录 | Query: emoji | `MomentResponse` | 删除表情回应 |

### 2.21 动态评论
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/moments/{id}/comments` | 已登录 | Query: page,pageSize | `MomentCommentPageResponse` | 评论分页列表 |
| `POST` | `/api/moments/{id}/comments` | 已登录 | MomentCommentRequest | `CommentResponse` | 发表评论/回复 |
| `GET` | `/api/moments/{momentId}/comments/{commentId}/replies` | 已登录 | Query: page,pageSize | `MomentCommentPageResponse` | 评论回复分页列表 |
| `GET` | `/api/moments/comments/{commentId}/replies` | 已登录 | Query: page,pageSize | `MomentCommentPageResponse` | 评论回复分页列表（短路径别名） |
| `DELETE` | `/api/moments/{momentId}/comments/{commentId}` | 评论作者 | — | `null` | 删除评论 |
| `DELETE` | `/api/moments/comments/{commentId}` | 评论作者 | — | `null` | 删除评论（短路径别名） |
| `PUT` | `/api/moments/{momentId}/comments/{commentId}/like` | 已登录 | ToggleRequest（可空） | `CommentResponse` | 设置评论点赞 |
| `PUT` | `/api/moments/comments/{commentId}/like` | 已登录 | ToggleRequest（可空） | `CommentResponse` | 设置评论点赞（短路径别名） |

### 2.22 动态话题
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/moments/topics/suggestions` | 已登录 | Query: query | `List<TopicResponse>` | 话题建议 |
| `GET` | `/api/moments/topics` | 已登录 | Query: query,page,pageSize | `MomentTopicPageResponse` | 话题分页列表 |
| `POST` | `/api/moments/topics` | 已登录 | TopicCreateRequest | `TopicResponse` | 创建/获取话题 |
| `GET` | `/api/moments/topics/{id}` | 已登录 | Query: page,pageSize | `MomentPageResponse` | 话题下动态分页列表 |

### 2.23 动态用户主页与隐私
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/moments/users/{id}` | 已登录 | Query: page,pageSize | `MomentPageResponse` | 用户动态分页列表 |
| `GET` | `/api/moments/users/{id}/summary` | 已登录 | — | `ProfileSummary` | 用户动态主页摘要 |
| `GET` | `/api/moments/users/{id}/privacy` | 已登录 | — | `PreferenceResponse` | 获取对目标用户的动态屏蔽偏好 |
| `PUT` | `/api/moments/users/{id}/privacy` | 已登录 | PreferenceRequest | `PreferenceResponse` | 修改对目标用户的动态屏蔽偏好 |
| `GET` | `/api/moments/privacy` | 已登录 | Query: targetUserId | `PreferenceResponse` | 获取动态屏蔽偏好（别名） |
| `PUT` | `/api/moments/privacy` | 已登录 | Query + PreferenceRequest | `PreferenceResponse` | 修改动态屏蔽偏好（别名） |
| `PUT` | `/api/moments/privacy/profile` | 已登录 | ProfileVisibilityRequest | `ProfileSummary` | 修改自己的动态主页可见性 |
| `GET` | `/api/moments/privacy/profile` | 已登录 | — | `ProfileSummary` | 获取自己的动态主页可见性 |

### 2.24 动态媒体
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/moments/images` | 已登录 | multipart: image | `MomentUploadResponse` | 上传动态图片 |
| `POST` | `/api/moments/comment-images` | 已登录 | multipart: image | `MomentUploadResponse` | 上传评论图片 |

### 2.25 动态分享与未读
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `POST` | `/api/moments/{id}/share` | 已登录 | MomentShareRequest | `ChatMessageResponse` | 分享动态/评论到会话 |
| `GET` | `/api/moments/unread-count` | 已登录 | — | `UnreadResponse` | 相关动态未读数 |
| `PUT` | `/api/moments/read` | 已登录 | MarkReadRequest | `null` | 批量标记动态已读 |

### 2.26 版本
| Method | Path | 权限 | 请求 | `data` 类型 | 说明 |
|---|---|---|---|---|---|
| `GET` | `/api/version/check` | 公开 | Query: appCode,currentVersionCode | `VersionCheckResponse` | 检查客户端更新 |
| `POST` | `/api/version/publish` | 公开（当前 SecurityConfig 行为） | VersionSaveRequest | `null` | 发布新版本；安全提醒：当前 /api/version/** 全部 permitAll，网页后台上线前建议将 publish 限制为 ADMIN。 |

## 3. 关键调用示例

### 3.1 登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "nickname": "demo",
  "password": "123456",
  "role": "CUSTOMER"
}
```
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJ...",
    "user": {
      "id": 9,
      "nickname": "demo",
      "role": "CUSTOMER"
    }
  }
}
```

### 3.2 获取店铺菜单并建立购物车
```http
GET /api/shops/1/menu
```
前端按 `data.categories[].dishes[]` 渲染；规格组选项价格应加到基础价后再计算小计，但最终金额以提交订单后的服务端返回为准。

### 3.3 提交订单
```json
{
  "shopId": 1,
  "remark": "少辣",
  "items": [
    {
      "dishId": 10,
      "quantity": 2,
      "optionItemIds": [
        101,
        105
      ]
    }
  ]
}
```

### 3.4 发送聊天图片（浏览器 FormData）
```ts
const form = new FormData();
form.append("image", file);
form.append("content", text);
if (quotedMessageId) form.append("quotedMessageId", String(quotedMessageId));
await fetch(`${baseUrl}/api/messages/conversations/${conversationId}/images`, {
  method: "POST",
  headers: { Authorization: `Bearer ${token}` }, // 不要手动设置 Content-Type
  body: form,
});
```

### 3.5 发布动态
先调用 `/api/moments/images` 上传图片，取得 URL 后再提交动态：
```json
{
  "title": "今日更新",
  "content": "新增聊天与动态功能",
  "visibility": "PUBLIC",
  "status": "PUBLISHED",
  "imageUrls": [
    "/images/moment/xxx.webp"
  ],
  "topics": [
    "update",
    "聊天"
  ]
}
```

### 3.6 动态评论/回复
- 一级评论：不传 `parentCommentId`、`replyToCommentId`。
- 回复一级评论：二者通常都传一级评论 ID。
- 回复子评论：`parentCommentId` 传根评论 ID，`replyToCommentId` 传目标子评论 ID。

## 4. WebSocket

### 4.1 连接地址
```text
wss://<host>/ws/customer?token=<JWT>
wss://<host>/ws/shop?token=<JWT>
```
顾客端只允许 CUSTOMER，商家端只允许 SHOP；也支持握手 Authorization 头，但浏览器原生 WebSocket 不能自定义头，因此使用 query token。

### 4.2 通用消息格式
```json
{
  "module": "ORDER",
  "action": "ACCEPTED",
  "data": {
    "orderId": 12,
    "status": "ACCEPTED"
  },
  "timestamp": 1784260000000
}
```

| module | action | data 关键字段 | 前端处理 |
|---|---|---|---|
| `SYSTEM` | `PONG` | {} | 客户端发送包含 PING 的文本后返回；该 PONG 实现中没有 timestamp。 |
| `ORDER` | `CREATED` | orderId, orderNo, shopId, customerUserId, status | 推送给商家 |
| `ORDER` | `CANCELLED` | orderId, orderNo, shopId, customerUserId?, status | 推送给商家及顾客 |
| `ORDER` | `ACCEPTED / REJECTED / COMPLETED` | orderId, orderNo, shopId, customerUserId?, status | 推送给商家及顾客 |
| `FEEDBACK` | `CREATED` | feedbackId, shopId, userId, status | 推送给商家 |
| `FEEDBACK` | `REPLIED` | feedbackId, shopId, userId?, status | 推送给商家及顾客 |
| `FAMILY` | `MEMBER_APPLIED` | memberId, shopId, userId, status | 推送给商家 |
| `FAMILY` | `MEMBER_APPROVED / MEMBER_REJECTED` | memberId, shopId, userId?, status | 推送给商家及顾客 |
| `SHOP` | `PROFILE_CHANGED` | shopId | 推送给商家自己的其他连接 |
| `SHOP` | `BUSINESS_STATUS_CHANGED` | shopId, businessStatus, isOpen | 推送给商家 |
| `NOTIFICATION` | `BADGE_CHANGED` | shopId, reason | 商家角标需要重新请求 /api/shop/notifications |
| `NOTIFICATION` | `CREATED` | notificationId?, unreadCount | 通知新增；重新请求通知列表 |
| `NOTIFICATION` | `READ_CHANGED` | unreadCount（服务端事件名以实现为准） | 通知已读变化 |
| `CHAT` | `MESSAGE_CREATED / MESSAGE_UPDATED / MESSAGE_DELETED` | conversationId 等 | 现有 Flutter 客户端按这些 action 触发会话重载 |
| `CHAT` | `CONVERSATION_UPDATED / MEMBER_UPDATED / ESSENCE_*` | conversationId 等 | 群资料、成员或精华发生变化；建议重新拉取对应资源 |
| `MOMENT` | `动态相关 action` | 通常包含 momentId / userId | 现有客户端监听 MOMENT 模块后重新检查第一页；具体 action 应以收到的数据为准 |

前端建议：WebSocket 事件只作为“资源已变化”的信号，收到后按 `module` 重新请求对应 REST 资源；不要假定 data 永远包含完整实体。实现指数退避重连、token 更新和 30~60 秒 PING。

## 5. TypeScript 请求封装建议
```ts
export type ApiResponse<T> = { code: number; message: string; data: T };

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("token");
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`${BASE_URL}${path}`, { ...init, headers });
  const payload = (await res.json()) as ApiResponse<T>;
  if (res.status === 401) { localStorage.removeItem("token"); /* 跳转登录 */ }
  if (!res.ok || payload.code !== 0) throw new Error(payload.message || `HTTP ${res.status}`);
  return payload.data;
}
```

## 6. 数据结构字典

### 6.1 `ApiResponse<T>`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| code | integer | 是 | 成功固定 0；失败常用 HTTP 状态码 | 业务码 |
| message | string | 是 | 成功固定 success | 消息 |
| data | T\|null | 否 | 按接口变化 | 响应数据 |

### 6.2 `PageResponse<T>`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| records | array<T> | 是 | — | 当前页数据 |
| page | integer(int64) | 是 | 从 1 开始 | 页码 |
| pageSize | integer(int64) | 是 | — | 每页数量 |
| total | integer(int64) | 是 | — | 总记录数 |

### 6.3 `MomentPageResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| records | array<MomentResponse> | 是 | — | 动态数据 |
| page | integer(int64) | 是 | 从 1 开始 | 页码 |
| pageSize | integer(int64) | 是 | — | 每页数量 |
| total | integer(int64) | 是 | — | 总记录数 |
| hasMore | boolean | 是 | — | 是否还有下一页 |

### 6.4 `CustomerRegisterRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| nickname | string | 是 | 非空，最大 50 | 昵称 |
| password | string | 是 | 6~72 | 密码 |

### 6.5 `RegisterResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| userId | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |

### 6.6 `LoginRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| nickname | string | 是 | 非空 | 昵称 |
| password | string | 是 | 非空 | 密码 |
| role | string | 是 | CUSTOMER / SHOP / ADMIN | 登录角色 |

### 6.7 `LoginResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| token | string | 是 | JWT | 访问令牌 |
| user | CurrentUserResponse | 是 | — | 当前用户 |

### 6.8 `CurrentUserResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| role | string | 是 | CUSTOMER / SHOP / ADMIN | 角色 |
| avatarUrl | string\|null | 否 | — | 头像 URL |
| phone | string\|null | 否 | — | 电话 |
| allowPrivateChat | boolean | 否 | — | 是否允许私聊 |
| autoJoinFamilyChat | boolean | 否 | — | 家庭通过后是否自动加入群聊 |
| showJoinedFamilies | boolean | 否 | — | 聊天资料是否展示已加入家庭 |
| showPhone | boolean | 否 | — | 聊天资料是否展示电话 |
| momentProfileVisibility | string | 否 | PUBLIC / CHAT_FRIENDS / FAMILY / PRIVATE | 动态主页可见性 |

### 6.9 `UpdateMeRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| nickname | string | 否 | 最大 50 | 昵称 |
| phone | string | 否 | 最大 30 | 电话 |
| avatarUrl | string | 否 | 最大 255 | 头像 URL |
| allowPrivateChat | boolean | 否 | — | 允许私聊 |
| autoJoinFamilyChat | boolean | 否 | — | 自动加入家庭群聊 |
| showJoinedFamilies | boolean | 否 | — | 展示已加入家庭 |
| showPhone | boolean | 否 | — | 展示电话 |
| momentProfileVisibility | string | 否 | 建议使用 PUBLIC/CHAT_FRIENDS/FAMILY/PRIVATE | 动态主页可见性 |

### 6.10 `UserAccountResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| role | string | 是 | — | 角色 |
| phone | string\|null | 否 | — | 电话 |
| status | integer | 是 | 1 启用，通常 0 禁用 | 账号状态 |
| createdAt | string(date-time) | 是 | — | 创建时间 |

### 6.11 `CreateUserRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| nickname | string | 是 | 最大 50 | 昵称 |
| password | string | 是 | 6~72 | 密码 |
| role | string | 是 | CUSTOMER / SHOP | 角色 |
| shopName | string | 否 | 最大 80；SHOP 时建议必填 | 店铺名 |
| phone | string | 否 | 最大 30 | 电话 |

### 6.12 `StatusRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| status | integer | 否 | 通常 0 / 1 | 账号状态 |

### 6.13 `ShopResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 店铺 ID |
| name | string | 是 | — | 店铺名 |
| description | string\|null | 否 | — | 描述 |
| coverImageUrl | string\|null | 否 | — | 封面 |
| address | string\|null | 否 | — | 地址 |
| phone | string\|null | 否 | — | 电话 |
| businessStatus | string | 是 | OPEN / CLOSED | 营业状态 |
| visibilityType | string | 是 | 常见 PUBLIC / FAMILY | 可见范围 |
| familyName | string\|null | 否 | — | 家庭名称 |
| familyCode | string\|null | 否 | 6 位大写字母或数字 | 家庭号 |
| familyJoinMode | string\|null | 否 | 由业务配置决定 | 加入模式 |
| familyImageUrl | string\|null | 否 | — | 家庭图片 |
| previewDishes | array<DishResponse> | 否 | — | 预览菜品 |

### 6.14 `ShopUpdateRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| name | string | 是 | 最大 80 | 店铺名 |
| description | string | 否 | 最大 500 | 描述 |
| coverImageUrl | string | 否 | 最大 255 | 封面 URL |
| address | string | 否 | 最大 255 | 地址 |
| phone | string | 否 | 最大 30 | 电话 |
| businessStatus | string | 否 | OPEN / CLOSED | 营业状态 |
| visibilityType | string | 否 | 常见 PUBLIC / FAMILY | 可见范围 |
| familyName | string | 否 | 最大 80 | 家庭名称 |
| familyJoinMode | string | 否 | — | 家庭加入模式 |
| familyImageUrl | string | 否 | 最大 255 | 家庭图片 URL |

### 6.15 `ShopNotificationResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| pendingOrders | integer(int64) | 是 | — | 待处理订单数 |
| pendingFamilyMembers | integer(int64) | 是 | — | 待审核家庭成员数 |

### 6.16 `MenuResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shop | ShopResponse | 是 | — | 店铺 |
| categories | array<CategoryMenuResponse> | 是 | — | 分类菜单 |

### 6.17 `CategoryMenuResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 分类 ID |
| name | string | 是 | — | 分类名 |
| dishes | array<DishResponse> | 是 | — | 菜品 |

### 6.18 `CategoryResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 分类 ID |
| name | string | 是 | — | 分类名 |
| sortOrder | integer\|null | 否 | — | 排序 |
| status | integer\|null | 否 | — | 状态 |

### 6.19 `CategoryRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| name | string | 是 | 非空，最大 50 | 分类名 |
| sortOrder | integer | 否 | — | 排序 |

### 6.20 `DishResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 菜品 ID |
| shopId | integer(int64) | 是 | — | 店铺 ID |
| categoryId | integer(int64)\|null | 否 | — | 分类 ID |
| name | string | 是 | — | 菜品名 |
| description | string\|null | 否 | — | 描述 |
| price | number(decimal) | 是 | >= 0 | 基础价格 |
| imageUrl | string\|null | 否 | — | 图片 |
| stock | integer\|null | 否 | >= 0 | 库存 |
| salesCount | integer | 是 | — | 销量 |
| status | string | 是 | ON_SALE / OFF_SALE | 菜品状态 |
| sortOrder | integer\|null | 否 | — | 排序 |
| recommendOrder | integer\|null | 否 | — | 推荐展示排序 |
| optionGroups | array<OptionGroupResponse> | 否 | — | 规格组 |

### 6.21 `DishRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| categoryId | integer(int64) | 否 | — | 分类 ID |
| name | string | 是 | 最大 80 | 菜品名 |
| description | string | 否 | 最大 500 | 描述 |
| price | number(decimal) | 是 | >=0 | 基础价格 |
| imageUrl | string | 否 | 最大 255 | 图片 URL |
| stock | integer | 否 | >=0 | 库存 |
| status | string | 否 | ON_SALE / OFF_SALE | 状态 |
| sortOrder | integer | 否 | — | 排序 |
| optionGroups | array<OptionGroupRequest> | 否 | — | 规格组 |

### 6.22 `OptionGroupResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 规格组 ID |
| name | string | 是 | — | 名称 |
| required | boolean | 否 | — | 是否必选 |
| selectionType | string | 否 | 常见 SINGLE / MULTIPLE | 选择类型 |
| minSelect | integer | 否 | — | 最少选择 |
| maxSelect | integer | 否 | — | 最多选择 |
| sortOrder | integer | 否 | — | 排序 |
| options | array<OptionItemResponse> | 是 | — | 选项 |

### 6.23 `OptionGroupRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 否 | 编辑时传 | 规格组 ID |
| name | string | 是 | 最大 50 | 名称 |
| required | boolean | 否 | — | 是否必选 |
| selectionType | string | 否 | 常见 SINGLE / MULTIPLE | 选择类型 |
| minSelect | integer | 否 | — | 最少选择 |
| maxSelect | integer | 否 | — | 最多选择 |
| sortOrder | integer | 否 | — | 排序 |
| options | array<OptionItemRequest> | 否 | — | 选项 |

### 6.24 `OptionItemResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 选项 ID |
| name | string | 是 | — | 名称 |
| priceDelta | number(decimal) | 是 | >=0 | 加价 |
| sortOrder | integer | 否 | — | 排序 |

### 6.25 `OptionItemRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 否 | 编辑时传 | 选项 ID |
| name | string | 是 | 最大 50 | 名称 |
| priceDelta | number(decimal) | 是 | >=0 | 加价 |
| sortOrder | integer | 否 | — | 排序 |

### 6.26 `DishStatusRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| status | string | 是 | ON_SALE / OFF_SALE | 菜品状态 |

### 6.27 `UploadResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| url | string | 是 | — | 可直接保存到业务对象的图片 URL |

### 6.28 `CreateOrderRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shopId | integer(int64) | 是 | — | 店铺 ID |
| remark | string | 否 | 最大 500 | 备注 |
| items | array<CreateOrderItemRequest> | 是 | 非空 | 订单项 |

### 6.29 `CreateOrderItemRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| dishId | integer(int64) | 是 | — | 菜品 ID |
| quantity | integer | 是 | >0 | 数量 |
| optionItemIds | array<integer(int64)> | 否 | — | 选择的规格选项 ID |

### 6.30 `OrderSubmitResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| orderId | integer(int64) | 是 | — | 订单 ID |
| orderNo | string | 是 | — | 订单号 |
| status | string | 是 | SUBMITTED | 状态 |
| totalAmount | number(decimal) | 是 | — | 总金额 |

### 6.31 `OrderSummaryResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 订单 ID |
| orderNo | string | 是 | — | 订单号 |
| shopId | integer(int64) | 是 | — | 店铺 ID |
| shopName | string | 是 | — | 店铺名 |
| status | string | 是 | 订单状态枚举 | 状态 |
| totalAmount | number(decimal) | 是 | — | 总金额 |
| createdAt | string(date-time) | 是 | — | 创建时间 |

### 6.32 `OrderDetailResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 订单 ID |
| orderNo | string | 是 | — | 订单号 |
| shopId | integer(int64) | 是 | — | 店铺 ID |
| shopName | string | 是 | — | 店铺名 |
| customerUserId | integer(int64) | 是 | — | 顾客 ID |
| customerNickname | string | 是 | — | 顾客昵称 |
| customerPhone | string\|null | 否 | — | 顾客电话 |
| customerAvatarUrl | string\|null | 否 | — | 顾客头像 |
| familyCustomer | boolean | 是 | — | 是否家庭顾客 |
| status | string | 是 | 订单状态枚举 | 状态 |
| totalAmount | number(decimal) | 是 | — | 总金额 |
| remark | string\|null | 否 | — | 备注 |
| createdAt | string(date-time) | 是 | — | 创建时间 |
| items | array<OrderItemResponse> | 是 | — | 订单项 |

### 6.33 `OrderItemResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| dishId | integer(int64) | 是 | — | 菜品 ID |
| dishName | string | 是 | — | 菜品名快照 |
| dishImageUrl | string\|null | 否 | — | 图片快照 |
| unitPrice | number(decimal) | 是 | — | 单价 |
| quantity | integer | 是 | — | 数量 |
| subtotalAmount | number(decimal) | 是 | — | 小计 |
| selectedOptions | string\|null | 否 | 服务端序列化文本 | 已选规格 |
| optionAmount | number(decimal) | 否 | — | 规格加价 |

### 6.34 `FeedbackRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shopId | integer(int64) | 否 | 业务上必须能定位店铺 | 店铺 ID |
| content | string | 是 | 最大 1000 | 留言内容 |

### 6.35 `ReplyRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| reply | string | 是 | 最大 1000 | 回复内容 |

### 6.36 `FeedbackResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 留言 ID |
| userId | integer(int64) | 是 | — | 顾客 ID |
| shopId | integer(int64) | 是 | — | 店铺 ID |
| content | string | 是 | — | 留言 |
| reply | string\|null | 否 | — | 商家回复 |
| status | string | 是 | PENDING / REPLIED / CLOSED | 状态 |
| createdAt | string(date-time) | 是 | — | 创建时间 |
| userNickname | string | 是 | — | 顾客昵称 |
| userAvatarUrl | string\|null | 否 | — | 顾客头像 |

### 6.37 `FamilyResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shopId | integer(int64) | 是 | — | 店铺 ID |
| shopName | string | 是 | — | 店铺名 |
| familyName | string | 否 | — | 家庭名 |
| familyCode | string | 是 | 6 位大写字母或数字 | 家庭号 |
| familyImageUrl | string\|null | 否 | — | 家庭图 |
| joinMode | string | 否 | — | 加入模式 |
| memberStatus | string | 是 | PENDING / APPROVED 等 | 成员状态 |

### 6.38 `JoinFamilyRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| familyCode | string | 是 | 正则 [0-9A-Z]{6} | 家庭号 |

### 6.39 `MyFamiliesResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| families | array<FamilyResponse> | 是 | — | 家庭列表 |

### 6.40 `FamilyMemberResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 成员记录 ID |
| userId | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| phone | string\|null | 否 | — | 电话 |
| avatarUrl | string\|null | 否 | — | 头像 |
| status | string | 是 | PENDING / APPROVED | 状态 |

### 6.41 `PushDeviceRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| token | string | 是 | 最大 512 | 推送 token |
| platform | string | 是 | ANDROID / IOS / OTHER（大小写不敏感） | 平台 |

### 6.42 `ConversationResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 会话 ID |
| type | string | 是 | DIRECT / FAMILY_GROUP | 会话类型 |
| name | string | 是 | — | 名称 |
| coverImageUrl | string\|null | 否 | — | 封面 |
| status | string | 是 | ACTIVE / DISSOLVED 等 | 状态 |
| lastMessageAt | string(date-time)\|null | 否 | — | 最后消息时间 |
| messageVersion | integer(int64) | 是 | — | 会话消息版本 |
| directUserId | integer(int64)\|null | 否 | 私聊时有值 | 对方用户 ID |
| directUserNickname | string\|null | 否 | — | 对方昵称 |
| directUserAvatarUrl | string\|null | 否 | — | 对方头像 |
| lastMessageContent | string\|null | 否 | — | 最后消息摘要 |
| unreadCount | integer(int64) | 是 | — | 未读数 |
| muted | boolean | 是 | — | 免打扰 |
| pinned | boolean | 是 | — | 置顶 |
| pinnedAt | string(date-time)\|null | 否 | — | 置顶时间 |
| hidden | boolean | 是 | — | 是否隐藏 |
| mutedBadge | boolean | 是 | — | 免打扰情况下是否仍显示角标 |

### 6.43 `ConversationPreferenceRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| muted | boolean | 否 | — | 免打扰 |
| hidden | boolean | 否 | — | 隐藏 |
| pinned | boolean | 否 | — | 置顶 |

### 6.44 `ConversationPreferenceResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversationId | integer(int64) | 是 | — | 会话 ID |
| muted | boolean | 是 | — | 免打扰 |
| hidden | boolean | 是 | — | 隐藏 |
| pinned | boolean | 是 | — | 置顶 |
| pinnedAt | string(date-time)\|null | 否 | — | 置顶时间 |
| lastEssenceReadAt | string(date-time)\|null | 否 | — | 最后精华已读时间 |

### 6.45 `ConversationHideResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| hidden | boolean | 是 | — | 隐藏结果 |
| messageVersion | integer(int64) | 是 | — | 当前消息版本 |

### 6.46 `SetMuteRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| muted | boolean | 否 | null 会按 false 处理（家庭群接口） | 是否免打扰 |

### 6.47 `CustomerSearchResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| avatarUrl | string\|null | 否 | — | 头像 |

### 6.48 `ChatUserProfileResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| userId | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| avatarUrl | string\|null | 否 | — | 头像 |
| role | string | 是 | — | 角色 |
| phone | string\|null | 否 | — | 电话 |
| phoneVisible | boolean | 是 | — | 电话是否可见 |
| joinedFamiliesVisible | boolean | 是 | — | 家庭是否可见 |
| joinedFamilies | array<JoinedFamilyProfile> | 是 | — | 已加入家庭 |
| shopProfile | ShopProfileSnapshot\|null | 否 | SHOP 用户时可能有 | 店铺快照 |

### 6.49 `ChatBlockResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| userId | integer(int64) | 是 | — | 目标用户 ID |
| blockedByMe | boolean | 是 | — | 我是否拉黑对方 |
| blockedMe | boolean | 是 | — | 对方是否拉黑我 |

### 6.50 `SendMessageRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| content | string | 是 | 非空，最大 2000 | 消息文本 |
| quotedMessageId | integer(int64) | 否 | — | 引用消息 ID |

### 6.51 `ChatMessageResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 消息 ID |
| conversationId | integer(int64) | 是 | — | 会话 ID |
| senderUserId | integer(int64) | 是 | — | 发送者 ID |
| senderNickname | string | 否 | — | 发送者昵称 |
| senderAvatarUrl | string\|null | 否 | — | 发送者头像 |
| type | string | 是 | TEXT / SYSTEM / DISH_SHARE / ORDER_SHARE / IMAGE_SHARE / SHOP_SHARE / MOMENT_SHARE / MOMENT_COMMENT_SHARE | 消息类型 |
| content | string\|null | 否 | — | 文本/附言 |
| dishId...dishSalesCount | 多字段 | 否 | 菜品分享时有值 | 菜品分享快照 |
| orderId...orderRemark | 多字段 | 否 | 订单分享时有值 | 订单分享快照 |
| quotedMessageId / quotedMessage | 引用结构 | 否 | — | 引用消息 |
| deliveryStatus | string | 是 | DELIVERED / FAILED_BLOCKED | 投递状态 |
| deliveryFailed | boolean | 是 | — | 是否投递失败 |
| failureReason | string\|null | 否 | — | 失败原因 |
| imageUrl / imageExpiresAt / imageExpired | 图片字段 | 否 | 图片消息时有值 | 聊天图片 |
| sharedShop* | 店铺分享字段 | 否 | — | 店铺快照 |
| sharedMoment* | 动态分享字段 | 否 | — | 动态/评论快照 |
| essence / essencePinned / essenceLikeCount | 精华字段 | 否 | — | 群精华状态 |
| recalledAt | string(date-time)\|null | 否 | — | 撤回时间 |
| createdAt | string(date-time) | 是 | — | 创建时间 |

### 6.52 `ConversationMessagesResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversation | ConversationResponse | 是 | — | 会话 |
| messages | array<ChatMessageResponse> | 是 | 按时间升序存储，前端现有实现用 reverse 渲染 | 消息列表 |

### 6.53 `FamilyGroupProfileRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| name | string | 否 | 最大 100 | 群名 |
| coverImageUrl | string | 否 | 最大 255 | 群封面 |
| announcement | string | 否 | 最大 1000 | 公告 |
| joinApprovalRequired | boolean | 否 | — | 入群是否需要审核 |

### 6.54 `FamilyGroupMemberResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| userId | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| avatarUrl | string\|null | 否 | — | 头像 |
| role | string | 是 | OWNER / MEMBER | 群角色 |
| memberStatus | string | 是 | ACTIVE / PENDING / LEFT / REMOVED | 群成员状态 |
| muted | boolean | 是 | — | 免打扰 |

### 6.55 `FamilyGroupResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 会话 ID |
| shopId | integer(int64) | 是 | — | 店铺 ID |
| name | string | 是 | — | 群名 |
| coverImageUrl | string\|null | 否 | — | 封面 |
| announcement | string\|null | 否 | — | 公告 |
| status | string | 是 | ACTIVE / DISSOLVED | 群状态 |
| joinApprovalRequired | boolean | 是 | — | 加入需审核 |
| members | array<FamilyGroupMemberResponse> | 是 | — | 成员 |

### 6.56 `ShareDishRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversationId | integer(int64) | 否 | 服务层会校验非空 | 会话 ID |
| dishId | integer(int64) | 否 | 服务层会校验非空 | 菜品 ID |

### 6.57 `ShareOrderRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversationId | integer(int64) | 否 | 服务层会校验非空 | 会话 ID |
| orderId | integer(int64) | 否 | 服务层会校验非空 | 订单 ID |

### 6.58 `ShareShopRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversationId | integer(int64) | 否 | 服务层会校验非空 | 会话 ID |
| shopId | integer(int64) | 否 | 服务层会校验非空 | 店铺 ID |
| quotedMessageId | integer(int64) | 否 | — | 引用消息 ID |

### 6.59 `MomentShareAliasRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversationId | integer(int64) | 是 | — | 会话 ID |
| momentId | integer(int64) | 是 | — | 动态 ID |
| commentId | integer(int64) | 否 | — | 评论 ID |

### 6.60 `EssenceToggleRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| pinned | boolean | 否 | 仅 true 表示置顶；其他按 false | 是否置顶 |

### 6.61 `LikeToggleRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| liked | boolean | 否 | 仅 true 表示点赞；其他按 false | 是否点赞 |

### 6.62 `EssenceReadRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| throughMessageId | integer(int64) | 否 | — | 读到的消息 ID |

### 6.63 `EssenceItemResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| message | ChatMessageResponse | 是 | — | 消息 |
| pinned | boolean | 是 | — | 置顶 |
| pinnedAt | string(date-time)\|null | 否 | — | 置顶时间 |
| liked | boolean | 是 | — | 我是否点赞 |
| likeCount | integer(int64) | 是 | — | 点赞数 |
| createdAt | string(date-time) | 是 | — | 设为精华时间 |

### 6.64 `EssenceListResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| items | array<EssenceItemResponse> | 是 | — | 精华 |
| unreadCount | integer(int64) | 是 | — | 未读精华数 |

### 6.65 `NotificationResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 通知 ID |
| userId | integer(int64) | 是 | — | 用户 ID |
| type | string | 是 | 业务类型 | 通知类型 |
| title | string | 是 | — | 标题 |
| content | string | 是 | — | 内容 |
| payload | string\|null | 否 | 通常为 JSON 字符串 | 业务载荷 |
| readAt | string(date-time)\|null | 否 | — | 已读时间 |
| createdAt | string(date-time) | 是 | — | 创建时间 |

### 6.66 `NotificationListResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| notifications | array<NotificationResponse> | 是 | — | 通知列表 |
| unreadCount | integer(int64) | 是 | — | 未读数 |

### 6.67 `UpsertMomentRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| title | string | 否 | 最大 120 | 标题 |
| content | string | 否 | 最大 20000 | 正文 |
| visibility | string | 否 | PUBLIC / RELATED / PRIVATE | 可见范围 |
| status | string | 否 | PUBLISHED / DRAFT | 状态 |
| shopId | integer(int64) | 否 | — | 关联店铺 |
| dishId | integer(int64) | 否 | — | 关联菜品 |
| imageUrls | array<string> | 否 | 最多 9；每项最大 500 | 图片 URL |
| topics | array<string> | 否 | 最多 5；每项非空最大 50 | 话题名 |

### 6.68 `AuthorResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 用户 ID |
| nickname | string | 是 | — | 昵称 |
| avatarUrl | string\|null | 否 | — | 头像 |
| role | string | 是 | — | 角色 |
| merchant | boolean | 是 | — | 是否商家 |

### 6.69 `TopicResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 话题 ID |
| name | string | 是 | — | 话题名 |
| usageCount | integer(int64) | 是 | — | 使用次数 |
| userCount | integer(int64) | 是 | — | 使用人数 |
| momentCount | integer(int64) | 是 | — | 动态数 |

### 6.70 `ReferenceResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shopId | integer(int64)\|null | 否 | — | 店铺 ID |
| shopName | string\|null | 否 | — | 店铺名 |
| shopImageUrl | string\|null | 否 | — | 店铺图 |
| dishId | integer(int64)\|null | 否 | — | 菜品 ID |
| dishName | string\|null | 否 | — | 菜品名 |
| dishImageUrl | string\|null | 否 | — | 菜品图 |
| dishPrice | number(decimal)\|null | 否 | — | 价格 |
| dishDescription | string\|null | 否 | — | 描述 |
| dishSalesCount | integer\|null | 否 | — | 销量 |

### 6.71 `ReactionResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| emoji | string | 是 | — | 表情 |
| count | integer(int64) | 是 | — | 数量 |
| reactedByMe | boolean | 是 | — | 我是否回应 |

### 6.72 `MomentResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 动态 ID |
| author | AuthorResponse | 是 | — | 作者 |
| title | string\|null | 否 | — | 标题 |
| content | string\|null | 否 | — | 正文 |
| visibility | string | 是 | PUBLIC / RELATED / PRIVATE | 可见范围 |
| status | string | 是 | PUBLISHED / DRAFT | 状态 |
| imageUrls | array<string> | 是 | — | 图片 |
| topics | array<TopicResponse> | 是 | — | 话题 |
| reference | ReferenceResponse\|null | 否 | — | 关联店铺/菜品 |
| likeCount | integer(int64) | 是 | — | 点赞数 |
| likedByMe | boolean | 是 | — | 我是否点赞 |
| commentCount | integer(int64) | 是 | — | 评论数 |
| reactions | array<ReactionResponse> | 是 | — | 表情回应 |
| editedAt | string(date-time)\|null | 否 | 固定 +08:00 格式 | 编辑时间 |
| publishedAt | string(date-time)\|null | 否 | 固定 +08:00 格式 | 发布时间 |
| createdAt | string(date-time) | 是 | 固定 +08:00 格式 | 创建时间 |
| editable | boolean | 是 | — | 当前用户能否编辑 |
| readByMe | boolean | 是 | — | 是否已读 |
| hotComment | CommentResponse\|null | 否 | 列表可能返回热门评论；详情前端可忽略 | 热门评论 |

### 6.73 `ToggleRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| active | boolean | 否 | null/缺失在部分接口表示 true | 目标状态 |

### 6.74 `ReactionRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| emoji | string | 是 | 非空，最大 32 | 表情 |
| active | boolean | 否 | PUT 时 null 表示 true | 目标状态 |

### 6.75 `MomentCommentRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| content | string | 否 | 最大 2000；可与图片组合 | 内容 |
| parentCommentId | integer(int64) | 否 | — | 根评论 ID |
| replyToCommentId | integer(int64) | 否 | — | 被回复评论 ID |
| imageUrls | array<string> | 否 | 最多 6；每项最大 500 | 评论图片 |

### 6.76 `CommentResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| id | integer(int64) | 是 | — | 评论 ID |
| momentId | integer(int64) | 是 | — | 动态 ID |
| author | AuthorResponse | 是 | — | 作者 |
| content | string\|null | 否 | — | 内容 |
| imageUrls | array<string> | 是 | — | 图片 |
| parentCommentId | integer(int64)\|null | 否 | — | 根评论 ID |
| replyToCommentId | integer(int64)\|null | 否 | — | 被回复评论 ID |
| replyToNickname | string\|null | 否 | — | 被回复昵称 |
| authorBadge | boolean | 是 | — | 是否动态作者 |
| likeCount | integer(int64) | 是 | — | 点赞数 |
| likedByMe | boolean | 是 | — | 我是否点赞 |
| replyCount | integer(int64) | 是 | — | 回复总数 |
| replies | array<CommentResponse> | 是 | — | 内嵌部分回复 |
| createdAt | string(date-time) | 是 | 固定 +08:00 格式 | 创建时间 |

### 6.77 `TopicCreateRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| name | string | 是 | 非空，最大 50 | 话题名 |

### 6.78 `PreferenceRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| hideTargetMoments | boolean | 否 | — | 不看对方动态 |
| hideMyMoments | boolean | 否 | — | 不让对方看我的动态 |

### 6.79 `PreferenceResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| targetUserId | integer(int64) | 是 | — | 目标用户 ID |
| hideTargetMoments | boolean | 是 | — | 不看对方 |
| hideMyMoments | boolean | 是 | — | 不让对方看我 |

### 6.80 `ProfileVisibilityRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| visibility | string | 是 | PUBLIC / CHAT_FRIENDS / FAMILY / PRIVATE | 主页可见性 |

### 6.81 `ProfileSummary`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| userId | integer(int64) | 是 | — | 用户 ID |
| visibility | string | 是 | — | 可见性 |
| canView | boolean | 是 | — | 当前用户是否可查看 |
| hiddenByMe | boolean | 是 | — | 是否被我屏蔽 |
| reason | string\|null | 否 | — | 不可见原因 |
| momentCount | integer(int64) | 是 | — | 动态数 |
| preview | array<MomentResponse> | 是 | — | 预览 |

### 6.82 `MomentShareRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| conversationId | integer(int64) | 是 | — | 会话 ID |
| commentId | integer(int64) | 否 | — | 评论 ID |

### 6.83 `MarkReadRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| momentIds | array<integer(int64)> | 是 | 非空，最多 100 | 动态 ID |

### 6.84 `UnreadResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| relatedUnreadCount | integer(int64) | 是 | — | 相关动态未读数 |

### 6.85 `AvailabilityResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| available | boolean | 是 | — | 是否可访问 |
| status | string | 否 | — | 动态状态 |
| reason | string\|null | 否 | — | 不可用原因 |

### 6.86 `JoinedFamilyProfile`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shopId | integer(int64) | 是 | — | 店铺 ID |
| shopName | string | 是 | — | 店铺名称 |
| conversationId | integer(int64)\|null | 否 | — | 家庭群会话 ID |

### 6.87 `ShopProfileSnapshot`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| shopId | integer(int64) | 是 | — | 店铺 ID |
| shopName | string | 是 | — | 店铺名称 |
| shopLogoUrl | string\|null | 否 | — | 店铺头像或封面 |
| shopDescription | string\|null | 否 | — | 店铺描述 |

### 6.88 `VersionCheckResponse`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| hasUpdate | boolean | 是 | — | 是否有更新 |
| versionName | string\|null | 否 | — | 最新展示版本 |
| downloadUrl | string\|null | 否 | — | 下载地址 |
| isForce | boolean | 是 | — | 是否强制更新 |
| updateContent | string\|null | 否 | — | 更新说明 |

### 6.89 `VersionSaveRequest`
| 字段 | 类型 | 必需 | 约束/枚举 | 说明 |
|---|---|---|---|---|
| appCode | string | 是 | 非空 | 应用标识 |
| versionCode | integer | 是 | — | 构建号 |
| versionName | string | 是 | 最大 50 | 展示版本 |
| downloadUrl | string | 是 | 最大 255 | 下载地址 |
| isForce | integer | 是 | 通常 0 / 1 | 强制更新标志 |
| updateContent | string | 否 | — | 更新说明 |

## 7. 前端开发注意事项与已发现风险

1. **版本发布权限**：当前 SecurityConfig 对 `/api/version/**` 全部公开，意味着 `/publish` 也无需登录。建议后端改为仅 `/check` 公开、`/publish` 仅 ADMIN。
2. **动态列表参数优先级**：同一次请求同时传 keyword/authorId/topicId 时，只会按 `keyword > authorId > topicId` 的第一个分支执行。
3. **分页返回不统一**：普通分页无 `hasMore`；动态分页有。网页端应分别建模。
4. **聊天图片不是普通静态资源**：读取接口需要 JWT；`<img src>` 无法附带 Authorization 时，可用 fetch(blob) + objectURL，或后端改用短时签名 URL/cookie 认证。
5. **状态更新后以返回实体为准**：动态点赞、表情、评论点赞接口会返回更新后的对象，前端应直接替换本地对象，避免只做计数加减导致并发不一致。
6. **WebSocket 采用重拉策略**：收到 CHAT/MOMENT/ORDER 等事件后按 ID 或模块刷新，避免依赖未文档化的 data 扩展字段。
7. **仓库配置安全**：开发配置文件不应提交真实数据库密码或 JWT secret；部署时使用环境变量/密钥管理。本文档不包含任何凭据。

## 8. 源代码对应关系

- 接口路径：`backend/src/main/java/com/order/menu/module/**/controller/*Controller.java`
- 请求/响应字段：`backend/src/main/java/com/order/menu/module/**/dto/*Dtos.java`
- 权限：`backend/src/main/java/com/order/menu/config/SecurityConfig.java`
- WebSocket：`backend/src/main/java/com/order/menu/config/WebSocketConfig.java` 与 `backend/src/main/java/com/order/menu/websocket/*`
- 统一异常：`backend/src/main/java/com/order/menu/common/exception/GlobalExceptionHandler.java`
- 状态常量：`backend/src/main/java/com/order/menu/common/constant/StatusConstants.java`
