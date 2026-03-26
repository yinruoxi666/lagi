package ai.llm.utils.convert;

import ai.common.exception.RRException;
import ai.llm.utils.LLMErrorConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QwenConvertTest {

    @Test
    void shouldDetectDataInspectionFailedAsSafetyBlock() {
        String message = "{\"code\":\"DataInspectionFailed\",\"message\":\"Input data may contain inappropriate content.\"}";

        RRException exception = QwenConvert.convertResponseException(LLMErrorConstants.INVALID_REQUEST_ERROR, message);

        assertEquals(LLMErrorConstants.CONTENT_SAFETY_BLOCKED, exception.getCode());
    }

    @Test
    void shouldDetectSafetySystemRejectMessage() {
        assertTrue(QwenConvert.isSafetyBlockedMessage("User request is rejected by safety system."));
    }
}
