package ai.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;

import ai.common.pojo.QuestionAnswerRequest;
import ai.migrate.service.ApiService;
import ai.openai.pojo.ChatMessage;
import ai.utils.AiGlobal;
import ai.utils.MigrateGlobal;

public class ImageServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private ApiService apiService = new ApiService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("generateVideo") || method.equals("image2video")) {
            this.generateVideo(req, resp);
        } else if (method.equals("imageToText") || method.equals("image2text")) {
            this.imageToText(req, resp);
        } else if (method.equals("generateImage")) {
            this.generateImage(req, resp);
        } else if (method.equals("enhanceImage") || method.equals("image2enhance")) {
            this.enhanceImage(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    private void imageToText(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String tempPath = this.getServletContext().getRealPath(AiGlobal.DIR_TEMP);
        File tmpFile = new File(tempPath);
        if (!tmpFile.exists()) {
            tmpFile.mkdir();
        }
        String result = "{\"status\":\"failed\"}";
        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setHeaderEncoding("UTF-8");
            if (!ServletFileUpload.isMultipartContent(req)) {
                return;
            }
            upload.setFileSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
            upload.setSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
            List<FileItem> list = upload.parseRequest(req);

            for (FileItem item : list) {
                if (item.isFormField()) {
                } else {
                    String filename = item.getName();
                    if (filename == null || filename.trim().equals("")) {
                        continue;
                    }
                    String extName = filename.substring(filename.lastIndexOf("."));
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                    String newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6) + extName;
                    InputStream in = item.getInputStream();
                    File file = new File(tempPath + "/" + newName);
                    FileUtils.copyInputStreamToFile(in, file);
                    result = apiService.imageToText(file.getAbsolutePath(), req);
                }
            }
        } catch (FileUploadException e) {
            e.printStackTrace();
        }
        responsePrint(resp, result);
    }
    
    private void generateVideo(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String tempPath = this.getServletContext().getRealPath(AiGlobal.DIR_TEMP);
        File tmpFile = new File(tempPath);
        if (!tmpFile.exists()) {
            tmpFile.mkdir();
        }
        String result = "{\"status\":\"failed\"}";
        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setHeaderEncoding("UTF-8");
            if (!ServletFileUpload.isMultipartContent(req)) {
                return;
            }
            upload.setFileSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
            upload.setSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
            List<FileItem> list = upload.parseRequest(req);

            for (FileItem item : list) {
                if (item.isFormField()) {
                } else {
                    String filename = item.getName();
                    if (filename == null || filename.trim().equals("")) {
                        continue;
                    }
                    String extName = filename.substring(filename.lastIndexOf("."));
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                    String newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6) + extName;
                    InputStream in = item.getInputStream();
                    File file = new File(tempPath + "/" + newName);
                    FileUtils.copyInputStreamToFile(in, file);
                    result = apiService.generateVideo(file.getAbsolutePath(), req);
                }
            }
        } catch (FileUploadException e) {
            e.printStackTrace();
        }
        responsePrint(resp, result);
    }
    
    private void generateImage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        QuestionAnswerRequest qaRequest = gson.fromJson(jsonString, QuestionAnswerRequest.class);
        List<ChatMessage> messages = qaRequest.getMessages();
        String content = messages.get(messages.size() - 1).getContent().trim();
        String result =  "{\"status\":\"failed\"}";
        result = apiService.generateImage(content, req);
        responsePrint(resp, result);
    }
    
    private void enhanceImage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String tempPath = this.getServletContext().getRealPath(AiGlobal.DIR_TEMP);
        File tmpFile = new File(tempPath);
        if (!tmpFile.exists()) {
            tmpFile.mkdir();
        }
        String result = "{\"status\":\"failed\"}";
        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setHeaderEncoding("UTF-8");
            if (!ServletFileUpload.isMultipartContent(req)) {
                return;
            }
            upload.setFileSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
            upload.setSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
            List<FileItem> list = upload.parseRequest(req);

            for (FileItem item : list) {
                if (item.isFormField()) {
                } else {
                    String filename = item.getName();
                    if (filename == null || filename.trim().equals("")) {
                        continue;
                    }
                    String extName = filename.substring(filename.lastIndexOf("."));
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                    String newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6) + extName;
                    InputStream in = item.getInputStream();
                    File file = new File(tempPath + "/" + newName);
                    FileUtils.copyInputStreamToFile(in, file);
                    result = apiService.enhanceImage(file.getAbsolutePath(), req);
                }
            }
        } catch (FileUploadException e) {
            e.printStackTrace();
        }
        responsePrint(resp, result);
    }
}
