package com.health.ai.impl;

import com.health.ai.AIConfig;
import com.health.ai.AIProvider;
import com.health.ai.AIServiceAdapter;
import com.health.ai.HealthAssistantPrompts;
import com.health.exception.AIServiceException;
import com.health.exception.AIServiceException.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 百度千帆（文心一言）服务适配器
 * 根据千帆API文档实现：https://cloud.baidu.com/doc/WENXINWORKSHOP/s/jlil56u11
 * 认证方式：Bearer API Key（无需Secret Key）
 */
@Slf4j
@Component
public class WenxinAdapter implements AIServiceAdapter {
    
    private final AIConfig.WenxinConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // 千帆API端点
    private static final String QIANFAN_API_URL = "https://qianfan.baidubce.com/v2/chat/completions";
    
    public WenxinAdapter(AIConfig aiConfig) {
        this.config = aiConfig.getWenxin();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public AIProvider getProvider() {
        return AIProvider.BAIDU_WENXIN;
    }
    
    @Override
    public String generateResponse(String message, String context) throws AIServiceException {
        // 验证API Key配置
        if (!isAvailable()) {
            log.error("文心一言API Key未配置");
            throw new AIServiceException(ErrorCode.INVALID_API_KEY, "文心一言API Key未配置");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel() != null && !config.getModel().isEmpty() 
                    ? config.getModel() : "ernie-4.0-turbo-BK");
            requestBody.put("temperature", config.getTemperature() > 0 ? config.getTemperature() : 0.7);
            requestBody.put("stream", false);
            
            List<Map<String, String>> messages = new ArrayList<>();
            
            // 添加系统提示
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", HealthAssistantPrompts.SYSTEM_PROMPT);
            messages.add(systemMessage);
            
            // 添加上下文消息（如果有）
            if (context != null && !context.isEmpty()) {
                try {
                    List<Map<String, Object>> contextMessages = objectMapper.readValue(context, List.class);
                    for (Map<String, Object> ctxMsg : contextMessages) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", (String) ctxMsg.get("role"));
                        msg.put("content", (String) ctxMsg.get("content"));
                        messages.add(msg);
                    }
                    log.debug("成功解析上下文消息，共{}条", contextMessages.size());
                } catch (Exception e) {
                    log.warn("解析上下文失败，忽略上下文: {}", e.getMessage());
                }
            }
            
            // 添加用户消息
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.add(userMessage);
            
            requestBody.put("messages", messages);
            
            // 构建请求
            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("文心一言请求体: {}", requestJson);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(QIANFAN_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            
            // 发送请求
            log.info("开始调用文心一言API，消息长度: {} 字符", message.length());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis() - startTime;
            log.info("文心一言API调用完成，响应状态码: {}，耗时: {}ms", response.statusCode(), responseTime);
            
            // 处理响应
            String result = parseResponse(response);
            
            log.info("文心一言API调用成功，响应长度: {} 字符", result.length());
            return result;
            
        } catch (AIServiceException e) {
            log.error("文心一言服务异常: {}", e.getMessage());
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("文心一言请求超时，耗时: {}ms", responseTime);
            throw new AIServiceException(ErrorCode.TIMEOUT, e);
        } catch (java.io.IOException e) {
            log.error("文心一言请求IO异常: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("文心一言请求被中断: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("文心一言请求异常: {}", e.getMessage(), e);
            throw new AIServiceException(ErrorCode.UNKNOWN, e);
        }
    }
    
    /**
     * 解析千帆API响应
     */
    private String parseResponse(HttpResponse<String> response) throws Exception {
        int statusCode = response.statusCode();
        String responseBody = response.body();
        
        log.debug("文心一言响应状态码: {}, 响应体: {}", statusCode, responseBody);
        
        if (statusCode == 200) {
            Map<String, Object> responseJson = objectMapper.readValue(responseBody, Map.class);
            
            // 检查错误码
            if (responseJson.containsKey("error_code")) {
                int errorCode = (Integer) responseJson.get("error_code");
                String errorMsg = (String) responseJson.get("error_msg");
                String errorType = (String) responseJson.get("error_type");
                
                log.error("文心一言API返回错误: error_code={}, error_type={}, error_msg={}", 
                        errorCode, errorType, errorMsg);
                
                handleAPIError(errorCode, errorMsg);
            }
            
            // 检查choices数组
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseJson.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                
                if (message != null) {
                    String content = (String) message.get("content");
                    if (content != null && !content.isEmpty()) {
                        return content.trim();
                    }
                }
            }
            
            // 检查是否有result字段（兼容旧版API）
            if (responseJson.containsKey("result")) {
                String result = (String) responseJson.get("result");
                if (result != null && !result.isEmpty()) {
                    return result.trim();
                }
            }
            
            throw new AIServiceException(ErrorCode.UNKNOWN, "文心一言返回空响应");
            
        } else if (statusCode == 401) {
            log.error("文心一言API认证失败，状态码: {}", statusCode);
            throw new AIServiceException(ErrorCode.INVALID_API_KEY, "API Key无效或已过期");
            
        } else if (statusCode == 403) {
            log.error("文心一言API权限不足，状态码: {}", statusCode);
            throw new AIServiceException(ErrorCode.FORBIDDEN, "API调用权限不足");
            
        } else if (statusCode == 429) {
            log.error("文心一言API调用超限，状态码: {}", statusCode);
            throw new AIServiceException(ErrorCode.RATE_LIMITED, "API调用频率超限，请稍后重试");
            
        } else if (statusCode >= 500) {
            log.error("文心一言API服务错误，状态码: {}", statusCode);
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后重试");
            
        } else {
            log.error("文心一言API返回未知错误状态码: {}", statusCode);
            throw new AIServiceException(ErrorCode.UNKNOWN, "文心一言返回错误状态码: " + statusCode);
        }
    }
    
    /**
     * 处理API错误码
     */
    private void handleAPIError(int errorCode, String errorMsg) throws AIServiceException {
        switch (errorCode) {
            case 10001:
            case 10002:
            case 10003:
            case 10013:
                throw new AIServiceException(ErrorCode.INVALID_API_KEY, errorMsg);
                
            case 10004:
            case 10005:
                throw new AIServiceException(ErrorCode.FORBIDDEN, errorMsg);
                
            case 10006:
            case 10007:
                throw new AIServiceException(ErrorCode.RATE_LIMITED, errorMsg);
                
            case 20001:
            case 20002:
            case 20003:
                throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, errorMsg);
                
            case 282000:
            case 282001:
            case 282002:
                throw new AIServiceException(ErrorCode.CONTENT_FILTERED, errorMsg);
                
            case 30001:
            case 30002:
            case 30003:
                throw new AIServiceException(ErrorCode.BAD_REQUEST, errorMsg);
                
            default:
                throw new AIServiceException(ErrorCode.UNKNOWN, errorMsg);
        }
    }
    
    @Override
    public void generateStreamResponse(String message, String context, Consumer<String> chunkCallback) throws AIServiceException {
        if (!isAvailable()) {
            throw new AIServiceException(ErrorCode.INVALID_API_KEY, "文心一言API Key未配置");
        }

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel() != null && !config.getModel().isEmpty()
                    ? config.getModel() : "ernie-4.0-turbo-BK");
            requestBody.put("temperature", config.getTemperature() > 0 ? config.getTemperature() : 0.7);
            requestBody.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", HealthAssistantPrompts.SYSTEM_PROMPT);
            messages.add(systemMessage);

            if (context != null && !context.isEmpty()) {
                try {
                    List<Map<String, Object>> contextMessages = objectMapper.readValue(context, List.class);
                    for (Map<String, Object> ctxMsg : contextMessages) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", (String) ctxMsg.get("role"));
                        msg.put("content", (String) ctxMsg.get("content"));
                        messages.add(msg);
                    }
                } catch (Exception e) {
                    log.warn("解析上下文失败，忽略上下文: {}", e.getMessage());
                }
            }

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.add(userMessage);

            requestBody.put("messages", messages);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("文心一言流式请求体: {}", requestJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(QIANFAN_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(java.time.Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            log.info("开始调用文心一言流式API，消息长度: {} 字符", message.length());
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            long connectTime = System.currentTimeMillis() - startTime;
            log.info("文心一言流式连接建立耗时: {}ms, 状态码: {}", connectTime, response.statusCode());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                log.error("文心一言流式请求失败，状态码: {}, 错误信息: {}", response.statusCode(), errorBody);
                parseResponse(buildErrorResponse(response.statusCode(), errorBody));
                return;
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                StringBuilder fullContent = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;

                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6);

                        if ("[DONE]".equals(jsonData)) {
                            break;
                        }

                        try {
                            Map<String, Object> chunkData = objectMapper.readValue(jsonData, Map.class);
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunkData.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                if (delta != null && delta.containsKey("content")) {
                                    String content = (String) delta.get("content");
                                    if (content != null && !content.isEmpty()) {
                                        fullContent.append(content);
                                        chunkCallback.accept(content);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("解析文心一言流式数据块失败: {}, 行内容: {}", e.getMessage(),
                                    line.length() > 200 ? line.substring(0, 200) + "..." : line);
                        }
                    }
                }
                log.info("文心一言流式响应完成，总耗时: {}ms, 总内容长度: {} 字符",
                        System.currentTimeMillis() - startTime, fullContent.length());
            }

        } catch (AIServiceException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("文心一言流式请求超时: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.TIMEOUT, e);
        } catch (java.io.IOException e) {
            log.error("文心一言流式请求IO异常: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("文心一言流式请求被中断: {}", e.getMessage());
            throw new AIServiceException(ErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("文心一言流式请求异常: {}", e.getMessage(), e);
            throw new AIServiceException(ErrorCode.UNKNOWN, e);
        }
    }

    private HttpResponse<String> buildErrorResponse(int statusCode, String errorBody) {
        return new HttpResponse<String>() {
            @Override
            public int statusCode() { return statusCode; }
            @Override
            public String body() { return errorBody; }
            @Override
            public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override
            public java.net.http.HttpRequest request() { return null; }
            @Override
            public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
            @Override
            public java.net.URI uri() { return null; }
            @Override
            public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        };
    }

    @Override
    public boolean isAvailable() {
        boolean available = config.getApiKey() != null && !config.getApiKey().isEmpty();
        log.debug("文心一言服务可用性: {}", available);
        return available;
    }
    
    /**
     * 检查配置是否完整
     * 根据千帆API文档，只需要API Key即可
     */
    public boolean hasCompleteConfig() {
        return isAvailable();
    }
}
