package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class FileNode {
    private String name;
    private String path;
    private String type; // "file" or "folder"
    private List<FileNode> children;

    public FileNode(String name, String path, String type) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.children = type.equals("folder") ? new ArrayList<>() : null;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<FileNode> getChildren() { return children; }
    public void setChildren(List<FileNode> children) { this.children = children; }

    public void addChild(FileNode child) {
        if (this.children != null) {
            this.children.add(child);
        }
    }
}
