package web.dtos;

import java.math.BigDecimal;
import java.util.List;

public record MovimientoWebDTO(String fecha, String tipo, BigDecimal monto, String descripcion) {}
