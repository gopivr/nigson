import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Container probe without a runtime shell or curl dependency. */
public class HealthProbe {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv("SERVER_PORT"));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().contains("\"status\":\"UP\"")) {
                System.exit(1);
            }
        }
    }
}
