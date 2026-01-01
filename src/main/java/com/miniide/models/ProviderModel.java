package com.miniide.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderModel {
    private String id;
    private String name;
    private boolean recommended;

    public ProviderModel() {}

    public ProviderModel(String id, String name, boolean recommended) {
        this.id = id;
        this.name = name;
        this.recommended = recommended;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }
}
