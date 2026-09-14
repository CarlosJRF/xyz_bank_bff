package main.java.loaders;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import cl.xyzbank.cuentas.entities.CuentaEntity;
import cl.xyzbank.cuentas.repositories.CuentaRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    private final CuentaRepository repository;

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) return;

        List cuentas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/intereses.csv").getInputStream()))) {
            
            reader.readLine(); // Saltar cabecera
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.isBlank()) continue;
                String[] campos = linea.split(",", -1);
                
                cuentas.add(new CuentaEntity(
                    Long.parseLong(campos[0].trim()),
                    campos[1].trim(),
                    campos[2].trim().isEmpty() ? BigDecimal.ZERO : new BigDecimal(campos[2].trim()),
                    campos[3].trim().isEmpty() ? null : Double.valueOf(campos[3].trim()).intValue(), // Parseo intermedio a Double por el formato del CSV
                    campos[4].trim()
                ));
            }
        }
        repository.saveAll(cuentas);
        System.out.println("Cuentas cargadas: " + cuentas.size());
    }
}
