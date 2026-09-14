package cajeros.service;

import org.springframework.stereotype.Service;
import clients.CuentasClient;
import dtos.CuentaBackendDTO;
import lombok.RequiredArgsConstructor;
import cajeros.dtos.*;

@Service
@RequiredArgsConstructor
public class BffCajeroService {
    private final CuentasClient cuentasClient; // Comunicación directa, sin agregar transacciones

    public SaldoCajeroDTO consultarSaldo(Long cuentaId) {
        CuentaBackendDTO cuenta = cuentasClient.obtenerCuenta(cuentaId);
        return new SaldoCajeroDTO(cuenta.cuentaId(), cuenta.saldo());
    }
}
