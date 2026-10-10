package main.java.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import main.java.repositories.ClienteRepository;
import main.java.dtos.ClienteDTO;
import main.java.dtos.ClienteRequestDTO;
import main.java.entities.ClienteEntity;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {
    private final ClienteRepository repository;

    @GetMapping
    public List<ClienteDTO> listarClientes() {
        return repository.findAll().stream()
            .map(this::toDTO)
            .toList();
    }

    @GetMapping("/{clienteId}")
    public ClienteDTO obtenerCliente(@PathVariable Long clienteId) {
        return repository.findById(clienteId)
            .map(this::toDTO)
            .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    @PostMapping
    public ResponseEntity<ClienteDTO> crearCliente(@RequestBody ClienteRequestDTO request) {
        ClienteEntity entity = new ClienteEntity(
            null,
            request.rut(),
            request.nombre(),
            request.email(),
            request.telefono(),
            request.direccion(),
            request.fechaNacimiento()
        );
        ClienteEntity guardado = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(guardado));
    }

    @PutMapping("/{clienteId}")
    public ClienteDTO actualizarCliente(@PathVariable Long clienteId, @RequestBody ClienteRequestDTO request) {
        ClienteEntity entity = repository.findById(clienteId)
            .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        entity.setRut(request.rut());
        entity.setNombre(request.nombre());
        entity.setEmail(request.email());
        entity.setTelefono(request.telefono());
        entity.setDireccion(request.direccion());
        entity.setFechaNacimiento(request.fechaNacimiento());

        return toDTO(repository.save(entity));
    }

    @DeleteMapping("/{clienteId}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long clienteId) {
        if (!repository.existsById(clienteId)) {
            throw new RuntimeException("Cliente no encontrado");
        }
        repository.deleteById(clienteId);
        return ResponseEntity.noContent().build();
    }

    private ClienteDTO toDTO(ClienteEntity entity) {
        return new ClienteDTO(
            entity.getClienteId(),
            entity.getRut(),
            entity.getNombre(),
            entity.getEmail(),
            entity.getTelefono(),
            entity.getDireccion(),
            entity.getFechaNacimiento()
        );
    }
}
