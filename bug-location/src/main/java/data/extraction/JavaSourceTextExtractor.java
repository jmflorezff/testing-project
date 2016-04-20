package data.extraction;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts textual information from .java source files.
 */
public class JavaSourceTextExtractor extends ASTVisitor {
    private final String sourceString;
    private List<String> extractedTexts = new ArrayList<>();

    public JavaSourceTextExtractor(String sourceString) {
        this.sourceString = sourceString;
    }

    public List<String> getExtractedTexts() {
        return extractedTexts;
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        // Don't visit import declarations
        return false;
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        // Don't visit package declarations
        return false;
    }

    @Override
    public boolean visit(CompilationUnit node) {
        node.getCommentList().stream().forEach(t -> ((ASTNode) t).accept(this));
        return super.visit(node);
    }

    @Override
    public boolean visit(LineComment node) {
        extractedTexts.add(extractCommentText(node).substring(2));
        return super.visit(node);
    }

    @Override
    public boolean visit(BlockComment node) {
        String commentText = extractCommentText(node);
        extractedTexts.add(commentText.substring(2, commentText.length() - 2).replace('\n', ' '));
        return super.visit(node);
    }

    private String extractCommentText(ASTNode commentNode) {
        int startPos = commentNode.getStartPosition();
        int endPos = startPos + commentNode.getLength();

        return sourceString.substring(startPos, endPos);
    }

    @Override
    public boolean visit(StringLiteral node) {
        extractedTexts.add(node.getLiteralValue());
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleName node) {
        extractedTexts.add(node.getIdentifier());
        return super.visit(node);
    }
}
