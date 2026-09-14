package main.java.dtos;

import java.math.BigDecimal;

public record TransaccionDTO(
        Long id,
        Long cuentaId,
        String fecha,
        String tipoTransaccion,
        BigDecimal monto,
        String descripcion
) {}