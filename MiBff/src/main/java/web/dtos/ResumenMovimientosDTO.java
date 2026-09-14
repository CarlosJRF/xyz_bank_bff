package web.dtos;

import java.math.BigDecimal;
import java.util.List;

public record ResumenMovimientosDTO(BigDecimal totalIngresos, BigDecimal totalEgresos, BigDecimal saldoNeto, int totalTransacciones) {}
