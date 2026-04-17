package ai.pnps.skills.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helpers for OpenAI-style filesystem tools ({@code read} / {@code write} / {@code edit} / {@code exec} argument maps).
 */
public final class FilesystemToolUtil {

    public static final int DEFAULT_MAX_READ_LINES = 2000;
    public static final int DEFAULT_MAX_READ_BYTES = 50 * 1024;

    private FilesystemToolUtil() {
    }

    public static Path resolveReadPath(Map<String, String> args) {
        if (args == null) {
            return null;
        }
        String fp = args.get("file_path");
        String p = args.get("path");
        Path filePath = fp != null && !fp.trim().isEmpty() ? Paths.get(fp.trim()) : null;
        Path path = p != null && !p.trim().isEmpty() ? Paths.get(p.trim()) : null;
        if (filePath != null && Files.exists(filePath)) {
            return filePath;
        }
        if (path != null) {
            return path;
        }
        return filePath;
    }

    public static Path resolveExecWorkDir(Map<String, String> args) {
        if (args == null) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
        String raw = SkillUtil.firstNonBlank(args.get("workdir"), args.get("working_dir"), ".");
        return Paths.get(raw).toAbsolutePath().normalize();
    }

    public static long resolveExecTimeoutSeconds(Map<String, String> args, long defaultSeconds) {
        if (args == null) {
            return Math.max(1L, defaultSeconds);
        }
        String raw = SkillUtil.trimToNull(args.get("timeout"));
        if (raw == null) {
            return Math.max(1L, defaultSeconds);
        }
        try {
            long sec = Math.round(Double.parseDouble(raw));
            if (sec < 1L) {
                return Math.max(1L, defaultSeconds);
            }
            return Math.min(sec, 24L * 3600L);
        } catch (NumberFormatException e) {
            return Math.max(1L, defaultSeconds);
        }
    }

    public static Map<String, String> parseEnvObject(Map<String, String> args) {
        if (args == null) {
            return Collections.emptyMap();
        }
        String raw = args.get("env");
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        JsonNode node = SkillsJsons.parseObject(raw);
        if (node == null) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<String, String>();
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v != null && !v.isNull()) {
                out.put(e.getKey(), v.isTextual() ? v.asText() : v.toString());
            }
        });
        return out;
    }

    public static String execUnsupportedFlagsNote(Map<String, String> args) {
        if (args == null) {
            return "";
        }
        List<String> n = new ArrayList<String>();
        if (truthy(args.get("background"))) {
            n.add("background");
        }
        if (SkillUtil.trimToNull(args.get("yieldMs")) != null) {
            n.add("yieldMs");
        }
        if (truthy(args.get("pty"))) {
            n.add("pty");
        }
        if (truthy(args.get("elevated"))) {
            n.add("elevated");
        }
        if (SkillUtil.trimToNull(args.get("host")) != null) {
            n.add("host");
        }
        if (SkillUtil.trimToNull(args.get("security")) != null) {
            n.add("security");
        }
        if (SkillUtil.trimToNull(args.get("ask")) != null) {
            n.add("ask");
        }
        if (SkillUtil.trimToNull(args.get("node")) != null) {
            n.add("node");
        }
        if (n.isEmpty()) {
            return "";
        }
        return "\n[note: not implemented: " + String.join(", ", n) + "]";
    }

    public static String writeUtf8File(Path target, String content) {
        try {
            Path abs = target.toAbsolutePath().normalize();
            Path parent = abs.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(abs, content.getBytes(StandardCharsets.UTF_8));
            return "ok: wrote " + abs;
        } catch (Exception e) {
            return "write failed: " + e.getMessage();
        }
    }

    public static String editUtf8File(Path target, String oldText, String newText) {
        if (oldText == null) {
            return "edit failed: missing oldText or old_string";
        }
        if (oldText.isEmpty()) {
            return "edit failed: oldText must be non-empty";
        }
        String replacement = newText == null ? "" : newText;
        try {
            Path abs = target.toAbsolutePath().normalize();
            if (!Files.isRegularFile(abs)) {
                return "edit failed: not a regular file: " + abs;
            }
            String raw = new String(Files.readAllBytes(abs), StandardCharsets.UTF_8);
            int first = raw.indexOf(oldText);
            if (first < 0) {
                return "edit failed: oldText not found (must match exactly, including whitespace)";
            }
            int second = raw.indexOf(oldText, first + oldText.length());
            if (second >= 0) {
                return "edit failed: oldText matches multiple locations; include more context so the match is unique";
            }
            String updated = raw.substring(0, first) + replacement + raw.substring(first + oldText.length());
            Files.write(abs, updated.getBytes(StandardCharsets.UTF_8));
            return "ok: edited " + abs;
        } catch (Exception e) {
            return "edit failed: " + e.getMessage();
        }
    }

    public static boolean isImageFilename(String lowerName) {
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".webp");
    }

    public static String imageMimeType(String lowerName) {
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerName.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    public static String readImageAttachmentJson(Path path, String lowerName, byte[] bytes) {
        String b64 = Base64.getEncoder().encodeToString(bytes);
        String mime = imageMimeType(lowerName);
        return "{\"kind\":\"image_attachment\",\"path\":"
                + jsonString(path.toString())
                + ",\"mimeType\":"
                + jsonString(mime)
                + ",\"data_base64\":"
                + jsonString(b64)
                + "}";
    }

    public static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static boolean isLikelyBinary(byte[] bytes) {
        int n = Math.min(bytes.length, 8000);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    public static String decodeUtf8Lenient(byte[] bytes) throws CharacterCodingException {
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return dec.decode(ByteBuffer.wrap(bytes)).toString();
    }

    /**
     * OpenClaw read: {@code offset} is 1-based line index; {@code limit} is max lines (optional).
     */
    public static String applyLineWindow(String content, Integer offset1Based, Integer maxLines) {
        if (content == null) {
            return "";
        }
        if (offset1Based == null && maxLines == null) {
            return content;
        }
        String[] lines = content.split("\\R", -1);
        int start = 0;
        if (offset1Based != null && offset1Based > 1) {
            start = Math.min(lines.length, offset1Based - 1);
        }
        int end = lines.length;
        if (maxLines != null) {
            end = Math.min(lines.length, start + Math.max(0, maxLines));
        }
        return String.join("\n", Arrays.copyOfRange(lines, start, end));
    }

    /**
     * Truncate in line order until either {@code maxLines} lines or {@code maxBytes} UTF-8 bytes is reached.
     */
    public static String capLinesOrBytesFirst(String text, int maxLines, int maxBytes) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        int linesOut = 0;
        int utf8Len = 0;
        boolean truncated = false;
        String truncationMsg = null;
        for (int i = 0; i < lines.length; i++) {
            if (linesOut >= maxLines) {
                truncated = true;
                truncationMsg = "max " + maxLines + " lines";
                break;
            }
            byte[] lineBytes = lines[i].getBytes(StandardCharsets.UTF_8);
            int sep = linesOut > 0 ? 1 : 0;
            if (utf8Len + sep + lineBytes.length > maxBytes) {
                int room = maxBytes - utf8Len - sep;
                if (room > 0) {
                    if (linesOut > 0) {
                        sb.append('\n');
                        utf8Len += 1;
                    }
                    sb.append(truncateUtf8Bytes(lines[i], room));
                    utf8Len = sb.toString().getBytes(StandardCharsets.UTF_8).length;
                }
                truncated = true;
                truncationMsg = maxBytes + " bytes UTF-8";
                break;
            }
            if (linesOut > 0) {
                sb.append('\n');
                utf8Len += 1;
            }
            sb.append(lines[i]);
            utf8Len += lineBytes.length;
            linesOut++;
        }
        if (truncated) {
            sb.append("\n...[truncated: ").append(truncationMsg).append("; use offset/limit]");
        }
        return sb.toString();
    }

    public static String truncateUtf8Bytes(String s, int maxBytes) {
        byte[] full = s.getBytes(StandardCharsets.UTF_8);
        if (full.length <= maxBytes) {
            return s;
        }
        int end = maxBytes;
        while (end > 0 && (full[end - 1] & 0xC0) == 0x80) {
            end--;
        }
        return new String(full, 0, end, StandardCharsets.UTF_8);
    }

    public static Integer parseOptionalPositiveInt(Map<String, String> args, String key) {
        if (args == null || key == null) {
            return null;
        }
        String raw = SkillUtil.trimToNull(args.get(key));
        if (raw == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(raw);
            int v = (int) Math.round(d);
            return v >= 1 ? Integer.valueOf(v) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseOptionalNonNegativeInt(Map<String, String> args, String key) {
        if (args == null || key == null) {
            return null;
        }
        String raw = SkillUtil.trimToNull(args.get(key));
        if (raw == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(raw);
            int v = (int) Math.round(d);
            return v >= 0 ? Integer.valueOf(v) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(String v) {
        if (v == null) {
            return false;
        }
        String t = v.trim();
        return "true".equalsIgnoreCase(t) || "1".equals(t);
    }
}
