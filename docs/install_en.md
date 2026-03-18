# LinkMind Installation Guide

This guide explains how to install the JDK and run LinkMind via JAR on Windows, macOS, and Linux.

---

## 1. Install JDK

**JDK** is required to run or develop LinkMind (it includes the JRE runtime and the `javac` compiler). You can use:

- **Oracle JDK**: Commercial license, free for non-commercial use
- **Eclipse Temurin (Adoptium) OpenJDK**: Open source, free for all use (recommended)

---

### 1.1 Windows

#### Step 1: Download JDK

1. Open a browser and go to: [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/#java8) or [Eclipse Temurin Downloads](https://adoptium.net/temurin/releases/)
2. Select **Windows** and **x64** architecture
3. Select **JDK 8**
4. Package format:
   - Oracle JDK: `.exe` installer (e.g. `jdk-8u391-windows-x64.exe`)
   - Temurin JDK: `.msi` installer (can configure PATH automatically, recommended)

#### Step 2: Install

1. Double-click the installer; when User Account Control appears, click **Yes**
2. For Oracle JDK, check "Accept License Agreement", then click **Next**
3. Use an install path without spaces or special characters, e.g. `C:\Development_tools\jdk1.8.0_391`
4. If the wizard offers to install the JRE, leave it checked
5. Click **Next** to finish, then **Close**

#### Step 3: Environment variables (optional)

If you chose "Add to PATH" during install (Temurin .msi usually does this), you can skip this step and go to Step 4 to verify.

1. Right-click **This PC** → **Properties** → **Advanced system settings** → **Environment variables**
2. Under **System variables**, click **New**:
   - Variable name: `JAVA_HOME`
   - Variable value: JDK install path (e.g. `C:\Development_tools\jdk1.8.0_391`)
3. Edit the **Path** system variable and add:
   - `%JAVA_HOME%\bin`
   - `%JAVA_HOME%\jre\bin`
4. Click **OK** to save, then close any open command windows

#### Step 4: Verify

1. Press `Win + R`, type `cmd`, press Enter
2. In the command window, run:
   ```powershell
   java -version
   ```
3. If you see output similar to the following, the install succeeded:
   ```
   java version "1.8.0_221"
   Java(TM) SE Runtime Environment (build 1.8.0_221-b11)
   Java HotSpot(TM) 64-Bit Server VM (build 25.221-b11, mixed mode)
   ```
4. If you see "'java' is not recognized as an internal or external command", restart the computer and try again

---

### 1.2 macOS

#### Method 1: Temurin OpenJDK (recommended, no registration)

1. **Download**  
   Go to the [Eclipse Temurin download page](https://adoptium.net/temurin/releases/) and choose:
   - Version: 8
   - OS: macOS
   - Architecture: **aarch64** for Apple Silicon, **x64** for Intel
   - Package: **PKG Installer**

2. **Install**  
   Double-click the `.pkg` and follow the installer. Default path: `/Library/Java/JavaVirtualMachines/temurin-8.jdk`

3. **Environment variables** (manual install often does not set these)  
   - Open Terminal (Launchpad → Other → Terminal)  
   - Edit your shell config (macOS 10.15+ uses Zsh by default):
     ```bash
     # Zsh
     nano ~/.zshrc
     # If using Bash
     nano ~/.bash_profile
     ```
   - Add at the end (adjust path if your install differs):
     ```bash
     export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
     export PATH=$JAVA_HOME/bin:$PATH
     ```
   - Save and exit, then run: `source ~/.zshrc` or `source ~/.bash_profile`

#### Method 2: Oracle JDK

1. Go to the [Oracle JDK download page](https://www.oracle.com/java/technologies/downloads/#java8), choose the macOS `.dmg` for your architecture
2. Double-click the `.dmg` and drag the icon into **Applications**
3. Set environment variables as in Method 1; default path example: `/Library/Java/JavaVirtualMachines/jdk1.8.0_391.jdk/Contents/Home`

#### Verify

In Terminal, run:

```bash
java -version
javac -version
echo $JAVA_HOME
```

Example output:

```
openjdk version "1.8.0_392"
OpenJDK Runtime Environment (Temurin)(build 1.8.0_392-b08)
OpenJDK 64-Bit Server VM (Temurin)(build 25.392-b08, mixed mode)
javac 1.8.0_392
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
```

---

### 1.3 Linux

#### Method 1: Package manager (recommended)

**Ubuntu / Debian:**

```bash
# Update package list
sudo apt update

# Install OpenJDK 8
sudo apt install openjdk-8-jdk

# If you have multiple JDKs, switch default with:
sudo update-alternatives --config java
```

**CentOS / RHEL / Fedora:**

```bash
# CentOS/RHEL 7/8, Fedora
sudo yum install java-1.8.0-openjdk-devel

# On newer Fedora you can use dnf
sudo dnf install java-1.8.0-openjdk-devel
```

**Arch Linux:**

```bash
sudo pacman -S jdk8-openjdk
```

#### Method 2: Temurin manual install

1. Open the [Eclipse Temurin download page](https://adoptium.net/temurin/releases/)
2. Select **Linux**, your architecture (x64 or aarch64), and **JDK 8**, then download the `.tar.gz`
3. Extract to a directory, for example:
   ```bash
   sudo mkdir -p /opt/java
   sudo tar -xzf OpenJDK8U-jdk_*.tar.gz -C /opt/java
   ```
4. Set environment variables in `~/.bashrc` or `~/.profile` (or `~/.zshrc` if using Zsh):
   ```bash
   export JAVA_HOME=/opt/java/jdk8u-xxx  # Replace with actual extracted directory name
   export PATH=$JAVA_HOME/bin:$PATH
   ```
5. Reload the config: `source ~/.bashrc` or `source ~/.zshrc`

#### Verify

```bash
java -version
javac -version
echo $JAVA_HOME
```

---

## 2. Install and run via JAR

This is the **simplest and recommended** way to run LinkMind on any platform.

---

### 2.1 Windows

#### Step 1: Prepare directory

1. Create a folder named `LinkMind` on any drive (e.g. D:)
2. Copy `LinkMind.jar` into that folder

#### Step 2: Start

1. Press `Win + X` and choose **Windows PowerShell** or **Terminal (Admin)**
2. Go to the folder and start (change path if needed):
   ```powershell
   cd D:\LinkMind
   java -jar LinkMind.jar
   ```
3. On **first run**, the app will:
   - Create `config` (configuration) and `data` (data and SQLite, etc.) in the same folder
   - Generate the default config file `lagi.yml`
4. **Success**: You see `Tomcat started on port(s): 8080 (http)` in the console with no red errors

#### Step 3: Open in browser

Go to: **http://localhost:8080**

---

### 2.2 macOS

#### Step 1: Prepare directory

1. In Finder, go to **Documents** and create a folder named `LinkMind`
2. Put `LinkMind.jar` inside that folder

#### Step 2: Start

1. Open Terminal (Launchpad → Other → Terminal)
2. Run:
   ```bash
   cd ~/Documents/LinkMind
   java -jar LinkMind.jar
   ```
3. On first run, `config` and `data` are created automatically

#### Step 3: Open in browser

Go to: **http://localhost:8080**

---

### 2.3 Linux

#### Step 1: Prepare directory

```bash
mkdir -p ~/LinkMind
# Copy LinkMind.jar to ~/LinkMind/LinkMind.jar
cp /path/to/LinkMind.jar ~/LinkMind
```

#### Step 2: Start

```bash
cd ~/LinkMind
java -jar LinkMind.jar
```

To run in the background (keeps running after closing the terminal):

```bash
nohup java -jar LinkMind.jar > linkmind.log 2>&1 &
```

On first run, `config` and `data` are created automatically.

#### Step 3: Open in browser

Go to: **http://localhost:8080**. From another machine, use the Linux host’s IP, e.g. **http://192.168.1.100:8080**.

---

## 3. Change port and data directories

### 3.1 Change port

If port 8080 is in use, specify another port:

```powershell
# Windows PowerShell
java -jar LinkMind.jar --port=8090
```

```bash
# macOS / Linux
java -jar LinkMind.jar --port=8090
```

Then open: **http://localhost:8090**

### 3.2 Custom config and data directories

**Windows:**

```powershell
java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
```

**macOS / Linux:**

```bash
java -jar LinkMind.jar --config=/path/to/config --data-dir=/path/to/data
```

Example on Linux:

```bash
java -jar LinkMind.jar --config=/opt/linkmind/config --data-dir=/var/lib/linkmind/data
```
