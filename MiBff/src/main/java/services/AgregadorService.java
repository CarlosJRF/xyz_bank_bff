package services;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import clients.ClientesClient;
import clients.CuentasClient;
import clients.TransaccionesClient;
import dtos.ClienteBackendDTO;
import dtos.CuentaBackendDTO;
import dtos.TransaccionBackendDTO;
import dtos.CuentaAgregadaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgregadorService {

    private final CuentasClient cuentasClient;
    private final TransaccionesClient transaccionesClient;
    private final ClientesClient clientesClient;
    private final ExecutorService backendExecutor;

    public List<CuentaBackendDTO> listarCuentas() {
        return cuentasClient.listarCuentas();
    }

    public CuentaAgregadaDTO agregarDetalle(Long cuentaId) {
        // Llamadas en paralelo
        CompletableFuture<CuentaBackendDTO> cuentaFuture= CompletableFuture.supplyAsync(
                () -> cuentasClient.obtenerCuenta(cuentaId), backendExecutor);

        CompletableFuture<List<TransaccionBackendDTO>> transaccionesFuture = CompletableFuture.supplyAsync(
            () -> transaccionesClient.listarPorCuenta(cuentaId), backendExecutor);

        // El titular se resuelve por nombre, por lo que depende de la cuenta; corre en paralelo con las transacciones
        CompletableFuture<ClienteBackendDTO> clienteFuture = cuentaFuture.thenApplyAsync(
            cuenta -> buscarCliente(cuenta.nombre()), backendExecutor);

        try {
            // Espera a que todas las llamadas terminen
            CompletableFuture.allOf(cuentaFuture, transaccionesFuture, clienteFuture).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }

        return new CuentaAgregadaDTO(cuentaFuture.join(), transaccionesFuture.join(), clienteFuture.join());
    }

    // Degradación controlada: si ms-clientes falla o el titular no existe, el canal responde sin datos de cliente
    public ClienteBackendDTO buscarCliente(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        try {
            return clientesClient.buscarPorNombre(nombre);
        } catch (RestClientException ex) {
            log.warn("No se pudo obtener el cliente '{}': {}", nombre, ex.getMessage());
            return null;
        }
    }
}