package web.controller;

import org.springframework.web.bind.annotation.*;
import web.dtos.CuentaWebDetalleDTO;
import web.services.BffWebService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/web/cuentas")
@RequiredArgsConstructor
public class CuentaWebController {
    private final BffWebService service;

    @GetMapping("/{cuentaId}")
    public CuentaWebDetalleDTO obtenerCuenta(@PathVariable Long cuentaId) {
        return service.obtenerDetalleWeb(cuentaId);
    }
}