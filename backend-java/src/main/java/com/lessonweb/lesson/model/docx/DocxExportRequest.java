package com.lessonweb.lesson.model.docx;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DocxExportRequest {

    @NotNull
    @Size(min = 1, max = 25_000_000)
    private String html;

    @NotNull
    @Size(min = 1, max = 180)
    private String filename = "lesson.docx";

    public DocxExportRequest() {
    }

    public String html() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String filename() {
        return filename;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setFilename(String filename) {
        this.filename = filename;
    }
}
