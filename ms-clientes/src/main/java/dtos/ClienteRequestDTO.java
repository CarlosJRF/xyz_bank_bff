package main.java.dtos;

public record ClienteRequestDTO(
        String rut,
        String nombre,
        String email,
        String telefono,
        String direccion,
        String fechaNacimiento
) {}
