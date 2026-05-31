package org.paring.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
@Getter
public class AccessConfigs {
    private static final String YANDEX_AUTH_TOKEN_ENV = "YANDEX_AUTH_TOKEN";

    private String authToken;
    private String yandexUri;
    private String dynamicsUri;

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void init () {
        Properties prop = new Properties();

        try (InputStream input = AccessConfigs.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("Извините, файл не найден");
                return;
            }
            prop.load(input);

            authToken = resolveAuthToken(prop);
            yandexUri = prop.getProperty("yandex.uri");
            dynamicsUri = prop.getProperty("yandex.uri.dynamics");

        } catch (IOException ex) {
            log.error("ошибка при чтении application.properties");
        }
    }

    private String resolveAuthToken(Properties properties) {
        String envToken = System.getenv(YANDEX_AUTH_TOKEN_ENV);
        if (envToken != null && !envToken.trim().isBlank()) {
            return envToken.trim();
        }

        String propertyToken = properties.getProperty("yandex.auth.token");
        return propertyToken == null ? "" : propertyToken.trim();
    }
}
