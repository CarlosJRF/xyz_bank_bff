package main.java.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import main.java.repositories.TransaccionRepository;
import main.java.dtos.TransaccionDTO;
import main.java.entities.TransaccionEntity;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transacciones")
@RequiredArgsConstructor
public class TransaccionController {
    
    private final TransaccionRepository repository;

    @GetMapping("/cuenta/{cuentaId}")
    public List listarPorCuenta(@PathVariable Long cuentaId) {
        return repository.findByCuentaId(cuentaId).stream()
            .map(this::toDTO)
            .toList();
    }

    private TransaccionDTO toDTO(TransaccionEntity entity) {
        return new TransaccionDTO(
            entity.getId(),
            entity.getCuentaId(),
            entity.getFecha(),
            entity.getTipoTransaccion(),
            entity.getMonto(),
            entity.getDescripcion()
        );
    }
}