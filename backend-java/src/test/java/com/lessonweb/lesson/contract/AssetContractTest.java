package com.lessonweb.lesson.contract;

import com.lessonweb.lesson.LessonApplication;
import com.lessonweb.lesson.storage.LocalStorage;
import com.lessonweb.lesson.service.LessonDocumentLibraryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LessonApplication.class)
@AutoConfigureMockMvc
class AssetContractTest extends MySqlContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalStorage storage;

    @MockBean
    private LessonDocumentLibraryService documents;

    @Test
    void unavailableAssetMatchesPythonErrorContract() throws Exception {
        mockMvc.perform(get("/api/assets/{jobId}/{filename}",
                        "00000000-0000-0000-0000-000000000000", "图片.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("资源不存在"));
    }

    @Test
    void servesChineseFilenameWithDetectedContentType() throws Exception {
        String jobId = UUID.randomUUID().toString();
        Path assetsDir = storage.jobDir(jobId).resolve("assets");
        Files.createDirectories(assetsDir);
        Files.write(assetsDir.resolve("图片.png"), new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/assets/{jobId}/{filename}", jobId, "图片.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
