package ai.manager;

import ai.config.pojo.RerankConfig;
import ai.rerank.adapter.IRerankAdapter;
import ai.rerank.adapter.RerankAdapterResult;
import ai.rerank.adapter.impl.BailianRerankAdapter;
import ai.rerank.adapter.impl.LocalRerankAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class RerankManagerTest {

    @Test
    void defaultsToLocalAdapterWhenConfigurationIsMissing() {
        RerankManager manager = new RerankManager();

        manager.initialize(null);

        assertInstanceOf(LocalRerankAdapter.class, manager.getAdapter());
    }

    @Test
    void loadsConfiguredAdapterByClassName() {
        RerankConfig config = new RerankConfig();
        config.setAdapter(TestRerankAdapter.class.getName());
        RerankManager manager = new RerankManager();

        manager.initialize(config);

        assertInstanceOf(TestRerankAdapter.class, manager.getAdapter());
    }

    @Test
    void loadsBailianAdapterWithValidCredentials() {
        RerankConfig config = new RerankConfig();
        config.setAdapter(BailianRerankAdapter.class.getName());
        config.setWorkspaceId("workspace-1");
        config.setApiKey("test-key");
        RerankManager manager = new RerankManager();

        manager.initialize(config);

        assertInstanceOf(BailianRerankAdapter.class, manager.getAdapter());
    }

    @Test
    void leavesAdapterUnavailableWhenBailianCredentialsAreMissing() {
        RerankConfig config = new RerankConfig();
        config.setAdapter(BailianRerankAdapter.class.getName());
        RerankManager manager = new RerankManager();

        manager.initialize(config);

        assertNull(manager.getAdapter());
    }

    @Test
    void doesNotFallBackToLocalForInvalidExplicitAdapter() {
        RerankConfig config = new RerankConfig();
        config.setAdapter("ai.rerank.adapter.impl.DoesNotExist");
        RerankManager manager = new RerankManager();

        manager.initialize(config);

        assertNull(manager.getAdapter());
    }

    @Test
    void rejectsConfiguredClassThatIsNotAnAdapter() {
        RerankConfig config = new RerankConfig();
        config.setAdapter(String.class.getName());
        RerankManager manager = new RerankManager();

        manager.initialize(config);

        assertNull(manager.getAdapter());
    }

    public static class TestRerankAdapter implements IRerankAdapter {
        @Override
        public void initialize(RerankConfig config) {
        }

        @Override
        public String getModelName() {
            return "test";
        }

        @Override
        public RerankAdapterResult rerank(String query, List<String> documents) {
            return null;
        }
    }
}
