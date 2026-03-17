# LinkMind 安装指南

本文介绍如何在 Windows、macOS 与 Linux 上安装 JDK 并通过 JAR 运行 LinkMind。

---

## 一、安装 JDK

**JDK** 是运行 / 开发 LinkMind 的必备环境（含 JRE 运行时与编译器 javac）。可选：

- **Oracle JDK**：商业授权，非商用免费
- **Eclipse Temurin (Adoptium) OpenJDK**：开源免费，全场景可用（推荐）

---

### 1.1 Windows 系统

#### 步骤 1：下载 JDK

1. 打开浏览器访问：[Oracle Java 下载](https://www.oracle.com/java/technologies/downloads/#java8) 或 [Eclipse Temurin 下载](https://adoptium.net/temurin/releases/)
2. 选择 **Windows**、**x64** 架构
3. 选择 **JDK 8**
4. 安装包格式：
   - Oracle JDK：`.exe` 安装包（如 `jdk-8u391-windows-x64.exe`）
   - Temurin JDK：`.msi` 安装包（可自动配置环境变量，推荐）

#### 步骤 2：安装

1. 双击安装包，在「用户账户控制」中点击「是」
2. Oracle JDK 需先勾选「接受许可协议」，再点击「下一步」
3. 建议安装路径无中文、无空格、无特殊符号，例如：`C:\Development_tools\jdk1.8.0_391`
4. 若向导提示「安装 JRE」，保持默认勾选
5. 完成安装后点击「关闭」

#### 步骤 3：环境变量（可选）

若安装时勾选了「Add to PATH」（Temurin 的 .msi 通常会自动勾选），可跳过本步，直接做「步骤 4」验证。

1. 右键「此电脑」→「属性」→「高级系统设置」→「环境变量」
2. 新建 **系统变量**：
   - 变量名：`JAVA_HOME`
   - 变量值：JDK 安装根路径（如 `C:\Development_tools\jdk1.8.0_391`）
3. 编辑 **系统变量** `Path`，新增：
   - `%JAVA_HOME%\bin`
   - `%JAVA_HOME%\jre\bin`
4. 确定保存后，关闭已打开的所有命令行窗口

#### 步骤 4：验证

1. 按 `Win + R`，输入 `cmd`，回车
2. 在命令行中执行：
   ```powershell
   java -version
   ```
3. 若看到类似以下输出即表示安装成功：
   ```
   java version "1.8.0_221"
   Java(TM) SE Runtime Environment (build 1.8.0_221-b11)
   Java HotSpot(TM) 64-Bit Server VM (build 25.221-b11, mixed mode)
   ```
4. 若提示「'java' 不是内部或外部命令」，请重启电脑后再试

---

### 1.2 macOS 系统

#### 方法一：Temurin OpenJDK（推荐，无需注册）

1. **下载**  
   进入 [Eclipse Temurin 下载页](https://adoptium.net/temurin/releases/)，选择：
   - 版本：8
   - 操作系统：macOS
   - 架构：Apple Silicon 选 **aarch64**，Intel 选 **x64**
   - 格式：**PKG Installer**

2. **安装**  
   双击 `.pkg`，按向导默认安装。默认路径：`/Library/Java/JavaVirtualMachines/temurin-8.jdk`

3. **环境变量**（手动安装通常未自动配置）  
   - 打开终端（启动台 → 其他 → 终端）  
   - 编辑 Shell 配置（macOS 10.15+ 默认 Zsh）：
     ```bash
     # Zsh
     nano ~/.zshrc
     # 若使用 Bash
     nano ~/.bash_profile
     ```
   - 在文件末尾添加（路径按实际安装版本调整）：
     ```bash
     export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
     export PATH=$JAVA_HOME/bin:$PATH
     ```
   - 保存退出后执行：`source ~/.zshrc` 或 `source ~/.bash_profile`

#### 方法二：Oracle JDK

1. 进入 [Oracle JDK 下载页](https://www.oracle.com/java/technologies/downloads/#java8)，选择 macOS 对应架构的 `.dmg`
2. 双击 `.dmg`，将图标拖入「应用程序」完成安装
3. 环境变量同方法一，默认路径示例：`/Library/Java/JavaVirtualMachines/jdk1.8.0_391.jdk/Contents/Home`

#### 验证

在终端执行：

```bash
java -version
javac -version
echo $JAVA_HOME
```

示例输出：

```
openjdk version "1.8.0_392"
OpenJDK Runtime Environment (Temurin)(build 1.8.0_392-b08)
OpenJDK 64-Bit Server VM (Temurin)(build 25.392-b08, mixed mode)
javac 1.8.0_392
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
```

---

### 1.3 Linux 系统

#### 方法一：包管理器（推荐）

**Ubuntu / Debian：**

```bash
# 更新包列表
sudo apt update

# 安装 OpenJDK 8
sudo apt install openjdk-8-jdk

# 若有多个 JDK，可切换默认版本
sudo update-alternatives --config java
```

**CentOS / RHEL / Fedora：**

```bash
# CentOS/RHEL 7/8、Fedora
sudo yum install java-1.8.0-openjdk-devel

# Fedora 新版本可用 dnf
sudo dnf install java-1.8.0-openjdk-devel
```

**Arch Linux：**

```bash
sudo pacman -S jdk8-openjdk
```

#### 方法二：Temurin 手动安装

1. 打开 [Eclipse Temurin 下载页](https://adoptium.net/temurin/releases/)
2. 选择 **Linux**、对应架构（x64 / aarch64）、**JDK 8**，下载 `.tar.gz`
3. 解压并放到统一目录，例如：
   ```bash
   sudo mkdir -p /opt/java
   sudo tar -xzf OpenJDK8U-jdk_*.tar.gz -C /opt/java
   ```
4. 配置环境变量，编辑 `~/.bashrc` 或 `~/.profile`（若用 Zsh 则 `~/.zshrc`）：
   ```bash
   export JAVA_HOME=/opt/java/jdk8u-xxx  # 替换为实际解压目录名
   export PATH=$JAVA_HOME/bin:$PATH
   ```
5. 使配置生效：`source ~/.bashrc` 或 `source ~/.zshrc`

#### 验证

```bash
java -version
javac -version
echo $JAVA_HOME
```

---

## 二、JAR 方式安装与运行

这是**最简单、推荐**的方式，适用于所有平台。

---

### 2.1 Windows

#### 步骤 1：准备目录

1. 在任意盘符（如 D 盘）新建文件夹 `LinkMind`
2. 将 `LinkMind.jar` 复制到该文件夹

#### 步骤 2：启动

1. 按 `Win + X`，选择 **「Windows PowerShell」** 或 **「终端(管理员)」**
2. 进入目录并启动（路径按实际修改）：
   ```powershell
   cd D:\LinkMind
   java -jar LinkMind.jar
   ```
3. **首次启动**会自动：
   - 在当前目录下创建 `config`（配置文件）和 `data`（数据与 SQLite 等）
   - 生成默认配置 `lagi.yml`
4. **启动成功**：控制台出现 `Tomcat started on port(s): 8080 (http)`，且无红色报错

#### 步骤 3：访问

浏览器打开：**http://localhost:8080**

---

### 2.2 macOS

#### 步骤 1：准备目录

1. 在「访达」中进入「文稿」(Documents)，新建文件夹 `LinkMind`
2. 将 `LinkMind.jar` 放入该文件夹

#### 步骤 2：启动

1. 打开「启动台」→「其他」→「终端」
2. 执行：
   ```bash
   cd ~/Documents/LinkMind
   java -jar LinkMind.jar
   ```
3. 首次启动会自动创建 `config` 和 `data`

#### 步骤 3：访问

浏览器打开：**http://localhost:8080**

---

### 2.3 Linux

#### 步骤 1：准备目录

```bash
mkdir -p ~/LinkMind
# 将 LinkMind.jar 复制到 ~/LinkMind/LinkMind.jar
cp /path/to/LinkMind.jar ~/LinkMind 
```

#### 步骤 2：启动

```bash
cd ~/LinkMind
java -jar LinkMind.jar
```

如需后台运行（关闭终端后仍运行）：

```bash
nohup java -jar LinkMind.jar > linkmind.log 2>&1 &
```

首次启动同样会自动创建 `config` 和 `data`。

#### 步骤 3：访问

浏览器打开：**http://localhost:8080**

---

## 三、修改端口与数据目录

### 3.1 修改端口

若 8080 被占用，可指定其他端口：

```powershell
# Windows PowerShell
java -jar LinkMind.jar --port=8090
```

```bash
# macOS / Linux
java -jar LinkMind.jar --port=8090
```

然后访问：**http://localhost:8090**

### 3.2 指定配置与数据目录

**Windows：**

```powershell
java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
```

**macOS / Linux：**

```bash
java -jar LinkMind.jar --config=/path/to/config --data-dir=/path/to/data
```
