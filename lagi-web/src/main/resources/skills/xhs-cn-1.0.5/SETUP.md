# 服务器安装（仅首次需要）

本 Skill 需要在本地运行 [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp) 服务器。

## 第 0 步：安装 Python 依赖

```bash
pip install requests
```

## 第 1 步：下载二进制文件

从 [GitHub Releases](https://github.com/xpzouying/xiaohongshu-mcp/releases) 下载：

| 平台 | MCP 服务器 | 登录工具 |
| ---- | ---------- | -------- |
| macOS (Apple Silicon) | `xiaohongshu-mcp-darwin-arm64` | `xiaohongshu-login-darwin-arm64` |
| macOS (Intel) | `xiaohongshu-mcp-darwin-amd64` | `xiaohongshu-login-darwin-amd64` |
| Windows | `xiaohongshu-mcp-windows-amd64.exe` | `xiaohongshu-login-windows-amd64.exe` |
| Linux | `xiaohongshu-mcp-linux-amd64` | `xiaohongshu-login-linux-amd64` |

授予执行权限（macOS/Linux）：

```bash
chmod +x xiaohongshu-mcp-* xiaohongshu-login-*
```

## 第 2 步：登录

运行登录工具，使用小红书 App 扫描二维码登录。

```bash
./xiaohongshu-login-darwin-arm64
```

> **重要提示**：请勿在其他任何网页浏览器中登录同一账号，否则会导致会话失效。

## 第 3 步：启动 MCP 服务器

```bash
# 无头模式（推荐）
./xiaohongshu-mcp-darwin-arm64

# 显示浏览器窗口（用于调试）
./xiaohongshu-mcp-darwin-arm64 -headless=false
```

服务器运行在 `http://localhost:18060`。使用以下命令验证：

```bash
python scripts/xhs_client.py status
```
