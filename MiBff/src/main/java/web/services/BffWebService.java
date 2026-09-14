package web.services;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import services.AgregadorService;
import dtos.CuentaAgregadaDTO;
import dtos.TransaccionBackendDTO;
import web.dtos.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BffWebService {
    private final AgregadorService agregador;

    public CuentaWebDetalleDTO obtenerDetalleWeb(Long cuentaId) {
        CuentaAgregadaDTO agregado = agregador.agregarDetalle(cuentaId);

        List<TransaccionBackendDTO> txs = agregado.transacciones();

        List<MovimientoWebDTO> movimientos = txs.stream()
            .map(t -> new MovimientoWebDTO(t.fecha(), t.tipoTransaccion(), t.monto(), t.descripcion()))
            .toList();

        BigDecimal ingresos = movimientos.stream()
            .filter(m -> m.monto().compareTo(BigDecimal.ZERO) > 0)
            .map(MovimientoWebDTO::monto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal egresos = movimientos.stream()
            .filter(m -> m.monto().compareTo(BigDecimal.ZERO) < 0)
            .map(MovimientoWebDTO::monto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal neto = ingresos.add(egresos);
        ResumenMovimientosDTO resumen = new ResumenMovimientosDTO(ingresos, egresos, neto, movimientos.size());

        return new CuentaWebDetalleDTO(agregado.cuenta().cuentaId(), agregado.cuenta().nombre(), agregado.cuenta().saldo(), agregado.cuenta().edad(), agregado.cuenta().tipo(), movimientos, resumen);
    }
}