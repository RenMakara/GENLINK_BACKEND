package kh.edu.acleda.campuslink.modules.entertainment.dto.websocket;

import java.util.UUID;

public record SubmitAnswerEvent(
    UUID questionId,
    UUID optionId
) {}
