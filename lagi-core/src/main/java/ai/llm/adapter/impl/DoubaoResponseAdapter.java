package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.llm.pojo.DoubaoTranslationInputContentItem;
import com.volcengine.ark.runtime.model.responses.constant.ResponsesConstants;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemText;
import com.volcengine.ark.runtime.model.responses.item.ItemEasyMessage;
import com.volcengine.ark.runtime.model.responses.item.MessageContent;
import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.request.ResponsesInput;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.service.ArkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;


@LLM(modelNames = {"doubao-pro-32k,doubao-pro-4k"})
public class DoubaoResponseAdapter extends ModelService {

    private static final Logger log = LoggerFactory.getLogger(DoubaoAdapter.class);

    private String getModelEndpoint(String model) {
        Map<String, String> collect = Arrays.stream(alias.split(",")).collect(Collectors.toMap(a -> a.split("=")[0], a -> a.split("=")[1]));
        return collect.get(model);
    }
    ArkService arkService = ArkService.builder().apiKey("02e4119d-a191-4df5-82c2-615b1275f835").build();

    public ResponseObject response(String text) {


        CreateResponsesRequest request = CreateResponsesRequest.builder()
                .model("doubao-seed-translation-250915")
                .stream(false)
                .input(ResponsesInput.builder().addListItem(
                        ItemEasyMessage.builder().role(ResponsesConstants.MESSAGE_ROLE_USER).content(
                                MessageContent.builder()
                                        .addListItem(DoubaoTranslationInputContentItem.builder()
                                                .text(text).transFromTo("zh","en").build())
                                        .build())
                                .build())
                        .build())
                .build();
        return arkService.createResponse(request);
    }

    public static void main(String[] args) {
        DoubaoResponseAdapter adapter = new DoubaoResponseAdapter();
        ResponseObject response = adapter.response("今天天气怎么样？");
        System.out.println(response);
    }
}
