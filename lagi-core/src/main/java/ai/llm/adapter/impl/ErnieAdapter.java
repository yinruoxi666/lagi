package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.common.utils.MappingIterable;
import ai.llm.utils.convert.ErnieConvert;
import ai.openai.pojo.*;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baidubce.qianfan.Qianfan;
import com.baidubce.qianfan.core.auth.Auth;
import com.baidubce.qianfan.model.chat.ChatRequest;
import com.baidubce.qianfan.model.chat.ChatResponse;
import com.baidubce.qianfan.model.chat.Function;
import com.baidubce.qianfan.model.chat.Message;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@LLM(modelNames = {"ERNIE-Speed-128K","ERNIE-Bot-turbo","ERNIE-4.0-8K","ERNIE-3.5-8K-0205","ERNIE-3.5-4K-0205", "ERNIE-3.5-8K-1222"})
public class ErnieAdapter extends OpenAIStandardAdapter {
    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://qianfan.baidubce.com/v2/chat/completions";
        }
        return apiAddress;
    }
}