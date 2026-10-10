package main.java.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import main.java.entities.ClienteEntity;

public interface ClienteRepository extends JpaRepository<ClienteEntity, Long> {
    Optional<ClienteEntity> findByRut(String rut);

    Optional<ClienteEntity> findFirstByNombreIgnoreCase(String nombre);
}
