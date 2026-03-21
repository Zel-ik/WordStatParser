package org.paring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paring.config.AccessConfigs;
import org.paring.enums.Device;
import org.paring.enums.Period;
import org.paring.model.UniRequest;

import java.net.Authenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@AllArgsConstructor
@Slf4j
public class RequestService {

    private final AccessConfigs accessConfigs;
    public void sendRequestToWordStat() {
        try {
        UniRequest uniRequest = new UniRequest();
        uniRequest.setPhrase("кфу");
        uniRequest.getDevices().add(Device.ALL.getType());
        uniRequest.setPeriod(Period.MONTHLY.getName());
        uniRequest.setToDate("2023-07-31");
        uniRequest.setFromDate("2022-08-01");

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(uniRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https:/" + accessConfigs.getYandexUri() + accessConfigs.getDynamicsUri()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessConfigs.getAuthToken())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Ответ получен");
            System.out.println(response.body());
        } catch (JsonProcessingException e) {
            log.error("Ошибка при Парсинге");
        }catch (Exception e) {
            log.error("Ошибка при попытке сделать запрос: \n" + e.getMessage());
        }
    }
}
