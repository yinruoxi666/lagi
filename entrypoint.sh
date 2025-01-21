#!/bin/bash

# 启动 Tomcat
$CATALINA_HOME/bin/catalina.sh start

# 启动 Chroma（假设它是通过某种方式启动的，可能是一个 Python 脚本或服务）
python3 -m chromadb.server &

# 保持容器前台运行，防止退出
tail -f /dev/null
