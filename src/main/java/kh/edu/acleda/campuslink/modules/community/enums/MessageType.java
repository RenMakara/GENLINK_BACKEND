package kh.edu.acleda.campuslink.modules.community.enums;

public enum MessageType {
    TEXT,
    IMAGE,
    VOICE,
    SYSTEM
    // TODO (chat phase 2): add VIDEO once the app needs video messages. Also update
    // ChatServiceImpl.validatePayload (treat VIDEO like IMAGE/VOICE — requires a mediaUrl).
}
