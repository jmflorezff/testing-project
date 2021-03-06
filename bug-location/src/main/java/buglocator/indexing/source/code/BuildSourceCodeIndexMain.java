package buglocator.indexing.source.code;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Build an index for a set of preprocessed source code corpora.
 */
public class BuildSourceCodeIndexMain {
    public static void main(String[] args) throws IOException {
        Path jsonsPath = Paths.get("..", "data", "processed-source-code");
        Files.walkFileTree(jsonsPath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!file.toString().endsWith(".json")) {
                    // Only read JSON files
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString();
                String system = fileName.substring(0, fileName.indexOf(".json"));
                Path indexPath = Paths.get("..", "index", "source-code", system);
                FileUtils.forceMkdir(indexPath.toFile());
                SourceCodeIndexBuilder indexBuilder = new SourceCodeIndexBuilder();
                indexBuilder.buildIndex(file, indexPath);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                // Only traverse one directory
                return FileVisitResult.TERMINATE;
            }
        });
    }
}
