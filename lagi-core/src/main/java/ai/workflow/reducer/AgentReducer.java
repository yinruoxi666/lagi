package ai.workflow.reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ai.learn.questionAnswer.KShingle;
import ai.mr.IReducer;
import ai.mr.reduce.BaseReducer;
import ai.openai.pojo.ChatCompletionResult;
import ai.qa.AiGlobalQA;
import ai.router.pojo.RouteAgentResult;
import ai.utils.qa.ChatCompletionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentReducer extends BaseReducer implements IReducer {
    private static final Logger logger = LoggerFactory.getLogger(AgentReducer.class);
    List<RouteAgentResult> result = new ArrayList<>();

    @Override
    public void myReducing(List<?> list) {
        Map<RouteAgentResult, Double> resultMap = new HashMap<>();
        for (Object mapperResult : list) {
            List<?> mapperList = (List<?>) mapperResult;
            RouteAgentResult chatCompletionResult = (RouteAgentResult) mapperList.get(AiGlobalQA.M_LIST_RESULT_TEXT);
            if (chatCompletionResult != null) {
                Double priority = chatCompletionResult.getPriority();
                if (resultMap.containsKey(chatCompletionResult)) {
                    if (priority > resultMap.get(chatCompletionResult)) {
                        resultMap.put(chatCompletionResult, priority);
                    }
                } else {
                    resultMap.put(chatCompletionResult, priority);
                }
            }
        }
        RouteAgentResult textResult = null;
        double highPriority = -1;
        for (Entry<RouteAgentResult, Double> entry : resultMap.entrySet()) {
            RouteAgentResult chatCompletionResult = entry.getKey();
            double priority = entry.getValue();
            if (priority > highPriority) {
                textResult = chatCompletionResult;
                highPriority = priority;
            }
            logger.info("AgentReducer: text = {}, priority = {}", ChatCompletionUtil.getFirstAnswer(chatCompletionResult.getResult().get(0)), priority);
        }
        result.add(textResult);
        logger.info("AgentReducer Finished Reducing...");
    }

    @Override
    public synchronized void myReducing(String mapperName, List<?> list, int priority) {
    }

    @Override
    public List<?> getResult() {
        return result;
    }
}
