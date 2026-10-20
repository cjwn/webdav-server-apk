# vivo 真机安装与 PROPFIND 性能复测

## 下载与安装

1. GitHub 仓库 → Actions → Debug APK → 选择本分支成功的运行。
2. 页面底部 Artifacts → 下载 `webdav-fast-propfind-debug-<commit>`，解压 ZIP。
3. 可用 `sha256sum -c SHA256SUMS.txt` 校验 APK；`COMMIT.txt` 记录实际构建提交。
4. 将 `webdav-fast-propfind-debug.apk` 传到 Android 12+ 的 vivo 手机上，用文件管理器打开。按系统提示为该文件管理器允许“安装未知应用”。
5. 包名为 `com.localdev.webdavserver.debug`，可以与正式版共存。如果已有同包名、不同签名的 Debug 版本，不能直接覆盖；先备份设置，再卸载旧 Debug 版或换回同一签名构建。卸载会清除该应用设置。
6. 首次启动创建管理员，授予文件访问、通知权限，设置测试目录并启动服务。vivo 的电池/后台设置中允许该应用后台运行，复测时保持前台与亮屏。

## 采集日志

在自己的电脑安装 Android platform-tools。手机启用开发者选项和 USB 调试，连接 USB 并允许调试授权：

```bash
adb devices
adb logcat -v time 'WebDavPerf:I' '*:S' > webdav-perf.log
```

保留该终端运行，复测结束按 Ctrl+C。日志字段：

```text
PROPFIND depth=1 items=... listMs=... buildMs=... xmlChars=...
```

- `items`：列出的直接子项数，不包含目录自身。
- `listMs`：列出并排序直接子项元数据的耗时。
- `buildMs`：包含列举在内的 XML 构造耗时；不是纯 XML 拼接时间，也不包含全部请求、鉴权、路径解析和网络传输时间。
- `xmlChars`：XML 字符数，不等同于传输字节数。

## 重复请求

电脑与手机连接同一可信局域网，使用应用首页显示的 IP 和端口。以下在电脑 Bash 执行，将 `server` 替换为手机地址，`path` 替换为相对于 WebDAV 根目录、已 URL 编码的测试目录路径（保留末尾 `/`）。`curl` 会提示输入密码，不要将真实密码写进命令或日志：

```bash
server='http://192.168.1.100:8080'
path='/test-folder/'
read -r -p 'WebDAV 用户名: ' dav_user
curl --user "$dav_user" --request PROPFIND --header 'Depth: 1' \
  --output propfind.xml \
  --write-out 'HTTP=%{http_code} total=%{time_total}s bytes=%{size_download}\n' \
  "$server$path"
```

每次应返回 HTTP 207。检查 XML 条目数量、名称、目录/文件类型与实际目录一致；保持隐藏文件开关一致。先预热一次，再重复 5–10 次，记录首次耗时以及后续请求的中位数。可同时检查 Depth: 0 只返回目录自身、不列出子项。

选择含大量直接子目录的目录，子目录内各有较多文件，最容易验证“避免扫描每个子目录”的优化。分别测试普通文件路径根目录和现有 SAF（content://）根目录（若有），不要为测试强行转换已有根目录。

与旧版对比时保持同一手机、目录内容、根目录类型、账号、隐藏文件设置和网络条件。旧版若没有 WebDavPerf 日志，比较客户端总耗时及返回结果，不要把客户端总耗时直接等同于新版本的 buildMs。此 APK 的编译成功不代表已经完成真机性能验证。
