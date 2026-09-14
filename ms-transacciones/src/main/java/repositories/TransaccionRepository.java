package main.java.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import main.java.entities.TransaccionEntity;

public interface TransaccionRepository extends JpaRepository<TransaccionEntity, Long> {
    List<TransaccionEntity> findByCuentaId(Long cuentaId);
}