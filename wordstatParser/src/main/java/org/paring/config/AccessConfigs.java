package org.paring.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
@Getter
public class AccessConfigs {

    private String authToken;
    private String yandexUri;
    private String dynamicsUri;
    public void init () {
        Properties prop = new Properties();

        try (InputStream input = AccessConfigs.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("Извините, файл не найден");
                return;
            }
            prop.load(input);

            authToken = prop.getProperty("yandex.auth.token");
            yandexUri = prop.getProperty("yandex.uri");
            dynamicsUri = prop.getProperty("yandex.uri.dynamics");

        } catch (IOException ex) {
            log.error("ошибка при чтении application.properties");
        }
    }
}
