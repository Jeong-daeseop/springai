package com.krdevops.springai.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenerationReport {
    private final String rootPath;
    private final List<String> created  = new ArrayList<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public GenerationReport(String rootPath) { this.rootPath = rootPath; }

    public void added(FilePlan p)            { created.add(p.relativePath()); }
    public void failed(FilePlan p, String m) { errors.put(p.relativePath(), m); }
    public void warn(String msg)             { warnings.add(msg); }

    public String rootPath()            { return rootPath; }
    public List<String> created()       { return List.copyOf(created); }
    public Map<String,String> errors()  { return Map.copyOf(errors); }
    public List<String> warnings()      { return List.copyOf(warnings); }
    public boolean hasErrors()          { return !errors.isEmpty(); }
    public int totalFiles()             { return created.size(); }
}
