# Skypie Music (饼音)

一个基于 Jetpack Compose 的 Android 本地+在线音乐播放器，个人学习项目。

## 功能

- 🎵 本地音乐扫描与播放
- ☁️ 在线音乐搜索与播放
- 📥 歌曲下载（自动内嵌封面、歌词、ID3标签）
- 📝 歌词显示（LRC 解析、桌面悬浮歌词）
- 🎨 3D 封面效果（陀螺仪倾斜 + 光影）
- 🎭 动态极光背景（从封面提取颜色）
- 📱 迷你播放条 + 全屏播放器
- 🔀 播放模式（循环/单曲循环/随机）
- 📂 文件夹管理
- 🎵 歌单功能

## 技术栈

- Kotlin + Jetpack Compose
- Media3 (ExoPlayer) 播放引擎
- Hilt 依赖注入
- Room 数据库
- Coil 图片加载
- Haze 毛玻璃效果
- JAudioTagger 音频标签

## 自建 API 说明

本应用**不提供任何公共 API 地址**，在线播放功能需要自行搭建 API 服务。

### 使用方式

1. 搭建兼容的音乐 API 服务
2. 打开应用 → 设置 → API 接口地址
3. 填入你的 API 根地址，例如：`https://your-api.com`

### 接口规范

API 需要提供以下两个接口：

#### 1. 酷我播放链接解析

```
GET {apiBase}/music/kw.php?id={songId}&level={level}

参数：
  id    - 酷我歌曲 ID（rid）
  level - 音质等级：standard / high / lossless

返回：
{
  "code": 200,
  "data": {
    "url": "https://stream.example.com/song.mp3"
  }
}
```

#### 2. 酷狗播放链接解析

```
GET {apiBase}/kgqq/kg.php?id={hash}&level={level}

参数：
  id    - 酷狗歌曲 hash
  level - 音质等级：standard / high / lossless

返回：
{
  "code": 200,
  "data": {
    "url": "https://stream.example.com/song.mp3"
  }
}
```

## 免责声明

本项目仅供个人学习研究使用。搜索/榜单接口使用公开的 Web 端接口，播放链接解析依赖用户自行搭建的 API 服务。请勿用于商业用途或侵犯版权。如有侵权，请联系删除。
