package fbw.assets;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

public class FileUtils {
    public static String loadFileAsString(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo: " + path, e);
        }
    }
}
