import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class ListFiles {
    public static void main(String[] args) {
        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java/org/graphify"))) {
            paths.filter(Files::isRegularFile)
                 .forEach(p -> System.out.println(p.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
