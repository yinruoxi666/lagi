package ai.servlet.api;

import ai.rerank.pojo.RerankRequest;
import ai.rerank.pojo.RerankResponse;
import ai.rerank.service.RerankService;
import ai.servlet.BaseServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RerankApiServlet extends BaseServlet {
    private final RerankService rerankService = new RerankService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("rerank")) {
            this.rerank(req, resp);
        }
    }

    private void rerank(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        RerankRequest rerankRequest = reqBodyToObj(req, RerankRequest.class);
        RerankResponse rerankResponse = rerankService.rerank(rerankRequest);
        responsePrint(resp, toJson(rerankResponse));
    }
}
