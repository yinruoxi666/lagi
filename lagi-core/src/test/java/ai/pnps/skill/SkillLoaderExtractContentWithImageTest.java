package ai.pnps.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SkillLoaderExtractContentWithImageTest {

    @Test
    public void testLoadExtractContentWithImageSkill() {
        Path skillsRoot = resolveRepoSkillsRoot();
        assertNotNull(skillsRoot);
        assertTrue(Files.isDirectory(skillsRoot));

        Map<String, SkillSchema> all = SkillLoader.loadSkillsStatic(skillsRoot.toString());
        assertNotNull(all);
        assertFalse(all.isEmpty());

        String expectedKey = "extract_content_with_image@1.0.0";
        assertTrue(all.containsKey(expectedKey), "should contain key: " + expectedKey);

        SkillSchema schema = all.get(expectedKey);
        assertNotNull(schema);
        assertEquals("extract_content_with_image", schema.getSkillId());
        assertEquals("1.0.0", schema.getVersion());
        assertNotNull(schema.getName());
        assertNotNull(schema.getDescription());
        assertFalse(schema.getScripts().isEmpty(), "should have scripts");

        SkillFile script = schema.getScripts().get(0);
        assertEquals(".py", script.getType().toLowerCase());
        assertNotNull(script.getPath());
        String p = script.getPath().toString().replace('\\', '/');
        assertTrue(p.endsWith("scripts/extract_content_with_image.py"));
    }

    private Path resolveRepoSkillsRoot() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null) {
            return null;
        }
        Path base = java.nio.file.Paths.get(userDir).toAbsolutePath().normalize();

        Path candidate1 = base.resolve("skills");
        if (Files.isDirectory(candidate1)) {
            return candidate1;
        }

        Path candidate2 = base.getParent() != null ? base.getParent().resolve("skills") : null;
        if (candidate2 != null && Files.isDirectory(candidate2)) {
            return candidate2;
        }

        Path candidate3 = java.nio.file.Paths.get("skills");
        if (Files.isDirectory(candidate3)) {
            return candidate3.toAbsolutePath().normalize();
        }

        return null;
    }


    @Test
    public void testLoadExtractContentWithImageSkillRun() {
        Path skillsRoot = resolveRepoSkillsRoot();
        assertNotNull(skillsRoot);
        assertTrue(Files.isDirectory(skillsRoot));

        String pdfEnv = System.getenv("EXTRACT_TEST_PDF");
        Path pdfPath = null;
        if (pdfEnv != null && !pdfEnv.isEmpty()) {
            pdfPath = Paths.get(pdfEnv).toAbsolutePath().normalize();
        }
        if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
            Path fallback = Paths.get("C:\\Users\\24175\\Desktop\\知识图谱.pdf").toAbsolutePath().normalize();
            if (Files.isRegularFile(fallback)) {
                pdfPath = fallback;
            }
        }
        Assumptions.assumeTrue(pdfPath != null && Files.isRegularFile(pdfPath),
                "请设置环境变量 EXTRACT_TEST_PDF 为存在的 PDF，或将样例 PDF 放在默认路径");
        assertNotNull(pdfPath);

        Map<String, SkillSchema> all = SkillLoader.loadSkillsStatic(skillsRoot.toString());
        SkillSchema skillSchema = all.get("extract_content_with_image@1.0.0");
        assertNotNull(skillSchema);
        SkillContainer skillContainer = new SkillContainer(skillsRoot, 1000);
        ExecutionInput executionInput = new ExecutionInput();
        List<Object> args = new ArrayList<>();
        args.add(pdfPath.toString());
        executionInput.setArgs(args);
        String td = System.getenv("TOKENIZER_DIR");
        if (td != null && !td.isEmpty()) {
            executionInput.getEnvVars().put("TOKENIZER_DIR", td);
        } else {
            String md = System.getenv("MODEL_DIR");
            if (md != null && !md.isEmpty()) {
                executionInput.getEnvVars().put("MODEL_DIR", md);
            }
        }
        ExecutionOutput executionOutput = skillContainer.executePythonScript(
                skillSchema.getScripts().get(0).getPath(), skillSchema.getSkillId(), executionInput);
        System.out.println("extract_content_with_image stdout:");
        System.out.println(executionOutput.getStdout());
        System.err.println("extract_content_with_image stderr:");
        System.err.println(executionOutput.getStderr());
        assertEquals(0, executionOutput.getExitCode(), "stderr=" + executionOutput.getStderr());
        JsonNode out = JsonUtils.parseJsonObject(executionOutput.getStdout());
        assertNotNull(out, "stdout 应为 JSON: " + executionOutput.getStdout());
        assertEquals("success", out.path("status").asText(), out.toString());
        assertTrue(out.has("data") && out.get("data").isArray(), out.toString());
    }
}

