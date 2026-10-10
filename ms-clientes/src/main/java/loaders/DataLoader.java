package main.java.loaders;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import main.java.entities.ClienteEntity;
import main.java.repositories.ClienteRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    private final ClienteRepository repository;

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) return;

        List<ClienteEntity> clientes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/clientes.csv").getInputStream()))) {

            reader.readLine(); // Saltar cabecera
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.isBlank()) continue;
                String[] campos = linea.split(",", -1);

                clientes.add(new ClienteEntity(
                    null,
                    campos[0].trim(),
                    campos[1].trim(),
                    campos[2].trim(),
                    campos[3].trim(),
                    campos[4].trim(),
                    campos[5].trim()
                ));
            }
        }
        repository.saveAll(clientes);
        System.out.println("Clientes cargados: " + clientes.size());
    }
}
