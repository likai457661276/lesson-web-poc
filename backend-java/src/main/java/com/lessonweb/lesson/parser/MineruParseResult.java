package com.lessonweb.lesson.parser;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;

public record MineruParseResult(JsonNode contentList, JsonNode ocrLayout, Path resultDir) {
}
