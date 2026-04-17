package ai.utils;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.common.utils.LRUCache;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SensitiveWordUtil {
    private static final Map<String, WordRule> ruleMap = new HashMap<>();
    private static final Map<String, WordRule> inputRuleMap = new HashMap<>();
    private static final Map<String, Pattern> patternCache = new HashMap<>();
    private static int filterWindowLength = -1;

    public static final String INPUT_RULE_TYPE = "input";
    public static final String OUTPUT_RULE_TYPE = "output";

    /** 监控表 filter_name：输入侧敏感规则 */
    public static final String MONITOR_FILTER_INPUT = "sensitive_input";
    /** 监控表 filter_name：输出侧敏感规则 */
    public static final String MONITOR_FILTER_OUTPUT = "sensitive";

    private static String monitorNameForRuleType(String ruleType) {
        return INPUT_RULE_TYPE.equalsIgnoreCase(ruleType) ? MONITOR_FILTER_INPUT : MONITOR_FILTER_OUTPUT;
    }

    private static final LRUCache<String, String> filterSlidingWindow = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
    private static final LRUCache<String, Boolean> blockMap = new LRUCache<>(10000, 30, TimeUnit.MINUTES);

    static {
        WordRules wordRules = JsonFileLoadUtil.readWordLRulesList("/sensitive_word.json", WordRules.class);
        WordRules inputWordRules = JsonFileLoadUtil.readWordLRulesList("/sensitive_input.json", WordRules.class);
        pushWordRule(ruleMap,wordRules);
        pushWordRule(inputRuleMap,inputWordRules);
    }

    public static void setFilterWindowLength(int length) {
        filterWindowLength = length;
    }

    public static void pushOutputRule( WordRules wordRules) {
        pushWordRule(ruleMap,wordRules);
    }

    public static void pushInputRule( WordRules wordRules) {
        pushWordRule(inputRuleMap,wordRules);
    }

    private static Map<String, WordRule> getRuleMap(String  type) {
        if(INPUT_RULE_TYPE.equalsIgnoreCase(type)) {
            return inputRuleMap;
        } else {
            return ruleMap;
        }
    }

    public static void pushWordRule(Map<String,WordRule> ruleMap,  WordRules wordRules) {
        if (wordRules != null) {
            if (wordRules.getRules() != null) {
                wordRules.getRules().stream()
                        .filter(r -> StrUtil.isNotBlank(r.getRule()))
                        .peek(r -> {
                            if (r.getMask() == null) {
                                r.setMask(wordRules.getMask());
                            }
                            if (r.getLevel() == null) {
                                r.setLevel(wordRules.getLevel());
                            }
                        })
                        .forEach(r -> {
                            ruleMap.put(r.getRule(), r);
                        });
            }
        }
    }


    public static String filter(String message, String ruleType) {
        return filter(message, Integer.MAX_VALUE, ruleType);
    }

    public static String filter(String message, int times, String ruleType) {
        int count = 0;
        Set<String> rules = getRuleMap(ruleType).keySet();
        for (String rule : rules) {
            Pattern p = getCompiledPattern(rule);
            Matcher matcher = p.matcher(message);
            if (matcher.find()) {
                log.info("sensitive message: {} match group: {} \t rule:{}", message, matcher.group(), rule);
                WordRule wordRule = getRuleMap(ruleType).get(rule);
                if (wordRule != null) {
                    String filterContent = "匹配规则: " + rule + ", 原始内容: " + (message.length() > 500 ? message.substring(0, 500) : message);
                    if (wordRule.getLevel() == 1) {
                        message = "";
                        FilterMonitorUtil.recordFilterAction(monitorNameForRuleType(ruleType), "block", filterContent);
                        break;
                    } else if (wordRule.getLevel() == 2) {
                        message = message.replaceAll(rule, wordRule.getMask());
                        FilterMonitorUtil.recordFilterAction(monitorNameForRuleType(ruleType), "mask", filterContent);
                    } else if (wordRule.getLevel() == 3) {
                        message = message.replaceAll(rule, "");
                        FilterMonitorUtil.recordFilterAction(monitorNameForRuleType(ruleType), "erase", filterContent);
                    }
                    count++;
                }
                if(count >= times) {
                    break;
                }
            }
        }
        return message;
    }


    private static final class OutputRuleMatch {
        final WordRule wordRule;
        final String filterContent;

        OutputRuleMatch(WordRule wordRule, String filterContent) {
            this.wordRule = wordRule;
            this.filterContent = filterContent;
        }
    }

    private static OutputRuleMatch findFirstOutputRuleMatch(String message) {
        Set<String> rules = ruleMap.keySet();
        for (String rule : rules) {
            Pattern p = getCompiledPattern(rule);
            Matcher matcher = p.matcher(message);
            if (matcher.find()) {
                WordRule wordRule = ruleMap.get(rule);
                if (wordRule != null) {
                    String filterContent = "匹配规则: " + rule + ", 原始内容: "
                            + (message.length() > 500 ? message.substring(0, 500) : message);
                    return new OutputRuleMatch(wordRule, filterContent);
                }
            }
        }
        return null;
    }

    public static String getNullOrReplaceContent(String message) {
        OutputRuleMatch m = findFirstOutputRuleMatch(message);
        if (m == null) {
            return null;
        }
        Integer level = m.wordRule.getLevel();
        if (level == null) {
            return null;
        }
        if (level == 1) {
            return "";
        } else if (level == 2) {
            return m.wordRule.getMask();
        } else if (level == 3) {
            return "";
        }
        return null;
    }

    /**
     * 流式输出路径（SecurityFilterImpl）在首次命中输出敏感规则时写入监控，与 {@link #getNullOrReplaceContent} 判定一致。
     */
    public static void recordOutputStreamFilter(String message) {
        OutputRuleMatch m = findFirstOutputRuleMatch(message);
        if (m == null) {
            return;
        }
        Integer level = m.wordRule.getLevel();
        if (level == null) {
            return;
        }
        String action;
        if (level == 1) {
            action = "block";
        } else if (level == 2) {
            action = "mask";
        } else if (level == 3) {
            action = "erase";
        } else {
            return;
        }
        FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, action, m.filterContent);
    }



    public static ChatCompletionResult filter4ChatCompletionResult(ChatCompletionResult chatCompletionResult) {
        String message = chatCompletionResult.getChoices().get(0).getMessage().getContent();
        message = filter(message, Integer.MAX_VALUE, OUTPUT_RULE_TYPE);
        chatCompletionResult.getChoices().get(0).getMessage().setContent(message);
        return chatCompletionResult;
    }

    private static Pattern getCompiledPattern(String rule) {
        return patternCache.computeIfAbsent(rule, Pattern::compile);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult) {
        return filter(chatCompletionResult, false);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult, boolean stream) {
        if (filterWindowLength <= 0 || chatCompletionResult == null || chatCompletionResult.getChoices() == null || chatCompletionResult.getChoices().isEmpty()) {
            return chatCompletionResult;
        }
        ChatCompletionChoice chatCompletionChoice = chatCompletionResult.getChoices().get(0);
        ChatMessage chatMessage;

        if (stream) {
            if (chatCompletionChoice.getMessage() == null) {
                return chatCompletionResult;
            }
            if (chatCompletionChoice.getMessage().getContent() == null && chatCompletionChoice.getFinish_reason() == null) {
                return chatCompletionResult;
            }
            chatMessage = chatCompletionChoice.getMessage();
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
            if (filterSlidingWindow.containsKey(id)) {
                if (chatMessage.getContent() != null) {
                    chatMessage.setContent(filterSlidingWindow.get(id) + chatMessage.getContent());
                } else {
                    chatMessage.setContent(filterSlidingWindow.get(id));
                }
            }
            return chatCompletionResult;
        }
        String content = chatMessage.getContent();
        if (filterSlidingWindow.containsKey(id)) {
            String oldContent = filterSlidingWindow.get(id);
            chatMessage.setContent(oldContent + content);
        } else {
            filterSlidingWindow.put(id, content);
        }
        Set<String> rules = ruleMap.keySet();
        for (String rule : rules) {
            Pattern p = getCompiledPattern(rule);
            String message = chatMessage.getContent().toLowerCase();
            Matcher matcher = p.matcher(message);
            if (matcher.find()) {
                log.info("sensitive message: {} match group: {}", message, matcher.group());
                WordRule wordRule = ruleMap.get(rule);
                if (wordRule != null) {
                    String replaceContent = "";
                    String filterContent = "匹配规则: " + rule + ", 原始内容: " + (message.length() > 500 ? message.substring(0, 500) : message);
                    if (wordRule.getLevel() == 1) {
                        FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, "block", filterContent);
                        blockMap.put(id, true);
                        break;
                    } else if (wordRule.getLevel() == 2) {
                        replaceContent = message.replaceAll(rule, wordRule.getMask());
                        FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, "mask", filterContent);
                    } else if (wordRule.getLevel() == 3) {
                        replaceContent = message.replaceAll(rule, "");
                        FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, "erase", filterContent);
                    }
                    chatMessage.setContent(replaceContent);
                }
            }
        }

        if (blockMap.containsKey(id)) {
            chatMessage.setContent("");
            return chatCompletionResult;
        }

        if (stream) {
            String tempContent = chatMessage.getContent();
            if (tempContent.length() > filterWindowLength) {
                chatMessage.setContent(tempContent.substring(0, tempContent.length() - filterWindowLength));
                filterSlidingWindow.put(id, tempContent.substring(tempContent.length() - filterWindowLength));
            } else {
                chatMessage.setContent("");
                filterSlidingWindow.put(id, tempContent);
            }
        }
        return chatCompletionResult;
    }

    public static void clearRuleMap() {
        ruleMap.clear();
        patternCache.clear();
        filterSlidingWindow.clear();
        blockMap.clear();
        filterWindowLength = -1;
    }
}
