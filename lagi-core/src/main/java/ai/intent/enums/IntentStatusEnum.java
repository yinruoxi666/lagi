package ai.intent.enums;

import lombok.Getter;


@Getter
public enum IntentStatusEnum {

    CONTINUE("continue"),
    COMPLETION("completion");

    private String name;

    IntentStatusEnum(String name) {
        this.name = name;
    }


}
