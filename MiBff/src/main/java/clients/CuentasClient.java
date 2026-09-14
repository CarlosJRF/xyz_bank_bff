package clients;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import dtos.CuentaBackendDTO;

@Component
public class CuentasClient {
    private final RestClient restClient;

    public CuentasClient(@Qualifier("cuentasRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List listarCuentas() {
        return restClient.get()
                .uri("/cuentas")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public CuentaBackendDTO obtenerCuenta(Long cuentaId) {
        return restClient.get()
                .uri("/cuentas/{id}", cuentaId)
                .retrieve()
                .body(CuentaBackendDTO.class);
    }
}