package ai.sevice;

import ai.common.pojo.VectorStoreConfig;
import ai.common.utils.LRUCache;
import ai.dto.openclaw.*;
import ai.vector.VectorStoreService;
import ai.vector.pojo.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class OpenClawService {
    private static final int MAX_SESSION_COUNT = 1000;
    private static final int MAX_VECTOR_COUNT = 1000;
    private static final LRUCache<String, Set<String>> contextCache = new LRUCache<>(MAX_SESSION_COUNT);
    private static final VectorStoreService vectorStoreService = new VectorStoreService();
    private static final float MAX_VECTOR_DISTANCE = 0.5f;
    private static final String VECTOR_STORE_PREFIX = "$oc$";
    private static final int COLLECTION_NAME_LENGTH = VECTOR_STORE_PREFIX.length() + 36;
    private static final int CLEAR_OLD_SESSION_INTERVAL_MINUTES = 1;
    private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "openclaw-session-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    static {
        CompletableFuture.runAsync(OpenClawService::loadSessions);
        CLEANUP_EXECUTOR.scheduleAtFixedRate(
                OpenClawService::clearOldSessions,
                CLEAR_OLD_SESSION_INTERVAL_MINUTES,
                CLEAR_OLD_SESSION_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private static void loadSessions() {
        List<VectorCollection> collections = vectorStoreService.listCollections();
        for (VectorCollection collection : collections) {
            String category = collection.getCategory();
            if (category.startsWith(VECTOR_STORE_PREFIX) && category.length() == COLLECTION_NAME_LENGTH) {
                contextCache.put(category, new HashSet<>());
                GetEmbedding getEmbedding = GetEmbedding.builder().limit(MAX_VECTOR_COUNT).offset(0).category(category).build();
                List<IndexRecord> indexRecords = vectorStoreService.get(getEmbedding);
                for (IndexRecord indexRecord : indexRecords) {
                    contextCache.get(category).add(indexRecord.getDocument());
                }
            }
        }
    }

    private static void clearOldSessions() {
        List<VectorCollection> collections = vectorStoreService.listCollections();
        if (collections == null || collections.isEmpty()) {
            return;
        }
        for (VectorCollection collection : collections) {
            if (collection == null) {
                continue;
            }
            String category = collection.getCategory();
            if (category == null || !category.startsWith(VECTOR_STORE_PREFIX) || category.length() != COLLECTION_NAME_LENGTH) {
                continue;
            }
            if (contextCache.containsKey(category)) {
                continue;
            }
            vectorStoreService.deleteCollection(category);
            contextCache.remove(category);
        }
        log.info("OpenClaw old sessions cleaned up.");
    }

    public List<AgentMessage> assemble(AssembleRequest assembleRequest) {
        String sessionId = assembleRequest.getSessionId();
        String prompt = assembleRequest.getPrompt();
        Set<String> relevantTexts = queryVector(sessionId, prompt);
        List<AgentMessage> messages = assembleRequest.getMessages();
        compressMessages(relevantTexts, messages);
        return messages;
    }

    private void compressMessages(Set<String> relevantTexts, List<AgentMessage> messages) {
        if (messages == null) {
            return;
        }
        for (AgentMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if ("toolResult".equals(message.getRole()) && message instanceof ToolResultMessage) {
                clearToolContents(relevantTexts, ((ToolResultMessage) message).getContent());
            }
            if ("assistant".equals(message.getRole()) && message instanceof AssistantMessage) {
                clearReasonContents(relevantTexts, ((AssistantMessage) message).getContent());
            }
        }
    }

    private void clearToolContents(Set<String> relevantTexts, List<Content> contents) {
        if (contents == null) {
            return;
        }
        for (Content content : contents) {
            if (content == null) {
                continue;
            }
            if (content instanceof TextContent) {
                String text = ((TextContent) content).getText();
                if (relevantTexts.contains(text)) {
                    continue;
                }
                ((TextContent) content).setText("");
            }
        }
    }

    private void clearReasonContents(Set<String> relevantTexts, List<Content> contents) {
        if (contents == null) {
            return;
        }
        for (Content content : contents) {
            if (content == null) {
                continue;
            }
            if (content instanceof ThinkingContent) {
                String thinking = ((ThinkingContent) content).getThinking();
                if (relevantTexts.contains(thinking)) {
                    continue;
                }
                ((ThinkingContent) content).setThinking("");
            }
        }
    }


    private Set<String> queryVector(String sessionId, String text) {
        QueryCondition queryCondition = new QueryCondition();
        queryCondition.setText(text);
        queryCondition.setCategory(sessionId);
        List<IndexRecord> indexRecords = vectorStoreService.query(queryCondition);
        if (indexRecords != null && !indexRecords.isEmpty()) {
            return indexRecords.stream()
                    .filter(record -> record.getDistance() <= MAX_VECTOR_DISTANCE)
                    .map(IndexRecord::getDocument)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    public void afterTurn(AfterTurnRequest afterRunRequest) {
        if (afterRunRequest == null) {
            return;
        }
        String sessionId = afterRunRequest.getSessionId();
        List<AgentMessage> messages = afterRunRequest.getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> texts = new ArrayList<>();
        for (AgentMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if ("toolResult".equals(message.getRole()) && message instanceof ToolResultMessage) {
                collectToolTextsForVector(((ToolResultMessage) message).getContent(), texts);
            }
            if ("assistant".equals(message.getRole()) && message instanceof AssistantMessage) {
                collectReasonTextsForVector(((AssistantMessage) message).getContent(), texts);
            }
        }
        vectorAdd(sessionId, texts);
    }

    private void vectorAdd(String sessionId, List<String> textList) {
        String category = getVectorStoreName(sessionId);
        Set<String> cachedContext = contextCache.get(category);
        if (textList == null || textList.isEmpty()) {
            return;
        }
        List<AddEmbedding.AddEmbeddingData> dataList = new ArrayList<>();
        for (String text : textList) {
            if (isBlank(text) || (cachedContext != null && cachedContext.contains(text))) {
                continue;
            }
            AddEmbedding.AddEmbeddingData row = new AddEmbedding.AddEmbeddingData();
            row.setDocument(text);
            dataList.add(row);
        }
        if (dataList.isEmpty()) {
            return;
        }
        AddEmbedding addEmbedding = AddEmbedding.builder()
                .category(category)
                .data(dataList)
                .build();
        vectorStoreService.add(addEmbedding);
        if (cachedContext == null) {
            contextCache.put(category, new HashSet<>(textList));
        } else {
            cachedContext.addAll(new HashSet<>(textList));
        }
    }

    private String getVectorStoreName(String sessionId) {
        return VECTOR_STORE_PREFIX + sessionId.replace("-", "_");
    }

    private void collectToolTextsForVector(List<Content> contents, List<String> out) {
        if (contents == null) {
            return;
        }
        for (Content content : contents) {
            if (content == null) {
                continue;
            }
            if (content instanceof TextContent) {
                String text = ((TextContent) content).getText();
                if (!isBlank(text)) {
                    out.add(text);
                }
            }
        }
    }

    private void collectReasonTextsForVector(List<Content> contents, List<String> out) {
        if (contents == null) {
            return;
        }
        for (Content content : contents) {
            if (content == null) {
                continue;
            }
            if (content instanceof ThinkingContent) {
                String thinking = ((ThinkingContent) content).getThinking();
                if (!isBlank(thinking)) {
                    out.add(thinking);
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
