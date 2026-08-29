package com.lessonweb.lesson.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.config.MineruProperties;
import com.lessonweb.lesson.exception.MineruException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class MineruClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MineruProperties properties;
    private final HttpClient uploadClient;

    public MineruClient(RestClient mineruRestClient, ObjectMapper objectMapper, MineruProperties properties) {
        this.restClient = mineruRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.uploadClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(properties.timeout())
                .build();
    }

    public UploadTarget createUpload(String filename) {
        requireApiKey();
        Map<String, Object> payload = Map.of(
                "files", List.of(Map.of("name", filename, "data_id", filename)),
                "model_version", properties.modelVersion(),
                "language", properties.language(),
                "enable_table", true,
                "enable_formula", true
        );
        HttpResult response = restClient.post()
                .uri(properties.baseUrl() + "/file-urls/batch")
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(jsonRequestBody(payload))
                .exchange((request, result) -> readResponse(result.getStatusCode().value(), result.getBody()));
        JsonNode json = apiJson(response, "申请上传地址失败");
        JsonNode data = json.path("data");
        String batchId = data.path("batch_id").asText("");
        JsonNode urls = data.path("file_urls");
        if (batchId.isBlank() || !urls.isArray() || urls.isEmpty()) {
            throw new MineruException("MinerU 未返回 batch_id 或上传地址");
        }
        return new UploadTarget(batchId, urls.get(0).asText());
    }

    public void uploadFile(String uploadUrl, Path file) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(properties.timeout())
                    // MinerU's pre-signed upload URL does not sign a Content-Type header.
                    .PUT(HttpRequest.BodyPublishers.ofFile(file))
                    .build();
            HttpResponse<Void> response = uploadClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 204) {
                throw new MineruException("文件上传失败（HTTP " + response.statusCode() + "）");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MineruException("文件上传被中断", exception);
        } catch (IOException exception) {
            throw new MineruException("文件上传失败", exception);
        }
    }

    public String waitForResult(String batchId, String filename) {
        long deadline = System.nanoTime() + properties.timeout().toNanos();
        while (System.nanoTime() < deadline) {
            HttpResult response = restClient.get()
                    .uri(properties.baseUrl() + "/extract-results/batch/" + batchId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .exchange((request, result) -> readResponse(result.getStatusCode().value(), result.getBody()));
            JsonNode json = apiJson(response, "查询解析状态失败");
            JsonNode rawResults = json.path("data").path("extract_result");
            JsonNode selected = selectResult(rawResults, filename);
            String state = selected.path("state").asText("");
            if ("done".equals(state)) {
                String zipUrl = selected.path("full_zip_url").asText("");
                if (zipUrl.isBlank()) {
                    throw new MineruException("解析完成但未返回结果压缩包");
                }
                return zipUrl;
            }
            if ("failed".equals(state)) {
                String message = selected.path("err_msg").asText("MinerU 解析失败");
                throw new MineruException(message.isBlank() ? "MinerU 解析失败" : message);
            }
            sleep(properties.pollInterval());
        }
        throw new MineruException("MinerU 解析超时");
    }

    public byte[] downloadZip(String zipUrl) {
        HttpResult response = restClient.get()
                .uri(zipUrl)
                .exchange((request, result) -> readResponse(result.getStatusCode().value(), result.getBody()));
        if (response.status() != 200) {
            throw new MineruException("结果下载失败（HTTP " + response.status() + "）");
        }
        return response.body();
    }

    private JsonNode selectResult(JsonNode rawResults, String filename) {
        if (rawResults.isArray()) {
            JsonNode first = rawResults.isEmpty() ? objectMapper.createObjectNode() : rawResults.get(0);
            for (JsonNode result : rawResults) {
                if (filename.equals(result.path("file_name").asText())) {
                    return result;
                }
            }
            return first;
        }
        return rawResults.isObject() ? rawResults : objectMapper.createObjectNode();
    }

    private JsonNode apiJson(HttpResult response, String context) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new MineruException(context + "：响应不是 JSON", exception);
        }
        if (response.status() >= 400 || payload.path("code").asInt(Integer.MIN_VALUE) != 0) {
            String message = payload.path("msg").asText("");
            if (message.isBlank()) {
                message = "HTTP " + response.status();
            }
            throw new MineruException(context + "：" + message);
        }
        return payload;
    }

    private byte[] jsonRequestBody(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new MineruException("MinerU 请求序列化失败", exception);
        }
    }

    private HttpResult readResponse(int status, java.io.InputStream body) throws IOException {
        return new HttpResult(status, body.readAllBytes());
    }

    private void requireApiKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new MineruException("未配置 MINERU_API_KEY");
        }
    }

    private String bearerToken() {
        requireApiKey();
        return "Bearer " + properties.apiKey();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MineruException("MinerU 解析轮询被中断", exception);
        }
    }

    public record UploadTarget(String batchId, String uploadUrl) {
    }

    private record HttpResult(int status, byte[] body) {
    }
}
