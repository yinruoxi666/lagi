package ai.agent.chat.lydaas.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LydaasChatRequest {
    private String query;
    private String agentInstanceId;
    private String bizInvokeFrom;
    private String tenantId;
    private Boolean isStream;
    private List<LydaasChatDataItem> dataList;
}
