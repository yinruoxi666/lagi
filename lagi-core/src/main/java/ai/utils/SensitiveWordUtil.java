package ai.utils;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.config.pojo.FilterConfig;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SensitiveWordUtil {
    private static final Map<String, WordRule> ruleMap = new HashMap<>();
    private static final Map<String, Pattern> patternCache = new HashMap<>();
    private static final List<FilterConfig> filterConfigs = ContextLoader.configuration.getFilters();
    private static int filterWindowLength = -1;

    private static LRUCache<String, String> filterSlidingWindow = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
    private static LRUCache<String, Boolean> blockMap = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
    // Track which rules have been recorded for each request to avoid duplicate recording
    private static LRUCache<String, Set<String>> recordedRulesCache = new LRUCache<>(10000, 30, TimeUnit.MINUTES);

    static {
        WordRules wordRules = JsonFileLoadUtil.readWordLRulesList("/sensitive_word.json", WordRules.class);
        pushWordRule(wordRules);
        if (filterConfigs != null) {
            for (FilterConfig filter : filterConfigs) {
                if (filter.getName().equals("sensitive")) {
                    filterWindowLength = filter.getFilterWindowLength();
                }
            }
        }
    }

    public static void pushWordRule(WordRules wordRules) {
        if (wordRules != null) {
            if (wordRules.getRules() != null) {
                int addedCount = 0;
                for (WordRule r : wordRules.getRules()) {
                    if (StrUtil.isNotBlank(r.getRule())) {
                        r.setRule(r.getRule().toLowerCase());
                        if (r.getMask() == null) {
                            r.setMask(wordRules.getMask());
                        }
                        if (r.getLevel() == null) {
                            r.setLevel(wordRules.getLevel());
                        }
                        ruleMap.put(r.getRule(), r);
                        addedCount++;
                    }
                }
                log.info("重新加载敏感词规则, 规则数量: {}, 已添加: {}", wordRules.getRules().size(), addedCount);
                FilterMonitorUtil.recordFilterAction("sensitive", "reload", 
                    "重新加载敏感词规则, 规则数量: " + addedCount);
            } else {
                log.warn("WordRules.getRules() 为 null，无法加载规则");
            }
        } else {
            log.warn("WordRules 为 null，无法加载规则");
        }
    }

    public static void clearRuleMap() {
        ruleMap.clear();
        patternCache.clear();
        filterSlidingWindow.shutdown();
        blockMap.shutdown();
        recordedRulesCache.shutdown();
        filterSlidingWindow = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
        blockMap = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
        recordedRulesCache = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
    }

    private static Pattern getCompiledPattern(String rule) {
        return patternCache.computeIfAbsent(rule, Pattern::compile);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult) {
        return filter(chatCompletionResult, false);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult, boolean stream) {
        // 如果配置了规则，即使filterWindowLength未设置，也应该执行过滤
        // filterWindowLength <= 0 时使用默认值200（滑动窗口长度）
        if (chatCompletionResult == null || chatCompletionResult.getChoices() == null || chatCompletionResult.getChoices().isEmpty()) {
            return chatCompletionResult;
        }
        
        // 如果没有规则，直接返回
        if (ruleMap.isEmpty()) {
            return chatCompletionResult;
        }
        
        ChatCompletionChoice chatCompletionChoice = chatCompletionResult.getChoices().get(0);
        ChatMessage chatMessage;

        if (stream) {
            // For streaming, try delta first, fall back to message if delta is null
            if (chatCompletionChoice.getDelta() != null) {
                if (chatCompletionChoice.getDelta().getContent() == null && chatCompletionChoice.getFinish_reason() == null) {
                    return chatCompletionResult;
                }
                chatMessage = chatCompletionChoice.getDelta();
            } else if (chatCompletionChoice.getMessage() != null) {
                // Some adapters use message instead of delta for streaming
                if (chatCompletionChoice.getMessage().getContent() == null && chatCompletionChoice.getFinish_reason() == null) {
                    return chatCompletionResult;
                }
                chatMessage = chatCompletionChoice.getMessage();
            } else {
                return chatCompletionResult;
            }
        } else {
            if (chatCompletionChoice.getMessage() == null) {
                return chatCompletionResult;
            }
            if (chatCompletionChoice.getMessage().getContent() == null && chatCompletionChoice.getFinish_reason() == null) {
                return chatCompletionResult;
            }
            chatMessage = chatCompletionChoice.getMessage();
        }

        String finishReason = chatCompletionChoice.getFinish_reason();
        String id = chatCompletionResult.getId();

        if (blockMap.containsKey(id)) {
            chatMessage.setContent("");
            return chatCompletionResult;
        }

        if (finishReason != null && stream) {
            // Stream ended: combine accumulated content and apply filtering
            String finalContent;
            if (filterSlidingWindow.containsKey(id)) {
                if (chatMessage.getContent() != null) {
                    finalContent = filterSlidingWindow.get(id) + chatMessage.getContent();
                } else {
                    finalContent = filterSlidingWindow.get(id);
                }
            } else {
                finalContent = chatMessage.getContent() != null ? chatMessage.getContent() : "";
            }
            
            // Apply mask/erase for level 2 and 3 rules
            String processedContent = finalContent;
            Set<String> rules = ruleMap.keySet();
            for (String rule : rules) {
                WordRule wordRule = ruleMap.get(rule);
                if (wordRule != null && wordRule.getLevel() != 1) { // Skip level 1 (already blocked)
                    Pattern p = getCompiledPattern(rule);
                    Matcher matcher = p.matcher(processedContent);
                    // Use find() with start/end to replace all occurrences
                    StringBuffer sb = new StringBuffer();
                    while (matcher.find()) {
                        if (wordRule.getLevel() == 2) {
                            // Mask: replace with mask string
                            String mask = wordRule.getMask() != null ? wordRule.getMask() : "...";
                            matcher.appendReplacement(sb, mask);
                        } else if (wordRule.getLevel() == 3) {
                            // Erase: remove the matched text
                            matcher.appendReplacement(sb, "");
                        }
                    }
                    matcher.appendTail(sb);
                    processedContent = sb.toString();
                }
            }
            
            chatMessage.setContent(processedContent);
            
            // Clean up recorded rules cache when stream ends
            recordedRulesCache.remove(id);
            filterSlidingWindow.remove(id);
            return chatCompletionResult;
        }
        String content = chatMessage.getContent();
        if (content == null) {
            content = "";
        }
        
        // Accumulate content in sliding window
        String accumulatedContent;
        if (filterSlidingWindow.containsKey(id)) {
            String oldContent = filterSlidingWindow.get(id);
            accumulatedContent = oldContent + content;
        } else {
            accumulatedContent = content;
        }
        
        // For streaming chunks (finish_reason == null), accumulate and check for sensitive words
        // The sliding window clearing logic should only apply at the end
        if (stream && finishReason == null) {
            // Store accumulated content for cross-chunk sensitive word detection
            filterSlidingWindow.put(id, accumulatedContent);
            
            // Get or create recorded rules set for this request to avoid duplicate recording
            Set<String> recordedRules = recordedRulesCache.get(id);
            if (recordedRules == null) {
                recordedRules = new HashSet<>();
                recordedRulesCache.put(id, recordedRules);
            }
            
            // Check accumulated content for sensitive words (important for blocking)
            Set<String> rules = ruleMap.keySet();
            for (String rule : rules) {
                // Skip if this rule has already been recorded for this request
                if (recordedRules.contains(rule)) {
                    continue;
                }
                
                Pattern p = getCompiledPattern(rule);
                String message = accumulatedContent.toLowerCase();
                Matcher matcher = p.matcher(message);
                if (matcher.find()) {
                    log.info("sensitive message: {} match group: {}", message, matcher.group());
                    WordRule wordRule = ruleMap.get(rule);
                    if (wordRule != null) {
                        String actionType = "";
                        if (wordRule.getLevel() == 1) {
                            // Block immediately for level 1
                            blockMap.put(id, true);
                            actionType = "block";
                            FilterMonitorUtil.recordFilterAction("sensitive", actionType, 
                                "匹配规则: " + rule + ", 原始内容: " + (message.length() > 500 ? message.substring(0, 500) : message));
                            // Mark as recorded
                            recordedRules.add(rule);
                            // Clear current chunk content and return
                            chatMessage.setContent("");
                            return chatCompletionResult;
                        } else if (wordRule.getLevel() == 2) {
                            actionType = "mask";
                        } else if (wordRule.getLevel() == 3) {
                            actionType = "erase";
                        }
                        // For level 2 and 3, record but don't block immediately (filtering happens at end)
                        if (wordRule.getLevel() != 1) {
                            FilterMonitorUtil.recordFilterAction("sensitive", actionType, 
                                "匹配规则: " + rule + ", 原始内容: " + (message.length() > 500 ? message.substring(0, 500) : message));
                            // Mark as recorded to avoid duplicate recording
                            recordedRules.add(rule);
                        }
                    }
                }
            }
            
            // Return the original chunk content - filtering will happen at the end for non-block cases
            return chatCompletionResult;
        }
        
        // Non-streaming or final chunk: apply full filtering and sliding window logic
        String processedContent = accumulatedContent;
        Set<String> rules = ruleMap.keySet();
        
        // First, check for level 1 (block) - highest priority
        for (String rule : rules) {
            WordRule wordRule = ruleMap.get(rule);
            if (wordRule != null && wordRule.getLevel() == 1) {
                Pattern p = getCompiledPattern(rule);
                String message = processedContent.toLowerCase();
                Matcher matcher = p.matcher(message);
                if (matcher.find()) {
                    log.info("sensitive message (block): {} match group: {}", message, matcher.group());
                    blockMap.put(id, true);
                    FilterMonitorUtil.recordFilterAction("sensitive", "block", 
                        "匹配规则: " + rule + ", 原始内容: " + (message.length() > 500 ? message.substring(0, 500) : message));
                    chatMessage.setContent("");
                    return chatCompletionResult;
                }
            }
        }
        
        // Then apply mask/erase for level 2 and 3
        for (String rule : rules) {
            WordRule wordRule = ruleMap.get(rule);
            if (wordRule != null && wordRule.getLevel() != 1) {
                Pattern p = getCompiledPattern(rule);
                Matcher matcher = p.matcher(processedContent);
                if (matcher.find()) {
                    log.info("sensitive message (level {}): {} match group: {}", wordRule.getLevel(), processedContent, matcher.group());
                    String actionType = "";
                    StringBuffer sb = new StringBuffer();
                    matcher.reset(); // Reset to match from beginning
                    while (matcher.find()) {
                        if (wordRule.getLevel() == 2) {
                            // Mask: replace with mask string
                            String mask = wordRule.getMask() != null ? wordRule.getMask() : "...";
                            matcher.appendReplacement(sb, mask);
                            actionType = "mask";
                        } else if (wordRule.getLevel() == 3) {
                            // Erase: remove the matched text
                            matcher.appendReplacement(sb, "");
                            actionType = "erase";
                        }
                    }
                    matcher.appendTail(sb);
                    processedContent = sb.toString();
                    if (!actionType.isEmpty()) {
                        FilterMonitorUtil.recordFilterAction("sensitive", actionType, 
                            "匹配规则: " + rule + ", 原始内容: " + (processedContent.length() > 500 ? processedContent.substring(0, 500) : processedContent));
                    }
                }
            }
        }
        
        // Apply sliding window logic after filtering
        final int effectiveWindowLengthLocal = filterWindowLength > 0 ? filterWindowLength : 200;
        if (processedContent.length() > effectiveWindowLengthLocal) {
            chatMessage.setContent(processedContent.substring(0, processedContent.length() - effectiveWindowLengthLocal));
            filterSlidingWindow.put(id, processedContent.substring(processedContent.length() - effectiveWindowLengthLocal));
        } else {
            chatMessage.setContent(processedContent);
            filterSlidingWindow.put(id, "");
        }
        return chatCompletionResult;
    }
}
