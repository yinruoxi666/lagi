package ai.pnps.skills.filesystem.impl;

import ai.pnps.skills.SkillScriptExecutor;
import ai.pnps.skills.filesystem.Filesystem;
import ai.pnps.skills.util.FilesystemToolUtil;
import ai.pnps.skills.pojo.ScriptExecutionResult;
import ai.pnps.skills.pojo.SkillExecutionPlan;
import ai.pnps.skills.util.SkillUtil;
import ai.pnps.skills.util.SkillsAgentToolUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Local filesystem backing for OpenAI-style tools: {@code read}, {@code write}, {@code edit}, {@code exec}
 * (same argument shapes as {@link ai.pnps.skills.SkillsAgent} tool loop).
 */
public class LocalFileSystemImpl implements Filesystem {

    private static final long MAX_READ_FILE_BYTES = 64L * 1024 * 1024;

    private final SkillScriptExecutor executor = new SkillScriptExecutor();

    @Override
    public String read(Map<String, String> arguments) {
        Path path = FilesystemToolUtil.resolveReadPath(arguments);
        if (path == null) {
            return "read failed: missing path or file_path";
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized)) {
                return "read failed: not a regular file: " + normalized;
            }
            long sz = Files.size(normalized);
            if (sz > MAX_READ_FILE_BYTES) {
                return "read failed: file too large (" + sz + " bytes); use offset/limit on a smaller export or split the file";
            }
            byte[] bytes = Files.readAllBytes(normalized);
            String lower = normalized.getFileName().toString().toLowerCase(Locale.ROOT);
            if (FilesystemToolUtil.isImageFilename(lower)) {
                return FilesystemToolUtil.readImageAttachmentJson(normalized, lower, bytes);
            }
            if (FilesystemToolUtil.isLikelyBinary(bytes)) {
                return "read failed: file appears binary (null bytes); not an image type handled as attachment";
            }
            String text = FilesystemToolUtil.decodeUtf8Lenient(bytes);
            text = FilesystemToolUtil.applyLineWindow(text,
                    FilesystemToolUtil.parseOptionalPositiveInt(arguments, "offset"),
                    FilesystemToolUtil.parseOptionalNonNegativeInt(arguments, "limit"));
            text = FilesystemToolUtil.capLinesOrBytesFirst(text,
                    FilesystemToolUtil.DEFAULT_MAX_READ_LINES,
                    FilesystemToolUtil.DEFAULT_MAX_READ_BYTES);
            return text;
        } catch (Exception e) {
            return "read failed: " + e.getMessage();
        }
    }

    @Override
    public String write(Map<String, String> arguments) {
        Path target = SkillsAgentToolUtil.resolveFilePathFromToolArgs(arguments);
        String content = SkillsAgentToolUtil.toolArgContent(arguments);
        if (target == null) {
            return "write failed: missing path or file_path";
        }
        if (content == null) {
            return "write failed: missing content";
        }
        return FilesystemToolUtil.writeUtf8File(target, content);
    }

    @Override
    public String edit(Map<String, String> arguments) {
        Path target = SkillsAgentToolUtil.resolveFilePathFromToolArgs(arguments);
        String oldText = SkillsAgentToolUtil.toolArgOldText(arguments);
        String newText = SkillsAgentToolUtil.toolArgNewText(arguments);
        if (target == null) {
            return "edit failed: missing path or file_path";
        }
        return FilesystemToolUtil.editUtf8File(target, oldText, newText);
    }

    @Override
    public String exec(Map<String, String> arguments, long timeoutSeconds) {
        SkillExecutionPlan.Script script = SkillUtil.scriptFromProviderExecArgs(arguments);
        Map<String, String> extraEnv = FilesystemToolUtil.parseEnvObject(arguments);
        Path workDir = FilesystemToolUtil.resolveExecWorkDir(arguments);
        long timeout = FilesystemToolUtil.resolveExecTimeoutSeconds(arguments, timeoutSeconds);
        if (SkillUtil.trimToNull(arguments == null ? null : arguments.get("command")) == null) {
            String notes = FilesystemToolUtil.execUnsupportedFlagsNote(arguments);
            return "exec failed: missing command" + notes;
        }
        ScriptExecutionResult r = executor.execute(workDir, script, timeout, 0L,
                extraEnv.isEmpty() ? null : extraEnv);
        return SkillUtil.formatExecToolResult(r);
    }
}
