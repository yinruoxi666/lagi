package ai.manager;


import ai.bigdata.IBigdata;
import ai.config.ContextLoader;
import ai.config.pojo.BigdataConfig;
import cn.hutool.core.bean.BeanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BigdataManager {
    private final Logger log = LoggerFactory.getLogger(BigdataManager.class);

    private final Map<String, IBigdata> bigdataMap = new ConcurrentHashMap<>();

    private BigdataManager() {
    }

    private static final BigdataManager INSTANCE = new BigdataManager();

    public static BigdataManager getInstance() {
        return INSTANCE;
    }

    public void register(List<BigdataConfig> bigdataConfigs) {
        bigdataMap.clear();
        if (bigdataConfigs == null || bigdataConfigs.isEmpty()) {
            return;
        }
        bigdataConfigs.forEach(bigdataConfig -> {
            if (Boolean.FALSE.equals(bigdataConfig.getEnable())) {
                return;
            }
            Class<?> clazz;
            try {
                clazz = ContextLoader.getClass(bigdataConfig.getDriver());
                Constructor<?> constructor = clazz.getConstructor(BigdataConfig.class);
                IBigdata bigdata = (IBigdata) constructor.newInstance(bigdataConfig);
                BeanUtil.copyProperties(bigdataConfig, bigdata);
                IBigdata previous = bigdataMap.put(bigdataConfig.getName(), bigdata);
                if (previous != null) {
                    log.debug("Bigdata {} ({}) replaced on re-register", bigdataConfig.getName(), bigdata.getClass().getName());
                }
            } catch (Exception e) {
                log.error("oss {} name {} register failed error : {}", bigdataConfig.getDriver(), bigdataConfig.getName(), e.getMessage());
            }
        });
    }

    public IBigdata getBigdata(String name) {
        return bigdataMap.getOrDefault(name, null);
    }

    public IBigdata getBigdata() {
        if(bigdataMap.isEmpty()) {
            return null;
        }
        return bigdataMap.values().iterator().next();
    }
}
