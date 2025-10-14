package ai.image.adapter.impl;

import ai.common.pojo.FileRequest;
import ai.openai.pojo.ChatCompletionRequest;
import ai.utils.ImageUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;


import java.io.IOException;

public class GPT4oAzureImageAdapter {

    private ChatCompletionRequest convertImage2TextRequest(FileRequest param) {
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
//        BeanUtil.copyProperties(param.getExtendParam(), image2TextRequest, "user_id");
//        String fileContentAsBase64 = null;
//        try {
//            fileContentAsBase64 = ImageUtil.getFileContentAsBase64(param.getImageUrl());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        if (StrUtil.isBlank(fileContentAsBase64)) {
//            return null;
//        }
//        image2TextRequest.setImage(fileContentAsBase64);
//        if(StrUtil.isBlank(image2TextRequest.getPrompt())) {
//            image2TextRequest.setPrompt("detail");
//        }
        return chatCompletionRequest;
    }

}
