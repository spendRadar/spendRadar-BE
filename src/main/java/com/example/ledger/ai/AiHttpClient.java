package com.example.ledger.ai;

import com.example.ledger.advice.AdviceUnavailable;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AiHttpClient {
    private final AiSettings settings;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = new JsonMapper();

    public AiHttpClient(AiSettings settings) {
        this.settings = settings;
    }

    public JsonNode post(String url, String token, Object body, String provider) {
        var request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // 외부 응답 원문에는 키나 민감한 요청 정보가 있을 수 있어 사용자에게 전달하지 않습니다.
                throw new AdviceUnavailable(502, provider + "_UNAVAILABLE",
                    provider + " 호출에 실패했습니다. 서버 설정·인증·사용 한도를 확인해주세요.");
            }
            var result = json.readTree(response.body());
            if (result == null || !result.isObject()) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdviceUnavailable(502, provider + "_UNAVAILABLE", "모델 호출이 중단되었습니다.");
        } catch (IOException e) {
            throw new AdviceUnavailable(502, provider + "_UNAVAILABLE", "모델 서버 연결 또는 응답 시간이 초과되었습니다.");
        } catch (AdviceUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AdviceUnavailable(502, provider + "_INVALID_RESPONSE", "모델 응답 형식이 올바르지 않습니다.");
        }
    }
}
