package ai.llm.pojo;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.volcengine.ark.runtime.model.content.generation.GetContentGenerationTaskResponse;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.model.responses.usage.Usage;

import java.util.List;
import java.util.Map;

public class OpenAiResponseResponse {
    private String id;
    private String object;
    @JsonProperty("created_at")
    private Long createdAt;

    private String status;

    // 为了兼容，将 error / incomplete_details 设计成单独对象（目前可能为 null）
//    private ResponseError error;
//
//    @JsonProperty("incomplete_details")
//    private IncompleteDetails incompleteDetails;

    private String instructions;

    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    private String model;

    // output 数组
    private List<ResponseOutputItem> output;

    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    @JsonProperty("previous_response_id")
    private String previousResponseId;

//    private Reasoning reasoning;

    private Boolean store;

    private Double temperature;

//    private ResponseTextConfig text;

    /**
     * tool_choice 既可以是字符串（"auto"），也可以是对象。
     * 这里简单建成 Object，如有需要可以再扩展为强类型。
     */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /**
     * tools 通常是一个工具定义数组，这里用 Object 占位，
     * 未来你可以根据官方 schema 定义 ResponseTool 类来替代。
     */
    private List<Object> tools;

    @JsonProperty("top_p")
    private Double topP;

    private String truncation;

    private Usage usage;

    private String user;

    private Map<String, Object> metadata;
}
