package ai.utils;

import ai.config.pojo.StoreConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YmlLoaderTest {

    @Test
    void honorsJacksonPropertyNamesWhenLoadingIncludedStoreConfig() {
        StoreConfig stores = YmlLoader.loaderProperties(
                "yml-loader-store.yml", "stores", StoreConfig.class);

        assertNotNull(stores);
        assertNotNull(stores.getBigdata());
        assertEquals(1, stores.getBigdata().size());
        assertEquals("sqlite", stores.getBigdata().get(0).getName());
        assertEquals("ai.bigdata.impl.SqliteSearchAdapter",
                stores.getBigdata().get(0).getDriver());
    }
}
