<<<<<<< HEAD
# 🔐 ServerAuthMod

**Minecraft Forge 1.20.1 服务器身份验证模组**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Terms of Service](https://img.shields.io/badge/Terms-Service-orange)](TERMS_OF_SERVICE.md)

---

## ✨ 功能

| 功能 | 说明 |
|------|------|
| ✅ **唯一标识ID** | 每位玩家自动分配一个永久数字 ID (#1, #2, #3...) |
| ✅ **白名单模式** | 仅允许指定 ID 的玩家进入服务器 |
| ✅ **黑名单模式** | 拒绝指定 ID 的玩家进入（优先级高于白名单） |
| ✅ **设备指纹识别** | 基于硬件 + 系统特征生成唯一设备标识 |
| ✅ **加密存储** | AES-256 CBC 加密客户端配置文件 |
| ✅ **离线和正版区分** | 自动识别 Premium 和 Offline 账号 |
| ✅ **设备变更检测** | 设备发生变化时自动标记并通知管理员 |
| ✅ **管理员命令** | 完整的白名单/黑名单/设备查询命令 |

---

## 📥 安装

1. 编译或下载 `ServerAuthMod-1.0.0.jar`
2. 放入服务端和客户端的 `mods/` 文件夹
3. 启动服务器（首次会自动生成配置文件）

> **注意**：客户端和服务端必须同时安装本模组！

---

## 🎮 命令（需要 OP 权限 ≥ 2）

| 命令 | 说明 |
|------|------|
| `/serverauth status` | 查看模组状态 |
| `/serverauth id <玩家>` | 查看玩家的 ID |
| `/serverauth device <玩家>` | 查看玩家的设备指纹信息 |
| `/serverauth whitelist add <ID>` | 将 ID 加入白名单 |
| `/serverauth whitelist remove <ID>` | 从白名单移除 ID |
| `/serverauth whitelist list` | 查看白名单列表 |
| `/serverauth blacklist add <ID>` | 将 ID 加入黑名单 |
| `/serverauth blacklist remove <ID>` | 从黑名单移除 ID |
| `/serverauth blacklist list` | 查看黑名单列表 |
| `/serverauth reload` | 重载配置文件 |

---

## 📂 配置文件

所有文件位于 `config/serverauth/`：

| 文件 | 说明 |
|------|------|
| `whitelist.json` | 白名单 ID 列表 |
| `blacklist.json` | 黑名单 ID 列表 |
| `player_id_map.json` | UUID → ID 映射 |
| `device_fingerprints.json` | 服务端设备指纹记录 |
| `device.dat` | **客户端**本地加密指纹文件 |

---

## 🛡️ 安全性

### 加密方案
- **算法**：AES-256 CBC
- **密钥派生**：PBKDF2WithHmacSHA256 (65536 轮)
- **初始化向量**：随机生成，每次不同
- **存储格式**：Base64(IV + Ciphertext)

### 重要提示
> **部署前请修改 `CryptoUtil.java` 中的 `SECRET` 和 `SALT` 值！**
> 使用默认值会降低加密安全性。

---

## 🔧 编译方法

### 前置要求
- JDK 17+
- 稳定的网络连接（用于下载依赖）

### 编译步骤

```bash
# Windows
gradlew.bat build

# Linux/macOS
./gradlew build
```

编译完成后，jar 文件在 `build/libs/ServerAuthMod-1.0.0.jar`

---

## 📜 开源协议

本项目基于 **GNU General Public License v3.0** 开源。

```
Copyright (C) 2024

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```

详见 [LICENSE](LICENSE) 文件。  
使用本模组即表示您同意 **[用户条款 (Terms of Service)](TERMS_OF_SERVICE.md)**，包括设备指纹数据收集条款。

---

## 📊 架构

```
┌───────────────────────────────────────────┐
│              客户端 (Client)               │
│  ├─ ClientDeviceFingerprint               │
│  │   采集硬件特征 → SHA-256 → 加密存储     │
│  ├─ ClientAuthHandler                     │
│  │   加入服务器时上报设备指纹              │
│  └─ 本地设备.dat (AES-256加密)            │
└───────────────────┬───────────────────────┘
                    │ 网络通信
                    ▼
┌───────────────────────────────────────────┐
│              服务端 (Server)               │
│  ├─ ServerAuthHandler                     │
│  │   验证身份 + 处理设备指纹              │
│  ├─ AuthConfig                            │
│  │   管理白名单/黑名单/设备记录           │
│  └─ Admin Commands                        │
│      管理ID/白名单/黑名单/设备信息        │
└───────────────────────────────────────────┘
```

---

## ⚙️ 工作流程

```
玩家加入 → 分配/获取唯一数字ID
         → 检查白名单/黑名单
         → 通过? → 进入游戏
              ↓
          拒绝? → 断开连接 + 显示原因
              
客户端:  生成设备指纹 → 上报服务器
服务端:  记录设备指纹 → 检测变化 → 通知管理员
         ↓
        区分正版/离线账号
```
=======
# fang-ring-ServerAuthMod-GitHub
>>>>>>> ca18cfa4bae1d9bfdf2890bc10ae81a7f489a7f2
