package ai.servlet.api.openclaw;

import ai.dto.openclaw.AfterTurnRequest;
import ai.dto.openclaw.AfterTurnResponse;
import ai.dto.openclaw.AssembleRequest;
import ai.dto.openclaw.AssembleResponse;
import ai.servlet.BaseServlet;
import ai.sevice.OpenClawService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ContextApiServlet extends BaseServlet {
    private final OpenClawService openClawService = new OpenClawService();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");

        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "assemble":
                assemble(req, resp);
                break;
            case "afterTurn":
                afterTurn(req, resp);
                break;
            default:
                writeError(resp, HttpServletResponse.SC_NOT_FOUND, "Method not found");
                break;
        }
    }

    private void assemble(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String json = requestToJson(req);
            AssembleRequest assembleRequest = OBJECT_MAPPER.readValue(json, AssembleRequest.class);
            AssembleResponse assembleResponse = new AssembleResponse();
            assembleResponse.setStatus("success");
            assembleResponse.setMessages(openClawService.assemble(assembleRequest));
            String responseJson = OBJECT_MAPPER.writeValueAsString(assembleResponse);
            responsePrint(resp, responseJson);
        } catch (JsonProcessingException e) {
            log.warn("assemble: invalid JSON body", e);
            writeFailedJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "Invalid JSON");
        } catch (Exception e) {
            log.error("assemble: request processing failed", e);
            writeFailedJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Request processing failed");
        }
    }

    private void afterTurn(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String json = requestToJson(req);
            AfterTurnRequest afterTurnRequest = OBJECT_MAPPER.readValue(json, AfterTurnRequest.class);
            openClawService.afterTurn(afterTurnRequest);
            AfterTurnResponse afterTurnResponse = new AfterTurnResponse();
            afterTurnResponse.setStatus("success");
            responsePrint(resp, OBJECT_MAPPER.writeValueAsString(afterTurnResponse));
        } catch (JsonProcessingException e) {
            log.warn("afterTurn: invalid JSON body", e);
            writeFailedJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "Invalid JSON");
        } catch (Exception e) {
            log.error("afterTurn: request processing failed", e);
            writeFailedJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Request processing failed");
        }
    }

    private void writeFailedJson(HttpServletResponse resp, int httpStatus, String message) throws IOException {
        resp.setStatus(httpStatus);
        Map<String, String> body = new HashMap<>();
        body.put("status", "failed");
        body.put("msg", message);
        responsePrint(resp, OBJECT_MAPPER.writeValueAsString(body));
    }
}
