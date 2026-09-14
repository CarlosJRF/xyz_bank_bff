package clients;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import dtos.TransaccionBackendDTO;

@Component
public class TransaccionesClient {
    private final RestClient restClient;

    public TransaccionesClient(@Qualifier("transaccionesRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<TransaccionBackendDTO> listarPorCuenta(Long cuentaId) {
        return restClient.get()
                .uri("/transacciones/cuenta/{id}", cuentaId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}