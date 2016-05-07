package buglocator.indexing.source.code;

import buglocator.indexing.BaseIndexBuilder;
import buglocator.indexing.data.SourceFileText;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriterConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Lucene index for a corpus of source code file texts. Should not be reused.
 */
public class SourceCodeIndexBuilder extends BaseIndexBuilder<SourceFileText> {
    private StatCollectingSimilarity similarity = new StatCollectingSimilarity();
    private boolean used = false;
    private List<Integer> documentLengths = new ArrayList<>();

    public SourceCodeIndexBuilder() {
        super(SourceFileText.class);
    }

    @Override
    public void buildIndex(Path sourceFilePath, Path indexPath) throws IOException {
        if (used) {
            throw new IllegalStateException("An index was already built using this instance");
        }

        super.buildIndex(sourceFilePath, indexPath);
        used = true;

        float averageLength = documentLengths.stream()
                .reduce((i, j) -> i + j).get() / documentLengths.size();
        float stdDev = (float) Math.sqrt(documentLengths.stream()
                .map(len -> Math.pow(len - averageLength, 2))
                .reduce((x, y) -> x + y)
                .get() / documentLengths.size());

        int minus3Sigma = (int) (averageLength - (3 * stdDev));
        int plus3Sigma = (int) (averageLength + (3 * stdDev));

        // Write maximum and minimum to a file in the index directory
        FileUtils.write(indexPath.resolve("stats.txt").toFile(),
                minus3Sigma + "\n" + plus3Sigma);
    }

    @Override
    protected Document createDocument(SourceFileText item) {
        Document document = new Document();

        document.add(new StringField("path", item.getFilePath(), Field.Store.YES));
        document.add(new Field("text", item.getText(), termVectorsFieldType));

        int wordCount = countSpaces(item.getText()) + 1;
        documentLengths.add(wordCount);

        return document;
    }

    @Override
    protected IndexWriterConfig createIndexBuilderConfig() {
        IndexWriterConfig indexBuilderConfig = super.createIndexBuilderConfig();
        indexBuilderConfig.setSimilarity(similarity);

        return indexBuilderConfig;
    }

    private int countSpaces(String string) {
        int count = 0;
        for (char c : string.toCharArray()) {
            if (c == ' ') {
                count++;
            }
        }

        return count;
    }
}
