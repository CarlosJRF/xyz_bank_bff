package web.dtos;

import java.math.BigDecimal;
import java.util.List;

public record CuentaWebDetalleDTO(Long cuentaId, String nombre, BigDecimal saldo, Integer edad, String tipo, List movimientos, ResumenMovimientosDTO resumen) {}