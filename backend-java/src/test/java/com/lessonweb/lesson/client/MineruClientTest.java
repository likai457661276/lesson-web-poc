package com.lessonweb.lesson.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.config.MineruProperties;
import com.lessonweb.lesson.exception.MineruException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class MineruClientTest {

    @TempDir
    Path tempDir;

    @Test
    void executesCreatePollAndDownloadProtocol() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MineruClient client = new MineruClient(restTemplate, new ObjectMapper(), properties("token"));
        server.expect(requestTo("https://mineru.test/api/v4/file-urls/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {"files":[{"name":"source.pdf","data_id":"source.pdf"}],"model_version":"vlm","language":"ch","enable_table":true,"enable_formula":true}
                        """))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"batch_id":"batch-1","file_urls":["https://upload.test/file"]}}
                        """, MediaType.APPLICATION_JSON));
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
        String zipUrl = client.waitForResult(target.batchId(), "source.pdf");
        assertThat(client.downloadZip(zipUrl)).containsExactly(1, 2, 3);
        server.verify();
    }

    @Test
    void uploadsWithoutContentTypeForPresignedUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/upload", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            Path file = tempDir.resolve("source.pdf");
            Files.write(file, "pdf".getBytes());
            MineruClient client = new MineruClient(new RestTemplate(), new ObjectMapper(), properties("token"));

            client.uploadFile("http://127.0.0.1:" + server.getAddress().getPort() + "/upload", file);

            assertThat(contentType.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        MineruClient client = new MineruClient(new RestTemplate(), new ObjectMapper(), properties(""));

        assertThatThrownBy(() -> client.createUpload("source.pdf"))
                .isInstanceOf(MineruException.class)
                .hasMessage("未配置 MINERU_API_KEY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "poll", "download"})
    void classifiesTransportAndHttpFailuresAtProviderBoundary(String stage) {
        for (int failure = 0; failure < 3; failure++) {
            RestTemplate rest = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
            String url = switch (stage) {
                case "create" -> "https://mineru.test/api/v4/file-urls/batch";
                case "poll" -> "https://mineru.test/api/v4/extract-results/batch/batch-1";
                default -> "https://download.test/result.zip?signature=private-value";
            };
            server.expect(requestTo(url)).andRespond(switch (failure) {
                case 0 -> withException(new SSLHandshakeException("private-value"));
                case 1 -> withException(new SocketTimeoutException("private-value"));
                default -> withStatus(HttpStatus.SERVICE_UNAVAILABLE);
            });
            MineruClient client = new MineruClient(rest, new ObjectMapper(), properties("token"));
            assertThatThrownBy(() -> {
                switch (stage) {
                    case "create" -> client.createUpload("source.pdf");
                    case "poll" -> client.waitForResult("batch-1", "source.pdf");
                    default -> client.downloadZip(url);
                }
            }).isInstanceOfSatisfying(MineruException.class, error -> {
                assertThat(error.code()).isEqualTo("MINERU_PARSE_FAILED");
                assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(error.getMessage()).contains(switch (stage) {
                    case "create" -> "申请上传地址失败";
                    case "poll" -> "查询解析状态失败";
                    default -> "结果下载失败";
                }).doesNotContain("private-value", "signature");
                assertThat(error.getCause()).isNull();
            });
            server.verify();
        }
    }

    private MineruProperties properties(String token) {
        return new MineruProperties(token, "https://mineru.test/api/v4", "vlm", "ch",
                Duration.ofMillis(1), Duration.ofSeconds(1));
    }
}
