# 使用Tomcat 8.5.38-jre8作为基础镜像
FROM tomcat:8.5.38-jre8

# 删除Tomcat webapps目录下的所有文件
RUN rm -rf /usr/local/tomcat/webapps/*

# 将lagi-web.war复制到Tomcat的webapps目录下
COPY ./lagi-web/target/ROOT.war /usr/local/tomcat/webapps/

# 暴露8080端口
EXPOSE 8080

# 启动Tomcat
CMD ["catalina.sh", "run"]