package ai.vector;

import ai.common.pojo.FileChunkResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FileServiceTest {

    @Test
    public void testParseExtractContentSkillResponseMapsLegacyImageField() {
        String raw = "{"
                + "\"status\":\"success\","
                + "\"data\":[{"
                + "\"text\":\"chunk-1\","
                + "\"image\":\"[{\\\"path\\\":\\\"E:/tmp/skill/image-1.png\\\",\\\"tag\\\":\\\"tag\\\",\\\"caption\\\":\\\"caption\\\"}]\""
                + "}]"
                + "}";

        FileChunkResponse response = FileService.parseExtractContentSkillResponse(raw);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        FileChunkResponse.Document document = response.getData().get(0);
        assertEquals("chunk-1", document.getText());
        assertNotNull(document.getImages());
        assertEquals(1, document.getImages().size());
        assertEquals("E:/tmp/skill/image-1.png", document.getImages().get(0).getPath());
    }

    @Test
    public void testParseExtractContentSkillResponseMapsImagesArrayField() {
        String raw = "{"
                + "\"status\":\"success\","
                + "\"data\":[{"
                + "\"text\":\"chunk-2\","
                + "\"images\":[{\"path\":\"E:/tmp/skill/image-2.png\"}]"
                + "}]"
                + "}";

        FileChunkResponse response = FileService.parseExtractContentSkillResponse(raw);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        FileChunkResponse.Document document = response.getData().get(0);
        assertEquals("chunk-2", document.getText());
        assertNotNull(document.getImages());
        assertEquals(1, document.getImages().size());
        assertEquals("E:/tmp/skill/image-2.png", document.getImages().get(0).getPath());
    }
}
