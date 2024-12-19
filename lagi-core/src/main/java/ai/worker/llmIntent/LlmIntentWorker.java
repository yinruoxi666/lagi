package ai.worker.llmIntent;

import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.GPTAzureAdapter;
import ai.llm.adapter.impl.MoonshotAdapter;
import ai.llm.pojo.ChatgptChatCompletionRequest;
import ai.llm.pojo.ResponseFormat;
import ai.llm.service.CompletionsService;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LlmIntentWorker {

    // 调用ChatGpt获取意图
    public ChatCompletionResult process(ChatCompletionRequest chatCompletionRequest, String url) {
        ChatCompletionResult chatCompletionResult = null;
        // 将提示词整合进请求
        String system_prompt = "# 角色\n角色：你的任务是识别用户的意图。你所在的场景是福州机场，你扮演的是一名机场工作人员。对你进行提问的是机场内的旅客。" +
                "你要将用户的问题归类到以下几个类别中：机场相关类(airport_related)、非机场相关类(non-airport_related)\n" +
                "机场相关次要类别:\n" +
                "- 航班状态类(flight_status)：只能包含询问目的地、时间段、航班号信息以及想要去某个城市的想法。不包括询问飞机内服务、航班起降情况的、询问飞机机体信息意图。\n" +
                "- 识别是否查询机场内部地图导航的问题(airport_map)：包含并不限于登机口、行李寄存处、到达大厅、国际出发大厅、厕所、吸烟室等位置的咨询；餐饮、便利店、奢饰品店、化妆品店等商铺；派出所、机场服务台等行政位置；通过商品推荐相关商户，如土特产，小电器、书籍等找到相关的商户;自助值机柜台、航司值机柜台可以为旅客办理托运及打印登机牌的业务.\n  " +
                "- 乘机流程及相关规定的查询(airport_rules)：如中国民航局的统一乘机规定，或者各航空公司对乘机、托运、随身携带物品，年龄性别限制等规定的查询；比如飞机内服务、行李寄存服务咨询；对应突发事件的咨询，比如：身份证丢了怎么办？包括对飞机机体信息咨询；对福州市内的交通咨询；比如飞机行驶规范的咨询。包括对携带物品的要求：比如携带打火机、宠物、大件物品的咨询。\n\n" +
                "非机场相关次要类别:\n" +
                "- 天气类别(weather)，包括询问温度、风力、风速、天气状态等。\n  " +
                "- 最近新闻类别(news)\n " +
                "- 其他(other)\n" +
                "# 执行要求\n" +
                "1. 识别到的结果以json格式返回。\n" +
                "2. 请不要假设或猜测传入函数的参数值。如果用户的描述不明确，请要求用户提供必要信息。\n" +
                "3. json格式如下：\n```json\n{\n  \"primary\": \"airport_related\",\n \"secondary\": \"flight_status\"\n}\n```";
        ChatgptChatCompletionRequest chatgptChatCompletionRequest = new ChatgptChatCompletionRequest();
        BeanUtil.copyProperties(chatCompletionRequest, chatgptChatCompletionRequest);
        ResponseFormat responseFormat = new ResponseFormat();
        responseFormat.setType("json_object");
        chatgptChatCompletionRequest.setResponse_format(responseFormat);
        ChatMessage message = new ChatMessage();
        message.setRole("system");
        message.setContent(system_prompt);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message);
        messages.addAll(chatgptChatCompletionRequest.getMessages());
        chatgptChatCompletionRequest.setMessages(messages);
        chatgptChatCompletionRequest.setModel("gpt-4o-20240513");
//        MoonshotAdapter moonshotAdapter = (MoonshotAdapter)LlmManager.getInstance().getAdapter("gpt-4o-20240513");
//        chatCompletionResult = moonshotAdapter.intentCompletions(chatCompletionRequest);
        GPTAzureAdapter gptAzureAdapter = (GPTAzureAdapter)LlmManager.getInstance().getAdapter("gpt-4o-20240513");
        chatCompletionResult = gptAzureAdapter.completions(chatgptChatCompletionRequest);
        // 将返回结果json转成文字对应
        JSONObject jsonObject = JSONObject.parseObject(chatCompletionResult.getChoices().get(0).getMessage().getContent());
        String primary = jsonObject.getString("primary");
        String secondary = jsonObject.getString("secondary");
        String msg = "抱歉我无法识别您的意图。";
        switch (primary){
            case "airport_related":
                msg = "您是问我机场相关的问题。";
                break;
            case "non-airport_related":
                msg = "您是问我非机场相关的问题。";
                break;
        }
        switch (secondary){
            case "flight_status":
                msg = "您是问我航班的状态。";
                break;
            case "airport_map":
                msg = "您是问我机场的地图。";
                break;
            case "weather":
                msg = "您是问我天气。";
                break;
            case "news":
                msg = "您是问我新闻。";
                break;
            case "airport_rules":
                msg = "您是问我机场的规定。";
                break;
            case "other":
                msg = "您是问我其他问题。";
                break;
        }
        chatCompletionResult.getChoices().get(0).getMessage().setContent(msg);
        chatCompletionResult.getChoices().get(0).getMessage().setContext(jsonObject.toJSONString());
        return chatCompletionResult;
    }
}
