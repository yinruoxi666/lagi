# 使用适配的 JDK 8 版本基础镜像
FROM openjdk:8-jdk

# 安装 Tomcat 8.5.38
RUN wget https://archive.apache.org/dist/tomcat/tomcat-8/v8.5.38/bin/apache-tomcat-8.5.38.tar.gz \
    && tar xvfz apache-tomcat-8.5.38.tar.gz \
    && mv apache-tomcat-8.5.38 /usr/local/tomcat \
    && rm apache-tomcat-8.5.38.tar.gz

# 添加编译 SQLite 的步骤
RUN apt-get update && apt-get install -y \
    wget \
    build-essential \
    libsqlite3-dev \
    && wget https://www.sqlite.org/2021/sqlite-autoconf-3360000.tar.gz \
    && tar xvfz sqlite-autoconf-3360000.tar.gz \
    && cd sqlite-autoconf-3360000 \
    && ./configure --prefix=/usr/local \
    && make \
    && make install \
    && ldconfig \
    && cd .. \
    && rm -rf sqlite-autoconf-3360000 sqlite-autoconf-3360000.tar.gz

# 安装 Python3 和 pip
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip

# 安装 Chroma 的依赖
RUN pip3 install chromadb

# 复制你的应用程序到 Tomcat 的 webapps 目录
COPY lagi-web/target/ROOT.war /usr/local/tomcat/webapps/

# 设置环境变量
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH

# 暴露 Tomcat 和 Chroma 使用的端口
EXPOSE 8080 5000

# 复制启动脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 设置默认的启动命令
CMD ["/entrypoint.sh"]
