package com.miniide.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PromptValidationResult {
    private final List<String> errors;

    private PromptValidationResult(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            this.errors = Collections.emptyList();
        } else {
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }
    }

    public static PromptValidationResult ok() {
        return new PromptValidationResult(Collections.emptyList());
    }

    public static PromptValidationResult of(List<String> errors) {
        return new PromptValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }
}
