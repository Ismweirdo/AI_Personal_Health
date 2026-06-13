package com.health.ai.impl;

import com.health.ai.AIServiceAdapter;
import com.health.exception.AIServiceException;
import com.health.exception.AIServiceException.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractOpenAICompatibleAdapter implements AIServiceAdapter {

    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected AbstractOpenAICompatibleAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    protected abstract String getApiKey();
    protected abstract String getBaseUrl();
    protected abstract String getModel();
    protected abstract double getTemperature();
    protected abstract String getSystemPrompt();

    @Override
    public boolean isAvailable() {
        String key = getApiKey();
        return key != null && !key.isEmpty();
    }

    @Override
    public String generateResponse(String message, String context) throws AIServiceException {
        if (!isAvailable()) {
            throw new AIServiceException(ErrorCode.INVALID_API_KEY);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", getModel());
            requestBody.put("temperature", getTemperature());

            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", getSystemPrompt());
            messages.add(systemMessage);

            if (context != null && !context.isEmpty()) {
                try {
                    List<Map<String, Object>> ctxList = objectMapper.readValue(context, List.class);
                    for (Map<String, Object> ctxMsg : ctxList) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", (String) ctxMsg.get("role"));
                        msg.put("content", (String) ctxMsg.get("content"));
                        messages.add(msg);
                    }
                } catch (Exception e) {
                    log.warn("解析上下文失败: {}", e.getMessage());
                }
            }

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.add(userMessage);

            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getApiKey())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("{} 响应耗时: {}ms, 状态码: {}", getProvider().getName(),
                    System.currentTimeMillis() - start, response.statusCode());

            return parseResponse(response);

        } catch (AIServiceException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("请求超时: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.TIMEOUT, e);
        } catch (java.io.IOException e) {
            log.error("IO异常: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("请求异常: {}", e.getMessage(), e);
            throw new AIServiceException(ErrorCode.UNKNOWN, e);
        }
    }

    protected String parseResponse(HttpResponse<String> response) throws Exception {
        int statusCode = response.statusCode();

        if (statusCode == 200) {
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, String> message = (Map<String, String>) choice.get("message");
                if (message != null && message.containsKey("content")) {
                    return message.get("content").trim();
                }
            }
            throw new AIServiceException(ErrorCode.UNKNOWN, "AI返回空响应");
        } else if (statusCode == 401 || statusCode == 403) {
            throw new AIServiceException(ErrorCode.INVALID_API_KEY);
        } else if (statusCode == 429) {
            throw new AIServiceException(ErrorCode.RATE_LIMITED);
        } else if (statusCode >= 500) {
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE);
        } else {
            try {
                Map<String, Object> errorBody = objectMapper.readValue(response.body(), Map.class);
                Object err = errorBody.get("error");
                String errorMsg = err instanceof String ? (String) err :
                        (err instanceof Map ? String.valueOf(((Map) err).get("message")) : response.body());
                if (errorMsg != null && errorMsg.contains("content")) {
                    throw new AIServiceException(ErrorCode.CONTENT_FILTERED, errorMsg);
                }
            } catch (AIServiceException e) { throw e; } catch (Exception ignored) {}
            throw new AIServiceException(ErrorCode.UNKNOWN, "AI返回错误状态码: " + statusCode);
        }
    }

    @Override
    public void generateStreamResponse(String message, String context, Consumer<String> chunkCallback) throws AIServiceException {
        if (!isAvailable()) {
            throw new AIServiceException(ErrorCode.INVALID_API_KEY);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", getModel());
            requestBody.put("temperature", getTemperature());
            requestBody.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", getSystemPrompt());
            messages.add(systemMessage);

            if (context != null && !context.isEmpty()) {
                try {
                    List<Map<String, Object>> ctxList = objectMapper.readValue(context, List.class);
                    for (Map<String, Object> ctxMsg : ctxList) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", (String) ctxMsg.get("role"));
                        msg.put("content", (String) ctxMsg.get("content"));
                        messages.add(msg);
                    }
                } catch (Exception e) {
                    log.warn("解析上下文失败: {}", e.getMessage());
                }
            }

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.add(userMessage);

            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getApiKey())
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            log.info("{} 流式响应连接建立耗时: {}ms, 状态码: {}", getProvider().getName(),
                    System.currentTimeMillis() - start, response.statusCode());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                handleStreamError(response.statusCode(), errorBody);
                return;
            }

            parseStreamResponse(response.body(), chunkCallback);
            log.info("{} 流式响应完成，总耗时: {}ms", getProvider().getName(), System.currentTimeMillis() - start);

        } catch (AIServiceException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("请求超时: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.TIMEOUT, e);
        } catch (java.io.IOException e) {
            log.error("IO异常: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("请求异常: {}", e.getMessage(), e);
            throw new AIServiceException(ErrorCode.UNKNOWN, e);
        }
    }

    protected void parseStreamResponse(InputStream inputStream, Consumer<String> chunkCallback) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                
                // SSE格式: data: {...}
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6);
                    
                    // 检查是否是结束标记
                    if ("[DONE]".equals(jsonData)) {
                        break;
                    }
                    
                    try {
                        JsonNode root = objectMapper.readTree(jsonData);
                        JsonNode choices = root.get("choices");
                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText();
                                if (content != null && !content.isEmpty()) {
                                    chunkCallback.accept(content);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析流式响应失败: {}, 行内容: {}", e.getMessage(), line);
                    }
                }
            }
        }
    }

    protected void handleStreamError(int statusCode, String errorBody) throws AIServiceException {
        log.error("流式请求失败，状态码: {}, 错误信息: {}", statusCode, errorBody);
        
        if (statusCode == 401 || statusCode == 403) {
            throw new AIServiceException(ErrorCode.INVALID_API_KEY);
        } else if (statusCode == 429) {
            throw new AIServiceException(ErrorCode.RATE_LIMITED);
        } else if (statusCode >= 500) {
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE);
        } else {
            try {
                Map<String, Object> errorMap = objectMapper.readValue(errorBody, Map.class);
                Object err = errorMap.get("error");
                String errorMsg = err instanceof String ? (String) err :
                        (err instanceof Map ? String.valueOf(((Map) err).get("message")) : errorBody);
                if (errorMsg != null && errorMsg.contains("content")) {
                    throw new AIServiceException(ErrorCode.CONTENT_FILTERED, errorMsg);
                }
            } catch (AIServiceException e) { throw e; } catch (Exception ignored) {}
            throw new AIServiceException(ErrorCode.UNKNOWN, "AI返回错误状态码: " + statusCode);
        }
    }
}
