package ai.config.pojo;

import ai.config.GlobalConfigurations;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RerankConfigTest {

    @Test
    void loadsRerankConfigurationFromFunctions() throws Exception {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        GlobalConfigurations configuration = mapper.readValue(
                "functions:\n"
                        + "  rerank:\n"
                        + "    adapter: ai.rerank.adapter.impl.BailianRerankAdapter\n"
                        + "    workspace_id: workspace-1\n"
                        + "    api_key: test-key\n",
                GlobalConfigurations.class);

        RerankConfig rerank = configuration.getFunctions().getRerank();
        assertEquals("ai.rerank.adapter.impl.BailianRerankAdapter", rerank.getAdapter());
        assertEquals("workspace-1", rerank.getWorkspaceId());
        assertEquals("test-key", rerank.getApiKey());
    }
}
