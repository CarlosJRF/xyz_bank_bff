package services;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Service;
import clients.CuentasClient;
import clients.TransaccionesClient;
import dtos.CuentaBackendDTO;
import dtos.TransaccionBackendDTO;
import dtos.CuentaAgregadaDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgregadorService {

    private final CuentasClient cuentasClient;
    private final TransaccionesClient transaccionesClient;
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

        try {
            // Espera a que ambas llamadas terminen
            CompletableFuture.allOf(cuentaFuture, transaccionesFuture).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }

        return new CuentaAgregadaDTO(cuentaFuture.join(), transaccionesFuture.join());
    }
}