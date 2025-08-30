package ai.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class ProxyInfo {
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyUser;
    private final String proxyPass;

}
