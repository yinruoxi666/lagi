package ai.migrate.service;

import ai.dto.ChargeDetail;
import ai.dto.CreditUserBalance;
import ai.dto.PrepayRequest;
import ai.dto.PrepayResponse;
import ai.utils.AiGlobal;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreditService {
    private final Gson gson = new Gson();
    private static final String SAAS_BASE_URL = AiGlobal.SAAS_URL;

    public PrepayResponse prepay(PrepayRequest prepayRequest) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("lagiUserId", prepayRequest.getLagiUserId());
        params.put("fee", prepayRequest.getFee());
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/saas/api/console_pay/prepay", params);
        PrepayResponse response = gson.fromJson(resultJson, PrepayResponse.class);
        return response;
    }

    public ChargeDetail getChargeDetail(String outTradeNo) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("outTradeNo", outTradeNo);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/saas/api/console_pay/getAgentChargeDetail", params);
        ChargeDetail response = gson.fromJson(resultJson, ChargeDetail.class);
        return response;
    }

    public List<ChargeDetail> getChargeDetailByUserId(String userId) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/saas/api/console_pay/getChargeDetailByUserId", params);
        List<ChargeDetail> response = gson.fromJson(resultJson, new TypeToken<List<ChargeDetail>>() {
        }.getType());
        return response;
    }

    public CreditUserBalance getCreditUserBalance(String userId) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/saas/api/console_pay/getUserBalance", params);
        CreditUserBalance response = gson.fromJson(resultJson, CreditUserBalance.class);
        return response;
    }

    public ChargeDetail getChargeDetailBySeq(String seq) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("seq", seq);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/saas/api/console_pay/getChargeDetailBySeq", params);
        ChargeDetail response = gson.fromJson(resultJson, ChargeDetail.class);
        return response;
    }
}
