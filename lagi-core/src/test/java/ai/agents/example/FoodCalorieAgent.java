package ai.agents.example;

import ai.agent.customer.CustomerAgent;
import ai.agent.customer.tools.FinishTool;
import ai.agents.example.tools.FoodCalorieTool;
import ai.config.pojo.AgentConfig;
import com.google.common.collect.Lists;

public class FoodCalorieAgent extends CustomerAgent {
    public FoodCalorieAgent(AgentConfig agentConfig) {
        super(agentConfig);
        FoodCalorieTool foodCalorieTool = new FoodCalorieTool(agentConfig.getToken());
        FinishTool finishTool = new FinishTool();
        this.toolInfoList = Lists.newArrayList(foodCalorieTool.getToolInfo(),
                finishTool.getToolInfo());

    }
}
