package ai.servlet;

import ai.dto.ChargeDetail;
import ai.dto.CreditUserBalance;
import ai.dto.PrepayRequest;
import ai.dto.PrepayResponse;
import ai.migrate.service.CreditService;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CreditServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final CreditService creditService = new CreditService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("getChargeDetail")) {
            this.getChargeDetail(req, resp);
        } else if (method.equals("getChargeDetailByUserId")) {
            this.getChargeDetailByUserId(req, resp);
        } else if (method.equals("getCreditUserBalance")) {
            this.getCreditUserBalance(req, resp);
        } else if (method.equals("getChargeDetailBySeq")) {
            this.getChargeDetailBySeq(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("prepay")) {
            this.prepay(req, resp);
        }
    }

    private void prepay(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        PrepayRequest prepayRequest = reqBodyToObj(req, PrepayRequest.class);
        PrepayResponse prepayResponse = creditService.prepay(prepayRequest);
        responsePrint(resp, gson.toJson(prepayResponse));
    }

    private void getChargeDetail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String outTradeNo = req.getParameter("outTradeNo");
        ChargeDetail agentChargeDetail = creditService.getChargeDetail(outTradeNo);
        responsePrint(resp, gson.toJson(agentChargeDetail));
    }

    private void getChargeDetailByUserId(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String userId = req.getParameter("userId");
        List<ChargeDetail> chargeDetails = creditService.getChargeDetailByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", chargeDetails);
        responsePrint(resp, gson.toJson(result));
    }

    private void getCreditUserBalance(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String userId = req.getParameter("userId");
        CreditUserBalance creditUserBalance = creditService.getCreditUserBalance(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", creditUserBalance);
        responsePrint(resp, gson.toJson(result));
    }

    private void getChargeDetailBySeq(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String seq = req.getParameter("seq");
        ChargeDetail chargeDetail = creditService.getChargeDetailBySeq(seq);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", chargeDetail);
        responsePrint(resp, gson.toJson(result));
    }
}
