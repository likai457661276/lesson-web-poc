package com.lessonweb.lesson.model.lesson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HeadingBlock.class, name = "heading"),
        @JsonSubTypes.Type(value = ParagraphBlock.class, name = "paragraph"),
        @JsonSubTypes.Type(value = ListBlock.class, name = "list"),
        @JsonSubTypes.Type(value = TableBlock.class, name = "table"),
        @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
        @JsonSubTypes.Type(value = FormulaBlock.class, name = "formula")
})
public sealed interface DocumentBlock permits HeadingBlock, ParagraphBlock, ListBlock,
        TableBlock, ImageBlock, FormulaBlock {

    String id();

    String type();
}
