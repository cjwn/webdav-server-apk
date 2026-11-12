# 相册付费解锁与应用商店分发

## 代码来源和商业使用

GitHub 将本仓库标记为 [anujkumarprasad/webdav-server-apk](https://github.com/anujkumarprasad/webdav-server-apk) 的 fork。上游 `main` 的 [LICENSE](https://github.com/anujkumarprasad/webdav-server-apk/blob/main/LICENSE) 与本仓库的 LICENSE 一致，均声明 Apache License 2.0。该许可允许商业分发修改版，因此可以收费解锁功能，也可以通过广告变现。这个判断适用于 Apache 2.0 覆盖的材料。正式打包前仍需核对依赖、图标、字体及新增 SDK 的条款。

按 Apache 2.0 第 4 节，分发时应向接收方提供完整许可文本，在修改过的文件中显著注明修改，在分发的源代码中保留相关版权、专利、商标和署名声明，并按要求保留适用的上游 NOTICE。核对时，上游根目录有 LICENSE，未见独立 NOTICE。仓库目前的 LICENSE 是简短声明和链接，正式分发包应附完整文本。商标和品牌按第 6 节另行处理。 [Apache License 2.0 正文](https://www.apache.org/licenses/LICENSE-2.0)

Apache 2.0 允许按其条款分发二进制，且没有公开修改版源码的义务。已经按 Apache 2.0 授权发布的代码仍可由他人依许可使用和再分发。付费购买的是发行版提供的功能权益。

## 产品和权限

- 免费功能包括局域网 WebDAV 共享和访达连接。
- 建议用一次性购买解锁访达 `Photos by Date` 和网页相册。付款前展示价格、解锁内容、相册目录读取权限，以及启用批量删除需要写入权限并会删除原件。
- 确认购买后由用户主动点「设置相册」，再打开 Android 系统目录选择器，优先定位到 `DCIM/Camera`，也允许用户选其他目录。取消授权或暂未选择目录时保留购买权益。浏览时使用读取授权，启用删除时再请求写入授权。基础 WebDAV 的共享授权继续按其自身用途处理。 [Android 目录授权说明](https://developer.android.com/training/data-storage/shared/documents-files)
- 相册目前依赖直接文件路径。正式实现需要让索引、缩略图、原件读取和删除支持获授权的目录 URI。手机服务端应在目录列表、缩略图、原件和删除入口校验相册权益、目录授权和账号访问权限。相册目录独立于 WebDAV 共享根目录时，仍要明确哪些账号能访问。
- 各渠道共用同一套相册权益和授权逻辑，支付接入按渠道实现。提供恢复购买，并在提审前验证购买待确认、重复回调、重装恢复、退款及授权撤回。购买是否跨商店通用需先确定订单核验与账号方案。

## 渠道接入

| 渠道 | 当前方案 |
| --- | --- |
| Google Play | 使用 Google Play Billing 一次性商品。商店详情和购买页明确付费功能，按 Play Console 要求审核存储与媒体权限。 [付款政策](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en) · [权限政策](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) |
| 荣耀应用市场 | 荣耀 IAP 支持非消耗型商品。确认开发者主体及商户资格后开通支付并进行沙盒验证。提审时按实际合作方式选择联运或非联运。 [荣耀 IAP](https://developer.honor.com/cn/docs/11017/guides/dev-overview) · [发布 APK](https://developer.honor.com/cn/doc/guides/100884) |
| vivo 应用商店 | 按普通工具应用准备分发。向平台确认非游戏工具的一次性内购、个人主体开通资格及可用支付方式。 [vivo 开发者平台](https://developer.vivo.com/?lang=zh_cn) |
| OPPO 软件商店 | 按普通工具应用准备分发。向平台确认支付服务是否适用于本应用、开通主体要求及允许的支付方式。 [OPPO 软件商店](https://open.oppomobile.com/product/page?page_name=appstore) |

中国大陆渠道按平台及应用类别准备 APP 备案或核准材料、隐私政策和版权资质。提审时说明手机启动服务后如何在同一局域网用浏览器和访达验证功能。 [工信部 APP 备案通知](https://wap.miit.gov.cn/jgsj/xgj/wjfb/art/2023/art_dd783a581c9644a4aee10afa582811db.html) · [荣耀版权资质说明](https://developer.honor.com/cn/doc/guides/101411)

## 广告选项

如选择广告变现，优先在手机 App 内设置清楚标识的广告位。局域网照片墙涉及私人照片和访问凭据，建议保持为由手机提供的页面，避免接入第三方广告脚本。选定广告 SDK 后核对其数据收集、隐私披露、商店声明和广告展示规则。 [Google Play 广告政策](https://support.google.com/googleplay/android-developer/answer/9857753) · [Google Play 应用内容申报](https://support.google.com/googleplay/android-developer/answer/9859455)

当前进度为方案阶段。项目尚未接入支付、广告或商店发行配置。
