package ai.utils;

import ai.common.pojo.ProxyInfo;

import java.net.MalformedURLException;

public class ProxyUtils {
    public static ProxyInfo parseProxyUrl(String proxyUrl) throws MalformedURLException {
        // 仅演示解析 socks5://username:password@host:port
        String prefix = "socks5://";
        if (!proxyUrl.startsWith(prefix)) {
            throw new MalformedURLException("Only support socks5://");
        }
        String urlWithoutSchema = proxyUrl.substring(prefix.length());

        String[] userAndHostPart = urlWithoutSchema.split("@");
        if (userAndHostPart.length != 2) {
            throw new MalformedURLException("Invalid proxy format: missing @");
        }

        String userPassPart = userAndHostPart[0]; // username:password
        String hostPortPart = userAndHostPart[1]; // host:port

        String[] userPassArr = userPassPart.split(":");
        if (userPassArr.length != 2) {
            throw new MalformedURLException("Invalid user:pass in proxy url");
        }
        String proxyUser = userPassArr[0];
        String proxyPass = userPassArr[1];

        String[] hostPortArr = hostPortPart.split(":");
        if (hostPortArr.length != 2) {
            throw new MalformedURLException("Invalid host:port in proxy url");
        }
        String proxyHost = hostPortArr[0];
        int proxyPort = Integer.parseInt(hostPortArr[1]);

        return new ProxyInfo(proxyHost, proxyPort, proxyUser, proxyPass);
    }

}
