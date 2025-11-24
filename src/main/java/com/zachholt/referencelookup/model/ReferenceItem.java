package com.zachholt.referencelookup.model;

import java.util.List;

public class ReferenceItem {
    private String code;
    private String value;
    private String description;
    private String category;
    private List<String> tags;

    public ReferenceItem() {
    }

    public ReferenceItem(String code, String value, String description, String category, List<String> tags) {
        this.code = code;
        this.value = value;
        this.description = description;
        this.category = category;
        this.tags = tags;
    }
    
    // Backward compatibility constructor
    public ReferenceItem(String code, String description, String category, List<String> tags) {
        this(code, null, description, category, tags);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return code + (value != null ? " (" + value + ")" : "") + " - " + description;
    }
}