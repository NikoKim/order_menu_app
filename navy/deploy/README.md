# Navy 宝塔部署

适用：常规 Linux + systemd 服务器，宝塔安装 Nginx、MySQL 8.0 和 Java 17。前端由 Nginx 提供，后端由 systemd 守护，不需要开发服务器、Redis或原项目。以下使用独立域名 `navy.example.com`，部署在域名根路径，不是 `/navy/` 子路径。

模板未在你的宝塔服务器上执行；域名、Java 安装位置、数据库及 SMTP 凭据必须根据服务器填写。用户头像保存在 `/www/wwwroot/navy/assets`，通过后端接口读取。

## 1. 本机打包

在 Navy 前端目录执行：

```powershell
Remove-Item Env:VITE_BACKEND_ORIGIN -ErrorAction SilentlyContinue
npm ci
npm run build
```

如有本机 `.env.production` 等文件，确认其中没有旧的 `VITE_BACKEND_ORIGIN`。生产使用同域 `/api`，不要把 `localhost:8082` 打进前端。

在 Navy 后端目录执行：

```powershell
mvn clean package -DskipTests
```

上传前端 `dist` **里面**的内容；后端上传 `target/navy-backend-1.0.0.jar`，在服务器上改名 `navy-backend.jar`。不要上传 `.jar.original`、`node_modules` 或 `ai`；不需要上传源码。

## 2. 服务器目录和数据库

在宝塔创建静态网站，绑定域名，网站根目录设置为 `/www/wwwroot/navy/frontend`。域名解析到服务器，并在宝塔申请 SSL 证书、开启 HTTPS。不要将网站根目录设置为整个 `/www/wwwroot/navy`，否则可能暴露后端配置。

宝塔数据库中创建 **MySQL 8.0** 数据库 `navy` 和仅有该库权限的用户 `navy_admin`，字符集 `utf8mb4`。不要使用 root 作为应用数据库账号，也不要用 MySQL 5.7 或直接换成 MariaDB。生产端口通常为 `3306`，并非本机开发的 `3307`，请确认实际设置。

首次部署到空库：后端启动时 Flyway 自动创建业务表，不需要逐个导入建表 SQL。

本版本需要升级至 V14。先备份数据库与头像，停止旧后端，再部署新后端并确认 Flyway 成功升级到 V14，最后更新前端。V10 清理已废弃功能的两张业务表，V11 调整新账号初始金币为 100、将“小鲨鱼”金币一次性设为 100，并增加任务手动领取状态；此前已经自动发奖的任务不会重复领取。V12 增加管理员权限与消息通知，初始化指定管理员并将现有未过期登录延长到 90 天。V13 新增商城、背包、聊天气泡装备和任务重置奖励归档，购买与使用具有幂等回执，未使用道具按购买时间过期。V14 增加商城物品邮件赠送，附件领取期限为发送后 24 小时，物品有效期从领取成功时开始；赠送物品不占商城限购。此前金币赠送邮件的领取期限也按原发送时间计算。双方按各自投注结算，游客和 Lv1 支持不下注。此前已应用的迁移文件和历史记录必须保留，不要手工修改校验和。不要只上传前端或手工清空账户、任务、押注及奖励流水表。历史对局不追溯发放等级分和金币。同一数据库只能运行一个 Navy 后端实例。

如果要保留本机 Navy 的用户、战绩等：先完整备份并导入 **navy 数据库，包括 flyway_schema_history**，再启动同版本后端。不要导入 `order_menu`，也不要删除迁移历史表。数据库备份本身不包含头像图片，还需迁移实际使用的头像目录。

服务器最终结构：

```text
/www/wwwroot/navy/
  frontend/
    index.html
    assets/
    naval/
  backend/
    navy-backend.jar
    config/
      application-prod.yml
      navy.env
      nginx-navy.conf
  assets/
    <generated-uuid>.png
  logs/
```

## 3. 单独配置

本目录文件与服务器位置对应：

| 模板 | 服务器位置 |
| --- | --- |
| application-prod.yml | /www/wwwroot/navy/backend/config/application-prod.yml |
| navy.env.example | /www/wwwroot/navy/backend/config/navy.env |
| nginx-navy.conf | /www/wwwroot/navy/backend/config/nginx-navy.conf |
| navy.service | /etc/systemd/system/navy.service |

在 `navy.env` 填写实际数据库密码、163 邮箱 SMTP 授权码和正式域名。SMTP 授权码不是邮箱登录密码；当前功能需要出站 TCP 465。域名必须包含 `https://`，不能包含结尾 `/` 或路径。

`NAVY_MAIL_SENDER_NAME` 默认为“海战棋”，只设置邮件的发件人显示名称，实际发件地址仍使用 `NAVY_MAIL_USER`，无需更换原邮箱或授权码。注册和绑定邮箱验证码使用同码的 HTML/纯文本双版本；验证码可直接选中复制，5 分钟内有效。本次邮件样式更新不增加数据库迁移。

头像永久存储目录为 `/www/wwwroot/navy/assets`，可用 `NAVY_AVATAR_DIRECTORY` 覆盖。该目录和前端构建目录 `/www/wwwroot/navy/frontend/assets` 是两个不同目录，不要合并、不要将头像目录作为前端构建输出。数据库仍保存 `/api/account/avatars/<文件名>.png`，由后端读取图片，Nginx 无需给头像目录配置 alias。不要把真实凭据提交 Git 或放到前端目录。

若之前使用 `/www/wwwroot/navy/data/avatars`，先停止后端，将旧目录中的 PNG 原文件复制到 `/www/wwwroot/navy/assets`，保留文件名并验证历史头像可用后再归档旧目录。数据库里的头像 URL 不需要修改。自定义路径时需同步修改 systemd 的 `ReadWritePaths`，并提前创建该目录。

上传配置后，以 root 执行以下 Linux 命令。只创建并授权本项目目录，不更改其他网站：

```bash
id navy >/dev/null 2>&1 || useradd --system --home-dir /www/wwwroot/navy --shell /usr/sbin/nologin navy
mkdir -p /www/wwwroot/navy/backend/config /www/wwwroot/navy/frontend /www/wwwroot/navy/assets /www/wwwroot/navy/logs
chown root:root /www/wwwroot/navy
chmod 755 /www/wwwroot/navy
chown root:navy /www/wwwroot/navy/backend /www/wwwroot/navy/backend/config
chmod 750 /www/wwwroot/navy/backend /www/wwwroot/navy/backend/config
chown root:navy /www/wwwroot/navy/backend/navy-backend.jar /www/wwwroot/navy/backend/config/application-prod.yml
chmod 640 /www/wwwroot/navy/backend/navy-backend.jar /www/wwwroot/navy/backend/config/application-prod.yml
chown root:root /www/wwwroot/navy/backend/config/navy.env
chmod 600 /www/wwwroot/navy/backend/config/navy.env
chown navy:navy /www/wwwroot/navy/assets /www/wwwroot/navy/logs
chmod 750 /www/wwwroot/navy/assets /www/wwwroot/navy/logs
```

前端目录及文件需要宝塔 Nginx 用户可读，通常目录 `755`、文件 `644`；通过宝塔文件管理器设置。迁移过来的头像及其目录也需由 `navy` 用户读取，不要只调整顶层目录。不要使用 `chmod 777`。

## 4. 后端启动

确认 Java 是 17：

```bash
java -version
readlink -f "$(command -v java)"
```

将 `navy.service` 的 `ExecStart` 中 `/usr/bin/java` 改为上述 Java 17 的真实绝对路径。宝塔安装的 Java 可能不在 `/usr/bin/java`；不要使用指向 Java 8 的路径。模板使用独立低权限 `navy` 用户，确保该用户可以访问 Java 所在目录。

确认 MySQL 已运行且已在宝塔开启自启动，然后执行：

```bash
systemctl daemon-reload
systemctl enable --now navy
systemctl status navy --no-pager
curl http://127.0.0.1:8082/api/health
```

健康检查应返回 `"status":"UP"`。内存参数是起点，`-Xmx512m` 仅限制 Java 堆，不代表整个 Java 进程只占 512MB；小内存机器还需给 MySQL、Nginx 和系统预留空间。

仅对外放行网站所需 `80/443`。**不需要开放 8082 或 MySQL 的公网端口**，后端仅监听本机回环地址。宝塔面板自己的管理端口按原安全设置处理。

不要同时再用宝塔 Java 项目管理器、Supervisor 或 `nohup` 启动相同 JAR，否则会抢占端口。这套方案用 systemd 管理后端。

## 5. Nginx 配置

在该网站的 Nginx `server { ... }` 内引入：

```nginx
include /www/wwwroot/navy/backend/config/nginx-navy.conf;
```

这是 **server 内的配置片段**，不是完整 Nginx 主配置。保留宝塔原有 `listen`、`server_name`、证书、续签验证和安全拦截规则，站点 `root` 为 `/www/wwwroot/navy/frontend`。不要再配置 PHP，也不要额外把整个网站反代到 8082。

已有 `location /`、`/api/`、`/api/naval/live`、`/assets/`、`/naval/` 等相同规则时，先移除或合并冲突项；若站点已配置 `client_max_body_size` 或 gzip，在同一个 server 内也只保留一份。宝塔“伪静态”和已有反向代理可能引入这些规则，不要重复粘贴。

让该片段由 root 管理，并允许 Nginx 主进程读取。宝塔常规 Nginx 主进程以 root 运行，可以读取上述受限目录；如你的 Nginx 主进程是非 root，请改用其可读、但不在网站根目录内的配置位置，并同步修改 include 路径。

片段已处理 SPA 路由回退、头像上传大小、接口与长连接代理、缓存和压缩。其中 `proxy_pass http://127.0.0.1:8082` **不要加尾部 `/`**，否则接口路径可能被改写。`/api/naval/live` 的 Upgrade 配置必须保留，否则大厅和对局实时同步无法正常工作。

在宝塔中测试配置并重载 Nginx，也可用实际安装路径执行：

```bash
/www/server/nginx/sbin/nginx -t
/www/server/nginx/sbin/nginx -s reload
```

启用 HTTPS 后，再确认 `NAVY_FRONTEND_ORIGIN` 是实际 HTTPS 域名；改动环境文件后重启后端。

## 6. 验收与排查

- 打开 `https://你的域名/`，刷新 `/login` 不应 404。
- 打开 `https://你的域名/api/health` 应返回 UP。
- 浏览器开发者工具 Network 的 WS 中，`/api/naval/live` 升级应为 101，并持续收发消息。
- 验证登录、注册、邮箱验证码、个人主页头像上传、双人匹配和断线重连。头像上传后确认 `/www/wwwroot/navy/assets` 下有生成的 PNG，且 `/api/account/avatars/<文件名>.png` 可读取；刷新与后端重启后仍可见。
- 更换头像时先预览并确认；确认成功后，原头像文件应从头像目录删除。取消、上传失败或数据库回滚应保留原头像。通知使用同一社区面板，退出登录位于个人信息页。
- `502`：确认后端正在运行且监听 127.0.0.1:8082。
- 跨域或 WebSocket 被拒绝：确认域名、HTTPS、Origin 和外置配置一致。
- 邮件发送失败：检查授权码和服务器出站 465；单纯健康检查成功不代表 SMTP 能投递。
- 上传失败：检查 3MB 的 Nginx 请求限制、2MB 的后端文件限制，以及头像目录归属。

查看日志：

```bash
journalctl -u navy -n 100 --no-pager
tail -n 100 /www/wwwroot/navy/logs/navy.log
```

## 7. 后续更新与备份

更新前备份 `navy` 数据库和 `/www/wwwroot/navy/assets`，妥善备份配置中的凭据。前端更新仅替换 `/www/wwwroot/navy/frontend` 内的构建产物；后端先 `systemctl stop navy`，再替换 JAR 并保持文件权限，最后 `systemctl start navy`。不要覆盖 `backend/config`、根目录 `assets` 或 `logs`。有数据库迁移时，回滚旧 JAR 不一定能回滚数据库，先在备份副本验证。

修改 YAML 或 `navy.env` 后执行 `systemctl restart navy`；修改 service 文件还需先执行 `systemctl daemon-reload`。修改 Nginx 配置先测试再重载。不要把 `ai` 上传或用作生产依赖。

当前实时同步、在线人数、验证码和部分缓存为进程内状态，先部署 **单后端实例**，不要直接启动多个实例做负载均衡。

## 配置依据

- [Spring Boot 外置配置](https://docs.spring.io/spring-boot/reference/features/external-config.html)：`spring.config.additional-location` 在保留 JAR 默认配置的基础上加载外置配置。
- [Nginx WebSocket 代理](https://nginx.org/en/docs/http/websocket.html)：显式转发 Upgrade/Connection，并设置适当连接超时。
