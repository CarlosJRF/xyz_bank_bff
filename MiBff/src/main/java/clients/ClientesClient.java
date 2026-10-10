package clients;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import dtos.ClienteBackendDTO;

@Component
public class ClientesClient {
    private final RestClient restClient;

    public ClientesClient(@Qualifier("clientesRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ClienteBackendDTO buscarPorNombre(String nombre) {
        return restClient.get()
                .uri("/clientes/buscar?nombre={nombre}", nombre)
                .retrieve()
                .body(ClienteBackendDTO.class);
    }
}
