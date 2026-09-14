package movil.services;

import java.util.List;
import org.springframework.stereotype.Service;
import services.AgregadorService;
import dtos.CuentaAgregadaDTO;
import dtos.TransaccionBackendDTO;
import movil.dtos.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BffMovilService {
    private final AgregadorService agregador;

    public CuentaMovilDetalleDTO obtenerDetalleMovil(Long cuentaId) {
        CuentaAgregadaDTO agregado = agregador.agregarDetalle(cuentaId);

        List<TransaccionBackendDTO> txs = agregado.transacciones();

        List<MovimientoMovilDTO> ultimos = txs.stream()
            .limit(5)
            .map(t -> new MovimientoMovilDTO(t.fecha(), t.monto()))
            .toList();

        return new CuentaMovilDetalleDTO(agregado.cuenta().cuentaId(), agregado.cuenta().saldo(), ultimos);
    }
}