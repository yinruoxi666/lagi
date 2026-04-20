package ai.agent.chat.lydaas.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LydaasThoughtChainContentInfo {
    private List<LydaasThoughtChainNode> thoughtChainList;
}
