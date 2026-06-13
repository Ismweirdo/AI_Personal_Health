package com.health.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "huawei.health")
public class HuaweiHealthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String authUrl = "https://oauth-login.cloud.huawei.com/oauth2/v3/authorize";
    private String tokenUrl = "https://oauth-login.cloud.huawei.com/oauth2/v3/token";
    private String scope = "https://www.huawei.com/healthkit/activity.read https://www.huawei.com/healthkit/heart.read";

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret) && StringUtils.hasText(redirectUri);
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
