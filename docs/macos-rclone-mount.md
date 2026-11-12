# Mac 上用 rclone 挂载手机

网页首页的 **Optional Mac mount with rclone** 提供可复制的命令。这是可选的只读挂载。rclone 在 Mac 上连接手机的 WebDAV，再通过 macOS 自带的 NFS 把共享目录挂载到访达。rclone 可缓存目录列表，首次打开、滚动和 Quick Look 的速度取决于文件、设备和网络。普通用户也可以直接用访达 `⌘K` 连接手机。 [rclone 的 macOS NFS 挂载说明](https://rclone.org/commands/rclone_mount/)

1. 在手机 App 启动服务，让手机和 Mac 连接同一个局域网。记下 App 显示的服务器地址。
2. 在 Mac 上安装支持 `nfsmount` 的 rclone。如果已使用 Homebrew，可以运行 `brew install rclone`。 [rclone 安装说明](https://rclone.org/install/#macos)
3. 在 Mac 终端运行 `rclone config`。创建名为 `phone` 的 remote，存储类型选 `webdav`，URL 填手机显示的地址，供应商选 `other`。在终端提示下输入当前的 WebDAV 用户名和密码，保存后退出配置。若已存在同名的手机配置，修改对应配置即可。 [rclone WebDAV 配置示例](https://rclone.org/webdav/)
4. 运行以下命令并保持终端窗口打开。

   ```sh
   mkdir -p ~/Phone-WebDAV && rclone nfsmount phone: ~/Phone-WebDAV --read-only --dir-cache-time 30s --poll-interval 0
   ```

5. 在访达按 `⇧⌘G`，输入 `~/Phone-WebDAV`。如果手机开启了 `Photos by Date`，可以按日期进入并用空格预览。
6. 使用完后，关闭正在浏览该挂载的窗口或文件。在另一个终端窗口执行 `/sbin/umount ~/Phone-WebDAV`。如果 rclone 进程仍在运行，回到原终端按 `Ctrl+C`。

如果挂载后目录无法读取，先在终端运行 `rclone lsd phone:`。遇到 HTTP 401 时，用 `rclone config` 编辑 `phone`，更新为当前密码后重新挂载。手机改了地址时也需要更新 URL。
