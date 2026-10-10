package cajeros.service;

import org.springframework.stereotype.Service;
import clients.CuentasClient;
import dtos.ClienteBackendDTO;
import dtos.CuentaBackendDTO;
import lombok.RequiredArgsConstructor;
import services.AgregadorService;
import cajeros.dtos.*;

@Service
@RequiredArgsConstructor
public class BffCajeroService {
    private final CuentasClient cuentasClient; // Comunicación directa, sin agregar transacciones
    private final AgregadorService agregador;

    public SaldoCajeroDTO consultarSaldo(Long cuentaId) {
        CuentaBackendDTO cuenta = cuentasClient.obtenerCuenta(cuentaId);
        ClienteBackendDTO cliente = agregador.buscarCliente(cuenta.nombre());
        String titular = cliente == null ? null : enmascarar(cliente.nombre());
        return new SaldoCajeroDTO(cuenta.cuentaId(), titular, cuenta.saldo());
    }

    // El cajero solo muestra el nombre y la inicial del apellido ("Charlie G."), nunca datos de contacto
    private String enmascarar(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        return partes[0] + " " + partes[partes.length - 1].charAt(0) + ".";
    }
}
