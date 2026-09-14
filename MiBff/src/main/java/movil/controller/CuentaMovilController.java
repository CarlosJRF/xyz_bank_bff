package movil.controller;

import org.springframework.web.bind.annotation.*;
import movil.dtos.CuentaMovilDetalleDTO;
import movil.services.BffMovilService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/movil/cuentas")
@RequiredArgsConstructor
public class CuentaMovilController {
    private final BffMovilService service;

    @GetMapping("/{cuentaId}")
    public CuentaMovilDetalleDTO obtenerCuenta(@PathVariable Long cuentaId) {
        return service.obtenerDetalleMovil(cuentaId);
    }
}
