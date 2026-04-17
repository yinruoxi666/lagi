package ai.servlet;

import ai.common.pojo.Configuration;
import ai.common.pojo.VectorStoreConfig;
import ai.migrate.service.UserService;
import ai.servlet.dto.LoginRequest;
import ai.servlet.dto.LoginResponse;
import ai.servlet.dto.RegisterRequest;
import ai.servlet.dto.RegisterResponse;
import ai.sevice.CookieService;
import ai.utils.MigrateGlobal;
import ai.utils.ValidateCodeCreator;
import ai.vector.VectorStoreService;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class UserServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final UserService userService = new UserService();
    private final CookieService cookieService = new CookieService();
    private static final Configuration config = MigrateGlobal.config;
    private static final String COOKIE_NAME = "lagi-auth";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 7;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("getRandomCategory")) {
            this.getRandomCategory(req, resp);
        } else if (method.equals("getDefaultTitle")) {
            this.getDefaultTitle(req, resp);
        } else if (method.equals("getCaptcha")) {
            this.getCaptcha(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("login")) {
            this.login(req, resp);
        } else if (method.equals("register")) {
            this.register(req, resp);
        } else if (method.equals("authLoginCookie")) {
            this.authLoginCookie(req, resp);
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        LoginResponse loginResponse;
        try {
            LoginRequest loginRequest = reqBodyToObj(req, LoginRequest.class);
            HttpSession session = req.getSession(true);
            String sessionCode = (String) session.getAttribute("captcha");
            if (sessionCode == null || !sessionCode.equalsIgnoreCase(loginRequest.getCaptcha())) {
                loginResponse = new LoginResponse();
                loginResponse.setStatus("failed");
                loginResponse.setMsg("验证码错误");
            } else {
                loginResponse = userService.login(loginRequest);
                if (loginResponse != null && "success".equalsIgnoreCase(loginResponse.getStatus())) {
                    addCookies(req, resp, loginRequest, loginResponse);
                }
            }
        } catch (Exception e) {
            loginResponse = new LoginResponse();
            loginResponse.setStatus("failed");
            loginResponse.setMsg("登录服务暂时不可用，请稍后重试");
        }
        if (loginResponse == null) {
            loginResponse = new LoginResponse();
            loginResponse.setStatus("failed");
            loginResponse.setMsg("登录失败");
        }
        responsePrint(resp, gson.toJson(loginResponse));
    }

    private void addCookies(HttpServletRequest req, HttpServletResponse resp, LoginRequest loginRequest, LoginResponse loginResponse) {
        String encodeValue = cookieService.encodeUser(loginRequest.getUsername(), loginRequest.getPassword());
        if (loginResponse == null || loginResponse.getData() == null || loginResponse.getData().getUserId() == null) {
            addCookie(req, resp, COOKIE_NAME, encodeValue);
            return;
        }
        Map<String, String> cookies = new HashMap<>();
        cookies.put(COOKIE_NAME, encodeValue);
        cookies.put("userId", loginResponse.getData().getUserId());
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            addCookie(req, resp, entry.getKey(), entry.getValue());
        }
    }

    private void addCookie(HttpServletRequest req, HttpServletResponse resp, String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setDomain(req.getServerName());
        cookie.setPath("/");
        resp.addCookie(cookie);
    }

    private void removeCookies(HttpServletRequest req, HttpServletResponse resp) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) || "userId".equals(cookie.getName())) {
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    resp.addCookie(cookie);
                }
            }
        }
    }

    private void register(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        RegisterResponse registerResponse;
        try {
            RegisterRequest registerRequest = reqBodyToObj(req, RegisterRequest.class);
            HttpSession session = req.getSession(true);
            String sessionCode = (String) session.getAttribute("captcha");
            if (sessionCode == null || !sessionCode.equalsIgnoreCase(registerRequest.getCaptcha())) {
                registerResponse = new RegisterResponse();
                registerResponse.setStatus("failed");
                registerResponse.setMsg("验证码错误");
            } else {
                registerResponse = userService.register(registerRequest);
                if (registerResponse != null && "success".equalsIgnoreCase(registerResponse.getStatus())) {
                    LoginRequest loginRequest = new LoginRequest();
                    loginRequest.setUsername(registerRequest.getUsername());
                    loginRequest.setPassword(registerRequest.getPassword());
                    try {
                        LoginResponse loginResponse = userService.login(loginRequest);
                        if (loginResponse != null && "success".equalsIgnoreCase(loginResponse.getStatus())) {
                            addCookies(req, resp, loginRequest, loginResponse);
                        } else {
                            addCookie(req, resp, COOKIE_NAME, cookieService.encodeUser(
                                    registerRequest.getUsername(), registerRequest.getPassword()));
                        }
                    } catch (IOException e) {
                        addCookie(req, resp, COOKIE_NAME, cookieService.encodeUser(
                                registerRequest.getUsername(), registerRequest.getPassword()));
                    }
                }
            }
        } catch (Exception e) {
            registerResponse = new RegisterResponse();
            registerResponse.setStatus("failed");
            registerResponse.setMsg("注册服务暂时不可用，请稍后重试");
        }
        if (registerResponse == null) {
            registerResponse = new RegisterResponse();
            registerResponse.setStatus("failed");
            registerResponse.setMsg("注册失败");
        }
        responsePrint(resp, gson.toJson(registerResponse));
    }

    private void authLoginCookie(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        LoginResponse loginResponse;
        try {
            JsonObject jsonObject = reqBodyToObj(req, JsonObject.class);
            String cookieValue = jsonObject.get("cookieValue").getAsString();
            LoginRequest loginRequest = cookieService.decodeUser(cookieValue);
            loginResponse = userService.login(loginRequest);
            if (loginResponse != null && "success".equalsIgnoreCase(loginResponse.getStatus())) {
                addCookies(req, resp, loginRequest, loginResponse);
            } else {
                removeCookies(req, resp);
            }
        } catch (Exception e) {
            loginResponse = new LoginResponse();
            loginResponse.setStatus("failed");
            loginResponse.setMsg("认证失败");
        }
        if (loginResponse == null) {
            loginResponse = new LoginResponse();
            loginResponse.setStatus("failed");
            loginResponse.setMsg("认证失败");
        }
        responsePrint(resp, gson.toJson(loginResponse));
    }

    private void getRandomCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String currentCategory = req.getParameter("currentCategory");
        String userId = req.getParameter("userId");
        JsonObject data = new JsonObject();
        String category;
        VectorStoreConfig vectorStoreConfig = new VectorStoreService().getVectorStoreConfig();
        if (vectorStoreConfig == null) {
            category = null;
        } else {
            category = vectorStoreConfig.getDefaultCategory();
        }
        if (category == null) {
            if (currentCategory == null || currentCategory.isEmpty()) {
                category = userService.getRandomCategory();
            } else {
                category = currentCategory;
            }
        }
        if(StrUtil.isNotBlank(userId)) {
            category = category + "_" + userId;
        }
        data.addProperty("category", category);
        Map<String, Object> map = new HashMap<>();
        if (category != null) {
            map.put("status", "success");
            map.put("data", data);
        } else {
            map.put("status", "failed");
        }
        responsePrint(resp, gson.toJson(map));
    }

    private void getDefaultTitle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        map.put("data", config.getSystemTitle());
        responsePrint(resp, gson.toJson(map));
    }

    private void getCaptcha(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("image/jpeg");
        int width = 60;
        int height = 20;
        int charNum = 4;
        int fontSize = 18;
        String num = req.getParameter("charNum");
        if (num != null) {
            charNum = Integer.parseInt(num);
            width = charNum * 15;
        }
        if (req.getParameter("width") != null) {
            width = Integer.parseInt(req.getParameter("width"));
        }

        if (req.getParameter("height") != null) {
            height = Integer.parseInt(req.getParameter("height"));
        }

        if (req.getParameter("fontSize") != null) {
            fontSize = Integer.parseInt(req.getParameter("fontSize"));
        }
        String code = ValidateCodeCreator.randomCode(charNum);
        HttpSession session = req.getSession();
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute("captcha", code);
        BufferedImage codeImage = ValidateCodeCreator.create(code, width, height, fontSize);
        ImageIO.write(codeImage, "JPEG", resp.getOutputStream());
    }
}
