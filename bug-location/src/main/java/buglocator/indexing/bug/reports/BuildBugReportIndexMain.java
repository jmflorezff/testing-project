package buglocator.indexing.bug.reports;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

/**
 * Builds an index of bug reports
 */
public class BuildBugReportIndexMain {
    private static final List<String> systems = Arrays.asList("bookkeeper", "derby", "lucene",
            "mahout", "openjpa", "pig", "solr", "tika", "zookeeper");

    public static void main(String[] args) throws IOException {
        BugReportIndexBuilder indexBuilder = new BugReportIndexBuilder();
        Path jsonsPath = Paths.get("..", "data", "processed-bug-reports");
        Files.walkFileTree(jsonsPath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                String fileName = file.getFileName().toString();
                String system = fileName.substring(0, fileName.indexOf(".json"));
                Path indexPath = Paths.get("..", "index", "bug-reports", system);
                FileUtils.forceMkdir(indexPath.toFile());
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
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
