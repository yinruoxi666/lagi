package ai.migrate.service;

import java.io.File;
import com.google.gson.Gson;

import ai.common.client.AiServiceCall;
import ai.common.client.AiServiceInfo;
import ai.common.pojo.Response;
import ai.common.pojo.MmeditingInferenceRequest;
import ai.common.pojo.MotInferenceRequest;
import ai.utils.FileUploadUtil;

public class VideoService {
    private Gson gson = new Gson();
    private AiServiceCall call = new AiServiceCall();

    public Response motInference(File file) {
        String videoUrl = FileUploadUtil.mmtrackingUpload(file);
        MotInferenceRequest request = new MotInferenceRequest();
        request.setVideoUrl(videoUrl);
        Object[] params = { gson.toJson(request) };
        String[] result = call.callWS(AiServiceInfo.WSVdoUrl, "motInference", params);
        Response response = gson.fromJson(result[0], Response.class);
        return response;
    }

    public Response mmeditingInference(File file) {
        String videoUrl = FileUploadUtil.mmeditingUpload(file);
        MmeditingInferenceRequest request = new MmeditingInferenceRequest();
        request.setVideoUrl(videoUrl);

        Object[] params = { gson.toJson(request) };
        String[] result = call.callWS(AiServiceInfo.WSVdoUrl, "mmeditingInference", params);
        Response response = gson.fromJson(result[0], Response.class);
        return response;
    }
}
