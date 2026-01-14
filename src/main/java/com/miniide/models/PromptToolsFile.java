package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class PromptToolsFile {
    private int version = 1;
    private List<PromptTool> prompts = new ArrayList<>();

    public PromptToolsFile() {}

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<PromptTool> getPrompts() {
        return prompts;
    }

    public void setPrompts(List<PromptTool> prompts) {
        this.prompts = prompts;
    }
}
