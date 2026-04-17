package ai.vector;

import ai.common.pojo.FileChunkResponse;
import ai.common.pojo.Response;
import ai.ocr.OcrService;
import ai.pnps.skills.SkillScriptExecutor;
import ai.pnps.skills.pojo.ScriptExecutionResult;
import ai.pnps.skills.pojo.SkillExecutionPlan;
import ai.utils.*;
import ai.utils.pdf.PdfUtil;
import ai.utils.word.WordUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class FileService {
    private static final String TO_MARKDOWN_URL = "http://lagi.saasai.top:8090/file/to_markdown";
    private static final String EXTRACT_CONTENT_SKILL_NAME = "extract_content_with_image";
    private static final String EXTRACT_CONTENT_SKILL_RESOURCE_ROOT = "skills/extract_content_with_image";
    private static final String EXTRACT_CONTENT_SKILL_SCRIPT = "scripts/extract_content_with_image.py";
    private static final long LOCAL_EXTRACT_CONTENT_TIMEOUT_SECONDS = 600L;
    private static final SkillScriptExecutor SKILL_SCRIPT_EXECUTOR = new SkillScriptExecutor();
    private static final Object EXTRACT_CONTENT_SKILL_LOCK = new Object();
    private static volatile Path cachedExtractContentSkillDir;

    private final Gson gson = new Gson();

    public static void main(String[] args) {
        // 请将下面这行替换成你本机 PDF 的真实绝对路径
        String pdfPath = "/Users/ruoxiyin/Documents/缔智元/02.项目/机场项目/docs/test/"
                + "KN.pdf";
        // 展示当前文件夹路径

        // 1. 使用 FileInputStream 读取本地 PDF
        try (InputStream inputStream = new FileInputStream(new File(pdfPath))) {

            // 2. 调用 PdfUtil.webPdfParse(InputStream) 解析文本
            String contents = PdfUtil.webPdfParse(inputStream);
            // 3. 打印解析结果
            System.out.println("====== 解析结果开始 ======");
            System.out.println(contents);
            System.out.println("====== 解析结果结束 ======");

        } catch (Exception e) {
            System.err.println("解析本地 PDF 失败！请检查文件路径是否正确。");
            e.printStackTrace();
        }
    }

    public FileChunkResponse extractContent(File file) {
        if (file == null || !file.isFile()) {
            return FileChunkResponse.builder().status("failed").msg("file not found").data(Collections.emptyList()).build();
        }
        try {
            Path skillDir = resolveExtractContentSkillDir();
            Map<String, String> extraEnv = buildExtractContentSkillEnv(file);
            SkillExecutionPlan.Script script = new SkillExecutionPlan.Script(
                    EXTRACT_CONTENT_SKILL_SCRIPT,
                    Collections.singletonList(file.getAbsolutePath()),
                    "."
            );
            ScriptExecutionResult execution = SKILL_SCRIPT_EXECUTOR.execute(
                    skillDir, script, LOCAL_EXTRACT_CONTENT_TIMEOUT_SECONDS, 0L, extraEnv);

            if (execution.getExitCode() != 0 || execution.isTimeout() || execution.isNoOutputTimeout()) {
                String msg = execution.isTimeout()
                        ? "extract_content_with_image skill timed out"
                        : buildSkillFailureMessage(execution);
                log.warn("Local extract_content_with_image skill failed for file {}: {}", file.getAbsolutePath(), msg);
                return FileChunkResponse.builder()
                        .status("failed")
                        .msg(msg)
                        .data(Collections.emptyList())
                        .build();
            }

            if (execution.getStderr() != null && !execution.getStderr().trim().isEmpty()) {
                log.warn("Local extract_content_with_image skill stderr for {}: {}", file.getAbsolutePath(), execution.getStderr());
            }

            FileChunkResponse response = parseExtractContentSkillResponse(execution.getStdout());
            if (response == null) {
                return FileChunkResponse.builder()
                        .status("failed")
                        .msg("invalid skill stdout")
                        .data(Collections.emptyList())
                        .build();
            }
            return response;
        } catch (Exception e) {
            log.error("execute local extract_content_with_image skill error", e);
            return FileChunkResponse.builder()
                    .status("failed")
                    .msg(e.getMessage())
                    .data(Collections.emptyList())
                    .build();
        }
    }

    public Response toMarkdown(File file) {
        String filePramName = "file";
        Map<String, String> formParmMap = new HashMap<>();
        List<File> fileList = new ArrayList<>();
        fileList.add(file);
        Map<String, String> headers = new HashMap<>();
        if (LagiGlobal.getLandingApikey() == null) {
            return null;
        }
        headers.put("Authorization", "Bearer " + LagiGlobal.getLandingApikey());
        String returnStr = HttpUtil.multipartUpload(TO_MARKDOWN_URL, filePramName, fileList, formParmMap, headers);
        return gson.fromJson(returnStr, Response.class);
    }

    public List<FileChunkResponse.Document> splitChunks(File file, int chunkSize) throws IOException {
        List<FileChunkResponse.Document> result = new ArrayList<>();
        String extString = file.getName().substring(file.getName().lastIndexOf("."));
        String fileType = extString.toLowerCase().toLowerCase();
        if (fileType.equals(".xls")||fileType.equals(".xlsx")){
//            return EasyExcelUtil.getChunkDocumentExcel(file,chunkSize);
        }else if (fileType.equals(".csv")){
//            return EasyExcelUtil.getChunkDocumentCsv(file);
        }else if (fileType.equals(".jpeg")||fileType.equals(".png")||
                  fileType.equals(".gif")||fileType.equals(".bmp")||
                  fileType.equals(".webp")||fileType.equals(".jpg")){
            return getChunkDocumentImage(file, chunkSize);
        }else if (fileType.equals(".pptx")||fileType.equals(".ppt")){
            return PptUtil.getChunkDocumentPpt(file, chunkSize);
        }
        String content = getFileContent(file);
        return splitContentChunks(chunkSize, content);
    }

    public static List<FileChunkResponse.Document> splitContentChunks(int chunkSize, String content) {
        return splitContentChunks(chunkSize, content, false);
    }

    public static List<FileChunkResponse.Document> splitContentChunks(int chunkSize, String content, boolean lineSeparator) {
        List<FileChunkResponse.Document> result = new ArrayList<>();
        StringSplitUtils.splitContentChunks(chunkSize, content, lineSeparator).forEach(text -> {
            FileChunkResponse.Document doc = new FileChunkResponse.Document();
            doc.setText(text);
            result.add(doc);
        });
        return result;
    }

    public static List<FileChunkResponse.Document> getChunkDocumentImage(File file,Integer chunkSize) {
        List<FileChunkResponse.Document> result = new ArrayList<>();
        List<String> langList = new ArrayList<>();
        langList.add("chn,eng,tai");
        OcrService ocrService = new OcrService();
        List<File> fileList = new ArrayList<>();
        fileList.add(file);
        File AbsoluteFile = new File("/upload/"+file.getName());
        System.out.println("AbsolutePath-----"+AbsoluteFile.getPath());
        String content = "";
        try {
            content = ocrService.image2Ocr(fileList, langList).get(0).toString();
        }catch (Exception e){
            System.out.println("ocr 未启用");
            content = "";
        }
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String text = content.substring(start, end).replaceAll("\\s+", " ");
            FileChunkResponse.Document doc = new FileChunkResponse.Document();
            doc.setText(text);
            List<String> images = new ArrayList<>();
            images.add(file.getAbsolutePath());
            FileChunkResponse.Image image = new FileChunkResponse.Image();
            image.setPath(AbsoluteFile.getPath());
            List<FileChunkResponse.Image> list = new ArrayList<>();
            list.add(image);
            doc.setImages(list);
            result.add(doc);
            start = end;
        }

        return result;
    }

    /**
     * Split content into chunks, ensuring table tags stay together
     * Normal splitting by chunkSize, but when encountering <table> tag,
     * ensure <table> and </table> are in the same chunk (chunk size can exceed chunkSize)
     * @param chunkSize the chunk size
     * @param content the content to split
     * @return list of text chunks
     */
    private static List<String> splitContentWithTableAwareness(int chunkSize, String content) {
        List<String> result = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return result;
        }
        
        int currentPos = 0;
        
        while (currentPos < content.length()) {
            // Calculate the end position for this chunk
            int chunkEnd = Math.min(currentPos + chunkSize, content.length());
            
            // Get the chunk text
            String chunk = content.substring(currentPos, chunkEnd);
            
            // Check if there's an unclosed <table> tag in this chunk
            int tableOpenCount = countOccurrences(chunk, "<table>");
            int tableCloseCount = countOccurrences(chunk, "</table>");
            
            // If there are more opening tags than closing tags, we need to extend the chunk
            if (tableOpenCount > tableCloseCount) {
                // Find the next </table> tag after chunkEnd
                int nextCloseTag = content.indexOf("</table>", chunkEnd);
                if (nextCloseTag != -1) {
                    // Extend chunk to include the closing tag
                    chunkEnd = nextCloseTag + "</table>".length();
                    chunk = content.substring(currentPos, chunkEnd);
                }
            }
            // If the chunk starts in the middle of a table (more closing than opening tags)
            else if (tableCloseCount > tableOpenCount) {
                // Find the previous <table> tag before currentPos
                int prevOpenTag = content.lastIndexOf("<table>", currentPos);
                if (prevOpenTag != -1 && prevOpenTag < currentPos) {
                    // This should not happen if we split correctly, but handle it anyway
                    // Move back to include the opening tag
                    currentPos = prevOpenTag;
                    chunkEnd = Math.min(currentPos + chunkSize, content.length());
                    chunk = content.substring(currentPos, chunkEnd);
                    
                    // Re-check for unclosed tables
                    tableOpenCount = countOccurrences(chunk, "<table>");
                    tableCloseCount = countOccurrences(chunk, "</table>");
                    if (tableOpenCount > tableCloseCount) {
                        int nextCloseTag = content.indexOf("</table>", chunkEnd);
                        if (nextCloseTag != -1) {
                            chunkEnd = nextCloseTag + "</table>".length();
                            chunk = content.substring(currentPos, chunkEnd);
                        }
                    }
                }
            }
            
            // Clean up whitespace and add to result
            String text = chunk.replaceAll("\\s+", " ");
            if (!text.trim().isEmpty()) {
                result.add(text);
            }
            
            currentPos = chunkEnd;
        }
        
        return result;
    }
    
    /**
     * Count occurrences of a substring in a string
     * @param str the string to search in
     * @param substr the substring to count
     * @return the number of occurrences
     */
    private static int countOccurrences(String str, String substr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }

    public static List<FileChunkResponse.Document> getChunkDocumentScannedPDF(File file,Integer chunkSize){
        OcrService ocrService = new OcrService();
        List<String> pdfContent = new ArrayList<>();
        try {
            pdfContent = ocrService.doc2ocr(file, Arrays.asList("chn", "eng"));
        }catch (Exception e){
            log.error("ocr error", e);
        }
        List<FileChunkResponse.Document> result = new ArrayList<>();
        List<File> fileList = pdftoImage(file);
        for (int i = 0; i < fileList.size(); i++) {
            FileChunkResponse.Image image = new FileChunkResponse.Image();
            String normalizedPath = fileList.get(i).getPath().replace("\\", "/");
            String imagePath = "";
            // 查找 "upload" 目录的起始位置
            int index = normalizedPath.indexOf("/upload");
            if (index != -1) {
                imagePath = normalizedPath.substring(index);
                System.out.println("提取的路径部分: " + imagePath);
            }
            image.setPath(imagePath);
            List<FileChunkResponse.Image> list = new ArrayList<>();
            list.add(image);

            String content = pdfContent.get(i);
            if (content != null && !content.isEmpty()){
                List<String> textChunks = splitContentWithTableAwareness(chunkSize, content);
                for (String text : textChunks) {
                    FileChunkResponse.Document doc = new FileChunkResponse.Document();
                    doc.setText(text);
                    doc.setImages(list);
                    result.add(doc);
                }
            }
        }
        return result;
    }

    private static List<File> pdftoImage(File file) {
        try {
            PDDocument document = PDDocument.load(file);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            String normalizedPath = file.getAbsolutePath().replace("\\", "/");
            String outputDir = "";
            int index = normalizedPath.indexOf("/upload");
            if (index != -1) {
                outputDir = normalizedPath.substring(0, index + "/upload".length());
            }
            System.out.println("创建保存图片的目录..."+outputDir);
            List<String> imageFiles = new ArrayList<>();
            List<File> fileList = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pageIndex, 100);  // 300 DPI的清晰度
                File outputFile = new File(outputDir, file.getName()+"_page_" + (pageIndex + 1) + ".png");
                ImageIO.write(bufferedImage, "PNG", outputFile);
                imageFiles.add("/upload/"+file.getName()+"_page_" + (pageIndex + 1) + ".png");
                fileList.add(outputFile);
            }
            document.close();
            return fileList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getFileContent(File file) throws IOException {
        String extString = file.getName().substring(file.getName().lastIndexOf("."));
        InputStream in = Files.newInputStream(file.toPath());
        String content = null;
        switch (extString.toLowerCase()) {
            case ".doc":
            case ".docx":
                 content = WordUtils.getContentsByWord(in, extString).replaceAll("\\n+", "\n");;
                 content = content!=null?removeDirectory(content):content;
                break;
            case ".txt":
                content = getString(file.getPath());
                break;
            case ".pdf":
//                dumpStream(in,"web-flex");
                content = PdfUtil.webPdfParse(in);
                if (content==null||content.trim().isEmpty()){
                    System.out.println("扫描件");
                    content = null;
                    break;
                }
                Response response = toMarkdown(file);
                if (response != null && response.getStatus().equals("success")){
                    content = response.getData();
                    content = content!=null?removeDirectory(content):content;
                }else {
                    content = PdfUtil.webPdfParse(in)
                            .replaceAll("(\r?\n){2,}", "\n")
                            .replaceAll("(?<=\r?\n)\\s*", "")
                            .replaceAll("(?<![.!?;:。！？；：\\s\\d])\r?\n", "");
                    if (content != null) {
                        content = removeDirectory(content);
                    } else {
                        System.out.println("扫描件");
                    }
                }
                break;
            case ".xls":
            case ".xlsx":
                content = EasyExcelUtil.getExcelContent(file);
                break;
            case ".csv":
                content = EasyExcelUtil.getCsvContent(file);
                break;
            case ".jpg":
            case ".jpeg":
            case ".png":
            case ".gif":
            case ".bmp":
            case ".webp":
                OcrService ocrService = new OcrService();
                List<String> langList = new ArrayList<>();
                langList.add("chn,eng,tai");
                content = "图片名为："+file.getName();
                List<File> fileList = new ArrayList<>();
                fileList.add(file);
                content += "内容为："+ocrService.image2Ocr(fileList, langList).toString();
                break;
            case ".pptx":
            case ".ppt":
                content = PptUtil.getPptContent(file);
                break;
            case ".md":
            case ".html":
                content = getString(file.getPath());
            default:
                System.out.println("无法识别该文件");
                break;
        }
        in.close();
        return content;
    }


    public static String removeDirectory(String content) {

        Pattern directoryTitlePattern = Pattern.compile("目\\s*录|目\\s*次", Pattern.CASE_INSENSITIVE);
        Matcher directoryTitleMatcher = directoryTitlePattern.matcher(content);
        if (!directoryTitleMatcher.find()) {
            return content;
        }
        Integer directoryEndIndex = directoryTitleMatcher.end();
        Integer jei = directoryEndIndex;
        Integer directoryStartIndex = directoryEndIndex;

        while (content.length()>= jei) {
            char ch = content.charAt(directoryStartIndex);
            while ((content.length()> directoryStartIndex+1)&&ch == '\n'){
                jei++;
                directoryStartIndex++;
                ch = content.charAt(directoryStartIndex);
            }
            int nextLineIndex = content.indexOf('\n', directoryStartIndex);
            if (nextLineIndex == -1) {
                break;
            }
            directoryStartIndex = nextLineIndex;
            String nextLine = content.substring(jei, nextLineIndex).trim();
            if (!nextLine.isEmpty()&&!nextLine.matches(".*([IVXLCDM]{1,4}|\\d+)$")){
                break;
            }
            jei = nextLineIndex;
        }
        String cleanedContent = content.substring(0, directoryEndIndex) + content.substring(jei);
        return cleanedContent;
    }

    public static String getString(String filePath) {
        StringBuilder content = new StringBuilder();
        try {
            content.append(Files.lines(Paths.get(filePath))
                    .collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();
    }

    static FileChunkResponse parseExtractContentSkillResponse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement parsed = new JsonParser().parse(raw.trim());
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            List<FileChunkResponse.Document> docs = new ArrayList<>();
            JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : null;
            if (data != null) {
                for (JsonElement item : data) {
                    if (item != null && item.isJsonObject()) {
                        docs.add(parseSkillDocument(item.getAsJsonObject()));
                    }
                }
            }
            return FileChunkResponse.builder()
                    .status(getAsString(root, "status"))
                    .msg(getAsString(root, "msg"))
                    .data(docs)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse extract_content_with_image skill stdout: " + raw, e);
            return null;
        }
    }

    private static FileChunkResponse.Document parseSkillDocument(JsonObject obj) {
        FileChunkResponse.Document doc = new FileChunkResponse.Document();
        doc.setText(getAsString(obj, "text"));
        doc.setSource(getAsString(obj, "source"));
        if (obj.has("order") && !obj.get("order").isJsonNull()) {
            try {
                doc.setOrder(obj.get("order").getAsInt());
            } catch (Exception ignored) {
            }
        }
        doc.setReferenceDocumentId(getAsString(obj, "referenceDocumentId"));

        List<FileChunkResponse.Image> images = new ArrayList<>();
        images.addAll(parseImagesArray(obj.get("images")));
        if (images.isEmpty()) {
            images.addAll(parseImagesArray(obj.get("image")));
        }
        doc.setImages(images);
        return doc;
    }

    private static List<FileChunkResponse.Image> parseImagesArray(JsonElement element) {
        List<FileChunkResponse.Image> images = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return images;
        }

        JsonElement actual = element;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            if (raw == null || raw.trim().isEmpty()) {
                return images;
            }
            try {
                actual = new JsonParser().parse(raw);
            } catch (Exception ignored) {
                FileChunkResponse.Image image = new FileChunkResponse.Image();
                image.setPath(raw);
                images.add(image);
                return images;
            }
        }

        if (!actual.isJsonArray()) {
            return images;
        }

        for (JsonElement item : actual.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            String path = getAsString(item.getAsJsonObject(), "path");
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            FileChunkResponse.Image image = new FileChunkResponse.Image();
            image.setPath(path);
            images.add(image);
        }
        return images;
    }

    private static String getAsString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildSkillFailureMessage(ScriptExecutionResult execution) {
        String stderr = execution.getStderr() == null ? "" : execution.getStderr().trim();
        String stdout = execution.getStdout() == null ? "" : execution.getStdout().trim();
        if (!stderr.isEmpty()) {
            return stderr;
        }
        if (!stdout.isEmpty()) {
            return stdout;
        }
        return "extract_content_with_image skill exited with code " + execution.getExitCode();
    }

    private static Map<String, String> buildExtractContentSkillEnv(File file) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        Path outputDir = resolveExtractContentOutputDir(file);
        env.put("SKILL_OUTPUT_DIR", outputDir.toString());
        putIfPresent(env, "TOKENIZER_DIR", System.getenv("TOKENIZER_DIR"));
        putIfPresent(env, "MODEL_DIR", System.getenv("MODEL_DIR"));
        putIfPresent(env, "SOFFICE_PATH", System.getenv("SOFFICE_PATH"));
        putIfPresent(env, "CHUNK_SIZE", System.getenv("CHUNK_SIZE"));
        return env;
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            env.put(key, value);
        }
    }

    private static Path resolveExtractContentOutputDir(File file) throws IOException {
        List<Path> candidates = new ArrayList<>();
        if (file != null && file.getParentFile() != null) {
            candidates.add(file.getParentFile().toPath());
        }
        candidates.add(Paths.get(System.getProperty("java.io.tmpdir")));

        for (Path baseDir : candidates) {
            Path outputDir = baseDir.resolve(".lagi_skill_outputs").toAbsolutePath().normalize();
            try {
                Files.createDirectories(outputDir);
                return outputDir;
            } catch (IOException e) {
                log.warn("Cannot create local skill output dir: {}", outputDir, e);
            }
        }
        throw new IOException("Unable to create local extract_content skill output dir");
    }

    private static Path resolveExtractContentSkillDir() throws IOException {
        Path cached = cachedExtractContentSkillDir;
        if (isValidExtractContentSkillDir(cached)) {
            return cached;
        }
        synchronized (EXTRACT_CONTENT_SKILL_LOCK) {
            if (isValidExtractContentSkillDir(cachedExtractContentSkillDir)) {
                return cachedExtractContentSkillDir;
            }
            Path resolved = locateExtractContentSkillDir();
            cachedExtractContentSkillDir = resolved;
            return resolved;
        }
    }

    private static Path locateExtractContentSkillDir() throws IOException {
        String userDir = System.getProperty("user.dir");
        List<Path> candidates = new ArrayList<>();
        if (userDir != null && !userDir.trim().isEmpty()) {
            Path base = Paths.get(userDir).toAbsolutePath().normalize();
            candidates.add(base.resolve("lagi-web").resolve("src").resolve("main").resolve("resources")
                    .resolve("skills").resolve(EXTRACT_CONTENT_SKILL_NAME));
            candidates.add(base.resolve("skills").resolve(EXTRACT_CONTENT_SKILL_NAME));
            Path parent = base.getParent();
            if (parent != null) {
                candidates.add(parent.resolve("lagi-web").resolve("src").resolve("main").resolve("resources")
                        .resolve("skills").resolve(EXTRACT_CONTENT_SKILL_NAME));
                candidates.add(parent.resolve("skills").resolve(EXTRACT_CONTENT_SKILL_NAME));
            }
        }

        URL skillMdUrl = FileService.class.getClassLoader()
                .getResource(EXTRACT_CONTENT_SKILL_RESOURCE_ROOT + "/SKILL.md");
        if (skillMdUrl != null && "file".equalsIgnoreCase(skillMdUrl.getProtocol())) {
            try {
                candidates.add(Paths.get(skillMdUrl.toURI()).getParent());
            } catch (Exception ignored) {
            }
        }

        for (Path candidate : candidates) {
            if (isValidExtractContentSkillDir(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        Path extracted = materializeExtractContentSkillFromResources();
        if (isValidExtractContentSkillDir(extracted)) {
            return extracted;
        }
        throw new FileNotFoundException("extract_content_with_image skill not found");
    }

    private static boolean isValidExtractContentSkillDir(Path dir) {
        if (dir == null) {
            return false;
        }
        return Files.isRegularFile(dir.resolve("SKILL.md"))
                && Files.isRegularFile(dir.resolve(EXTRACT_CONTENT_SKILL_SCRIPT));
    }

    private static Path materializeExtractContentSkillFromResources() throws IOException {
        Path skillDir = Paths.get(System.getProperty("java.io.tmpdir"), "lagi-skills", EXTRACT_CONTENT_SKILL_NAME)
                .toAbsolutePath().normalize();
        copyClasspathResource(
                EXTRACT_CONTENT_SKILL_RESOURCE_ROOT + "/SKILL.md",
                skillDir.resolve("SKILL.md")
        );
        copyClasspathResource(
                EXTRACT_CONTENT_SKILL_RESOURCE_ROOT + "/" + EXTRACT_CONTENT_SKILL_SCRIPT,
                skillDir.resolve(EXTRACT_CONTENT_SKILL_SCRIPT)
        );
        return skillDir;
    }

    private static void copyClasspathResource(String resourcePath, Path target) throws IOException {
        URL url = FileService.class.getClassLoader().getResource(resourcePath);
        if (url == null) {
            throw new FileNotFoundException("resource not found: " + resourcePath);
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (InputStream in = url.openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] dumpStream(InputStream in, String tag) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];

        int n;
        while ((n = in.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        byte[] data = baos.toByteArray();

        System.out.println("[" + tag + "] bytes length : " + data.length);
        if (data.length >= 12) {
            String header = new String(Arrays.copyOfRange(data, 0, 12), StandardCharsets.ISO_8859_1);
            System.out.println("[" + tag + "] first 12 bytes: " + header.replaceAll("\\p{Cntrl}", "."));
        } else {
            System.out.println("[" + tag + "] stream too short to show header.");
        }
        System.out.println("------------------------------------------------");

        return data;
    }
}
