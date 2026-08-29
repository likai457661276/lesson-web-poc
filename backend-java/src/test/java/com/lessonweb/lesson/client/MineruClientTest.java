package com.lessonweb.lesson.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.config.MineruProperties;
import com.lessonweb.lesson.exception.MineruException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MineruClientTest {

    @TempDir
    Path tempDir;

    @Test
    void executesUploadPollAndDownloadProtocol() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MineruClient client = new MineruClient(builder.build(), new ObjectMapper(), properties("token"));
        Path file = tempDir.resolve("source.pdf");
        Files.write(file, "pdf".getBytes());

        server.expect(requestTo("https://mineru.test/api/v4/file-urls/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"batch_id":"batch-1","file_urls":["https://upload.test/file"]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://upload.test/file"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        server.expect(requestTo("https://mineru.test/api/v4/extract-results/batch/batch-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"extract_result":[
                          {"file_name":"other.pdf","state":"failed"},
                          {"file_name":"source.pdf","state":"done","full_zip_url":"https://download.test/result.zip"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://download.test/result.zip"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

        MineruClient.UploadTarget target = client.createUpload("source.pdf");
        client.uploadFile(target.uploadUrl(), file);
        String zipUrl = client.waitForResult(target.batchId(), "source.pdf");
        assertThat(client.downloadZip(zipUrl)).containsExactly(1, 2, 3);
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        MineruClient client = new MineruClient(RestClient.create(), new ObjectMapper(), properties(""));

        assertThatThrownBy(() -> client.createUpload("source.pdf"))
                .isInstanceOf(MineruException.class)
                .hasMessage("未配置 MINERU_API_KEY");
    }

    private MineruProperties properties(String token) {
        return new MineruProperties(token, "https://mineru.test/api/v4", "vlm", "ch",
                Duration.ofMillis(1), Duration.ofSeconds(1));
    }
}
