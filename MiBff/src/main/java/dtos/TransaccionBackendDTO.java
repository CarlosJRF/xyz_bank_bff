package dtos;

import java.math.BigDecimal;

public record TransaccionBackendDTO(
        Long id,
        Long cuentaId,
        String fecha,
        String tipoTransaccion,
        BigDecimal monto,
        String descripcion
) {}