package ai.config;

import ai.common.pojo.*;
import ai.config.pojo.*;
import ai.manager.*;
import ai.medusa.utils.PromptCacheConfig;
import ai.ocr.OcrConfig;
import ai.router.Routers;
import ai.utils.*;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class GlobalConfigurations extends AbstractConfiguration {
    private Logger logger = LoggerFactory.getLogger(GlobalConfigurations.class);

    private String systemTitle;
    private List<Backend> models;
    private StoreConfig stores;
    private ModelFunctions functions;
    private List<AgentConfig> agents;
    private List<PnpConfig> pnps;

    private List<WorkerConfig> workers;
    private List<RouterConfig> routers;
    private List<FilterConfig> filters;

    private McpConfig mcps;

    private String includeModels;
    private String includeStores;
    private String includeAgents;
    private String includeMcps;
    private String includePnps;

    @Override
    public void init() {
        loadFromPropertiesFromYaml();
        EmbeddingManager.getInstance().register(functions.getEmbedding());
        BigdataManager.getInstance().register(stores.getBigdata());
        OSSManager.getInstance().register(stores.getOss());
        VectorStoreManager.getInstance().register(stores.getVectors(), stores.getRag(), functions.getEmbedding());
        MultimodalAIManager.register(models, functions);
        PromptCacheConfig.init(stores.getVectors(), stores.getMedusa());
        OcrConfig.init(functions.getImage2ocr());
        AgentManager.getInstance().register(agents);
        Routers.getInstance().register(workers, routers);
        Routers.getInstance().register(functions, routers);
        WorkerManager.getInstance().register(workers);
        McpManager.getInstance().register(mcps);
        PnpManager.getInstance().register(pnps);
        registerFilter();
    }

    private void loadFromPropertiesFromYaml() {
        try {
            if(models == null) {
                models = YmlPropertiesLoader.loaderProperties(getIncludeModels(), "models", new cn.hutool.core.lang.TypeReference<List<Backend>>(){});
            } else {
                List<Backend> models1 = YmlPropertiesLoader.loaderProperties(getIncludeModels(), "models", new cn.hutool.core.lang.TypeReference<List<Backend>>(){});
                if (models1 != null && models != null) {
                    models.addAll(models1);
                }
            }
            if(models != null) {
                Set<String> modelNames = models.stream().map(Backend::getName).collect(Collectors.toSet());
                models = models.stream().filter(model -> modelNames.contains(model.getName())).collect(Collectors.toList());
            }
        } catch (Exception ignored){}
        try {
            if(stores == null) {
                stores = YmlPropertiesLoader.loaderProperties(getIncludeStores(), "stores", StoreConfig.class);
            } else {
                StoreConfig stores1 = YmlPropertiesLoader.loaderProperties(getIncludeStores(), "stores", StoreConfig.class);
                if(stores1 != null) {
                    CopyOptions copyOption = CopyOptions.create(null, true, "vectors");
                    BeanUtil.copyProperties(stores1, stores, copyOption);
                    if(stores.getVectors() != null  && stores1.getVectors() != null) {
                        stores.getVectors().addAll(stores1.getVectors());
                    }
                }
            }
            if(stores != null && stores.getVectors() != null) {
                List<VectorStoreConfig> vectors = stores.getVectors();
                Set<String> vectorNames = vectors.stream().map(VectorStoreConfig::getName).collect(Collectors.toSet());
                stores.setVectors(vectors.stream().filter(vector -> vectorNames.contains(vector.getName())).collect(Collectors.toList()));
            }

        }catch (Exception ignored) {}
        try {
            if(agents == null) {
                agents = YmlPropertiesLoader.loaderProperties(getIncludeAgents(), "agents", new cn.hutool.core.lang.TypeReference<List<AgentConfig>>(){});
            } else {
                List<AgentConfig> agents1 = YmlPropertiesLoader.loaderProperties(getIncludeAgents(), "agents", new cn.hutool.core.lang.TypeReference<List<AgentConfig>>(){});
                if (agents1 != null && agents != null) {
                    agents.addAll(agents1);
                }
            }
            if(agents != null) {
                Set<String> agentNames = agents.stream().map(AgentConfig::getName).collect(Collectors.toSet());
                agents = agents.stream().filter(model -> agentNames.contains(model.getName())).collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
        try {
            if(mcps == null) {
                mcps = YmlPropertiesLoader.loaderProperties(getIncludeMcps(), "mcps", McpConfig.class);
            } else {
                McpConfig mcps1 = YmlPropertiesLoader.loaderProperties(getIncludeMcps(), "mcps", McpConfig.class);
                if(mcps1 != null) {
                    CopyOptions copyOption = CopyOptions.create(null, true, "servers");
                    BeanUtil.copyProperties(mcps1, mcps, copyOption);
                    if(mcps.getServers() != null  && mcps1.getServers() != null) {
                        mcps.getServers().addAll(mcps1.getServers());
                    }
                }
            }
            if(mcps != null && mcps.getServers() != null) {
                List<McpBackend> mcpBackends = mcps.getServers();
                Set<String> mcpServerNames = mcpBackends.stream().map(McpBackend::getName).collect(Collectors.toSet());
                mcps.setServers(mcpBackends.stream().filter(vector -> mcpServerNames.contains(vector.getName())).collect(Collectors.toList()));
            }
        }catch (Exception ignored) {}

        try {
            if(pnps == null) {
                pnps = YmlPropertiesLoader.loaderProperties(getIncludePnps(), "pnps", new cn.hutool.core.lang.TypeReference<List<PnpConfig>>(){});
            } else {
                List<PnpConfig> pnps2 = YmlPropertiesLoader.loaderProperties(getIncludePnps(), "pnps", new cn.hutool.core.lang.TypeReference<List<PnpConfig>>(){});
                if (pnps2 != null && pnps != null) {
                    pnps.addAll(pnps2);
                }
            }
            if(pnps != null) {
                Set<String> pnpNames = pnps.stream().map(PnpConfig::getName).collect(Collectors.toSet());
                pnps = pnps.stream().filter(model -> pnpNames.contains(model.getName())).collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
    }



    private void registerFilter() {
        if (filters != null) {
            for (FilterConfig filter : filters) {
                if(filter.getName().equals("sensitive")) {
                    push2wordRule(filter);
                } else if(filter.getName().equals("priority")) {
                    PriorityWordUtil.addWords(convert2List(filter));
                } else if (filter.getName().equals("continue")) {
                    ContinueWordUtil.addWords(convert2List(filter));
                } else if (filter.getName().equals("stopping")) {
                    StoppingWordUtil.addWords(convert2List(filter));
                } else if(filter.getName().equals("retain")) {
                    StoppingWordUtil.addWords(convert2List(filter));
                }
            }
        }
    }


    private List<String> convert2List(FilterConfig filterItem) {
        return convert2ListRules(filterItem.getRules());
    }


    private static List<String> convert2ListRules(String rules){
        String s = rules.replaceAll("\\\\\\\\,", "·regx-dot·");
        List<String> collect = Arrays.stream(s.split(",")).map(String::trim).collect(Collectors.toList());
        collect = collect.stream().map(temp -> temp.replaceAll("·regx-dot·", ",")).collect(Collectors.toList());
        return collect;
    }


    private void push2wordRule(FilterConfig filter) {
        if (filter.getGroups() == null || filter.getGroups().isEmpty()) {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalConfigurations.class);
            log.warn("FilterConfig groups 为空，无法加载敏感词规则: {}", filter.getName());
            return;
        }
        
        List<WordRule> rules = filter.getGroups().stream()
                                .flatMap(group-> {
                                        if (group.getRules() == null || group.getRules().trim().isEmpty()) {
                                            return java.util.stream.Stream.empty();
                                        }
                                        return convert2ListRules(group.getRules()).stream()
                                                .map(rule -> {
                                                        String level = group.getLevel();
                                                        String mask = group.getMask();
                                                        int levelInt = 0;
                                                        
                                                        // 支持数字字符串和单词两种格式
                                                        if (level != null) {
                                                            if ("erase".equalsIgnoreCase(level) || "3".equals(level)) {
                                                                levelInt = 3;
                                                            } else if ("block".equalsIgnoreCase(level) || "1".equals(level)) {
                                                                levelInt = 1;
                                                            } else if ("mask".equalsIgnoreCase(level) || "2".equals(level)) {
                                                                levelInt = 2;
                                                            } else {
                                                                // 尝试解析为整数
                                                                try {
                                                                    levelInt = Integer.parseInt(level);
                                                                    if (levelInt < 1 || levelInt > 3) {
                                                                        levelInt = 2; // 默认使用掩码
                                                                    }
                                                                } catch (NumberFormatException e) {
                                                                    levelInt = 2; // 默认使用掩码
                                                                }
                                                            }
                                                        } else {
                                                            levelInt = 2; // 默认使用掩码
                                                        }
                                                        
                                                        rule = rule.trim();
                                                        return WordRule.builder().level(levelInt).mask(mask).rule(rule).build();
                                                });
                                    })
                                .collect(Collectors.toList());
        
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalConfigurations.class);
        log.info("从 FilterConfig 加载敏感词规则: 分组数量={}, 规则数量={}", filter.getGroups().size(), rules.size());
        
        WordRules wordRules = WordRules.builder()
                .rules(rules)
                .build();
        SensitiveWordUtil.pushWordRule(wordRules);
    }

    @Override
    public Configuration transformToConfiguration() {
        List<Backend> chatBackends = functions.getChat().getBackends().stream().map(backendMatch -> {
            Optional<Backend> any = models.stream().filter(backend -> backend.getEnable() && backendMatch.getEnable() && backendMatch.getBackend().equals(backend.getName())).findAny();
            Backend backend = any.orElse(null);
            if(backend != null) {
                backend.setPriority(backendMatch.getPriority());
            }
            return backend;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        LLM llm = LLM.builder().backends(models).embedding(functions.getEmbedding().get(0))
                .chatBackends(chatBackends)
                .build();
        llm.getBackends().forEach(backend -> {
            if (backend.getPriority() == null) {
                backend.setPriority(10);
            }
        });

        return Configuration.builder()
                .systemTitle(systemTitle)
                .vectorStores(stores.getVectors())
                .LLM(llm)
                .ASR(ASR.builder().backends(functions.getSpeech2text()).build())
                .TTS(TTS.builder().backends(functions.getText2speech()).build())
                .imageEnhance(ImageEnhance.builder().backends(functions.getImage2Enhance()).build())
                .imageGeneration(ImageGeneration.builder().backends(functions.getText2image()).build())
                .imageCaptioning(ImageCaptioning.builder().backends(functions.getImage2text()).build())
                .videoEnhance(VideoEnhance.builder().backends(functions.getVideo2Enhance()).build())
                .videoGeneration(VideoGeneration.builder().backends(functions.getImage2video()).build())
                .videoTrack(VideoTrack.builder().backends(functions.getVideo2Track()).build())
                .agents(agents)
                .workers(workers)
                .build();
    }


}
