package data.extraction;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracts texts from the software systems present in the file system.
 */
public class TextExtractionMain {
    private static final Map<String, String> systemRoot = new HashMap<>();

    static {
        // Systems collected by us
        systemRoot.put("bookkeeper", "/home/juan/Source/bookkeeper-4.1.0/");
        systemRoot.put("derby", "/home/juan/Source/db-derby-10.9.1.0-src/");
        systemRoot.put("lucene", "/home/juan/Source/lucene-4.0.0/");
        systemRoot.put("mahout", "/home/juan/Source/mahout-distribution-0.8/");
        systemRoot.put("openjpa", "/home/juan/Source/openjpa-2.2.0/");
        systemRoot.put("pig", "/home/juan/Source/pig-release-0.11.1/");
        systemRoot.put("solr", "/home/juan/Source/lucene-solr-releases-lucene-solr-4.4.0/");
        systemRoot.put("tika", "/home/juan/Source/tika-1.3/");
        systemRoot.put("zookeeper", "/home/juan/Source/zookeeper-release-3.4.5/");

        // Systems used by the authors
        systemRoot.put("eclipse", "/home/juan/Source/eclipse-3.1/");
        systemRoot.put("swt", "/home/juan/Source/SWT-3.1/");
        systemRoot.put("aspectj", "/home/juan/Source/aspectj-1.5.3/");
    }

    public static void main(String[] args) {
        systemRoot.forEach((systemName, pathString) -> {
            Path startPath = Paths.get(pathString);
            File outputFile =
                    new File(".." + File.separator + "data" + File.separator + "raw-source" +
                            File.separator + systemName + ".json");
            Gson gson = new Gson();
            try {
                Files.walkFileTree(startPath, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir, BasicFileAttributes attrs) throws IOException {
//                         Ignore test directories
//                        if (dir.endsWith("src/test")) {
//                            System.out.println("Ignoring test directory: " + dir.toString());
//                            return FileVisitResult.SKIP_SUBTREE;
//                        } else {
//                            return FileVisitResult.CONTINUE;
//                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, BasicFileAttributes attrs) throws IOException {

                        // If this is not a .java file, ignore
                        if (!file.toString().endsWith(".java")) {
                            return FileVisitResult.CONTINUE;
                        }

                        // Load source file contents
                        String sourceString = FileUtils.readFileToString(file.toFile());

                        // Create a parser
                        ASTParser parser = ASTParser.newParser(AST.JLS8);
                        parser.setSource(sourceString.toCharArray());
                        parser.setKind(ASTParser.K_COMPILATION_UNIT);

                        // Parse file contents
                        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

                        // Extract textual information from AST
                        JavaSourceTextExtractor extractor =
                                new JavaSourceTextExtractor(sourceString);
                        compilationUnit.accept(extractor);

                        String sourceText = String.join(" ", extractor.getExtractedTexts());

                        Map<String, String> json = new HashMap<>();
                        json.put("file_path", file.toString().replace(pathString, ""));
                        json.put("text", sourceText);

                        FileUtils.write(outputFile, gson.toJson(json) + "\n", true);

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
