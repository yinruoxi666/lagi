#!/bin/bash

# 检查是否已安装 Docker
if ! command -v docker &> /dev/null
then
    echo "Docker 未安装，正在安装 Docker..."

    # 更新 apt 并安装依赖项
    sudo apt-get update
    sudo apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg-agent \
        software-properties-common

    # 添加 Docker 官方的 GPG 密钥
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

    # 设置 Docker 仓库
    sudo add-apt-repository \
       "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
       $(lsb_release -cs) \
       stable"

    # 安装 Docker
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io

    echo "Docker 安装完成。"
else
    echo "Docker 已安装。"
fi

# 检查 Docker 是否启动
if ! sudo systemctl is-active --quiet docker
then
    echo "Docker 未启动，正在启动 Docker..."
    sudo systemctl start docker
    echo "Docker 已启动。"
else
    echo "Docker 已在运行。"
fi

# 拉取 Docker 镜像
echo "正在拉取 chromadb/chroma 镜像..."
sudo docker pull chromadb/chroma:latest

echo "正在拉取 tomcat:8.5.38-jre8 镜像..."
sudo docker pull tomcat:8.5.38-jre8

# 启动 Chroma 容器
echo "正在启动 Chroma 容器..."
sudo docker run -d --name chroma-container -p 5000:5000 chromadb/chroma:latest

# 启动 Tomcat 容器
echo "正在启动 Tomcat 容器..."
sudo docker run -d --name tomcat-container -p 8080:8080 tomcat:8.5.38-jre8

echo "所有容器已启动。"
