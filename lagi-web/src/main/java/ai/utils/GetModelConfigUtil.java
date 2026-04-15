package ai.utils;

import ai.dto.LagiModelInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class GetModelConfigUtil {
    private static final String URL = "https://saas.landingbj.com/saas/api/apikey/listModelInfo";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        try {
            String response = OkHttpUtil.get(URL);
            List<LagiModelInfo> modelInfos = parseResponse(response);
            printGroupedByProvider(modelInfos);
            printLandingSummary(modelInfos);
        } catch (IOException e) {
            System.err.println("Request failed: " + e.getMessage());
        }
    }

    private static List<LagiModelInfo> parseResponse(String response) {
        Type listType = new TypeToken<List<LagiModelInfo>>() {}.getType();
        List<LagiModelInfo> modelInfos = GSON.fromJson(response, listType);
        return modelInfos == null ? new ArrayList<LagiModelInfo>() : modelInfos;
    }

    private static void printGroupedByProvider(List<LagiModelInfo> modelInfos) {
        Map<String, List<String>> providerModelsMap = new LinkedHashMap<String, List<String>>();
        for (LagiModelInfo modelInfo : modelInfos) {
            if (modelInfo == null || modelInfo.getProvider() == null || modelInfo.getModelName() == null) {
                continue;
            }
            List<String> models = providerModelsMap.get(modelInfo.getProvider());
            if (models == null) {
                models = new ArrayList<String>();
                providerModelsMap.put(modelInfo.getProvider(), models);
            }
            models.add(modelInfo.getModelName());
        }

        for (Map.Entry<String, List<String>> entry : providerModelsMap.entrySet()) {
            System.out.println("type: " + entry.getKey());
            System.out.println("enable: true");
            System.out.println("model: " + joinWithComma(entry.getValue()));
            System.out.println();
        }
    }

    private static void printLandingSummary(List<LagiModelInfo> modelInfos) {
        List<String> allModels = new ArrayList<String>();
        for (LagiModelInfo modelInfo : modelInfos) {
            if (modelInfo == null || modelInfo.getProvider() == null || modelInfo.getModelName() == null) {
                continue;
            }
            allModels.add(modelInfo.getProvider() + "/" + modelInfo.getModelName());
        }

        System.out.println("type: Landing");
        System.out.println("enable: true");
        System.out.println("model: " + joinWithComma(allModels));
    }

    private static String joinWithComma(List<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add(value);
        }
        return joiner.toString();
    }
}
