package com.lessonweb.lesson.model.docx;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public record BatchDocxExportRequest(
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid DocxExportRequest> documents
) {}
