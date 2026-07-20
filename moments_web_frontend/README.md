# 香狗聊天与动态 Web

独立的 Vue 3 前端，只包含客户侧聊天、动态及其附属功能。本地开发通过 Vite 将接口与图片请求代理到 `http://localhost:8080`。

## 本地运行

```bash
npm install
npm run dev
```

默认地址：`http://localhost:5174`

## 生产构建

```bash
npm run build
```

生产构建输出到 `moment_dist`，部署地址为 `https://moment.zsj-unknown.top`。生产环境的 API、上传和聊天媒体请求直接访问 `https://order-menu-backend.zsj-unknown.top`。

后端部署时使用 `SPRING_PROFILES_ACTIVE=prod`，并确保 Web 服务器将 Vue History 路由回退到 `index.html`。

Nginx 需要在 `moment.zsj-unknown.top` 的 HTTPS `server` 块中加入 [nginx-spa.conf](./nginx-spa.conf) 的 `location` 配置。否则直接刷新 `/customer/moments` 等 History 路由时，Nginx 会把路径当作实体文件并返回 404。

构建产物同时包含自定义 `404.html`。Nginx 配置会在真正缺失的资源或页面上展示该页面，并提供返回 `https://moment.zsj-unknown.top/` 的重新加载链接。

公开动态入口为 `/customer/moments`，无需登录即可浏览动态、搜索和查看详情。聊天、发布及互动操作仍需登录。
