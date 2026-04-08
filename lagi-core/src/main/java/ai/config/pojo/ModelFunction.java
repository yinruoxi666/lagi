package ai.config.pojo;

import ai.common.pojo.Backend;
import ai.llm.utils.PolicyConstants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModelFunction extends Backend {
    @JsonProperty("route")
    private String route;
    @JsonProperty("backends")
    private List<Backend> backends;
    @JsonProperty("enable_queue_handle")
    private Boolean enableQueueHandle;
    @JsonProperty("handle")
    private String handle;
    @JsonProperty("grace_time")
    private Integer graceTime;
    @JsonProperty("maxgen")
    private Integer maxGen;
    @JsonProperty("context_length")
    private Integer contextLength;
    @JsonProperty("auth_api_key")
    private Boolean authApiKey;
    @JsonProperty("token_charge")
    private Boolean tokenCharge;

    @JsonCreator
    public ModelFunction(
            @JsonProperty("enable_queue_handle") Boolean enableQueueHandle,
            @JsonProperty("handle") String handle,
            @JsonProperty("grace_time") Integer graceTime,
            @JsonProperty("maxgen") Integer maxGen,
            @JsonProperty("context_length") Integer contextLength,
            @JsonProperty("auth_api_key") Boolean authApiKey,
            @JsonProperty("token_charge") Boolean tokenCharge
    ) {
        this.enableQueueHandle = enableQueueHandle != null && enableQueueHandle;
        this.handle = handle == null ? PolicyConstants.PARALLEL : handle;
        this.graceTime = graceTime == null ? 3600 : graceTime;
        this.maxGen = maxGen == null ? Integer.MAX_VALUE : maxGen ;
        this.contextLength = contextLength == null ? 4096 : contextLength;
        this.authApiKey = authApiKey == null ? false : authApiKey;
        this.tokenCharge = tokenCharge == null ? false : tokenCharge;
    }
}
