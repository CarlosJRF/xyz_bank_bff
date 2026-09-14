package config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BackendConfig {

    @Bean
    public ClientHttpRequestFactory backendRequestFactory(
            @Value("${backend.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${backend.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }

    @Bean
    @Qualifier("cuentasRestClient")
    public RestClient cuentasRestClient(@Value("${ms-cuentas.base-url}") String baseUrl,
                                        ClientHttpRequestFactory factory) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    @Qualifier("transaccionesRestClient")
    public RestClient transaccionesRestClient(@Value("${ms-transacciones.base-url}") String baseUrl,
                                              ClientHttpRequestFactory factory) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    public ExecutorService backendExecutor() {
        return Executors.newFixedThreadPool(10); // Pool para llamadas concurrentes
    }
}