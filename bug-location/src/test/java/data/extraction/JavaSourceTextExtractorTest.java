package data.extraction;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for the java source text extractor.
 */
public class JavaSourceTextExtractorTest {
    @Test
    public void ignorePackageTest() {
        doTest("package do.not.return.this;class Me {}", Collections.singletonList("Me"));
    }

    @Test
    public void ignoreImportTest() {
        doTest("package a.pack; import do.not.return.this; //Some comment\ninterface Me{}",
                Arrays.asList("Some comment", "Me"));
    }

    @Test
    public void includeJavadocTest() {
        doTest("package a.pack; import do.not.return.this;\n" +
                        "/* Javadoc\n" +
                        " * Comment\n" +
                        " * @param me\n" +
                        "*/\n" +
                        "interface Interface{}",
                Arrays.asList(" Javadoc  * Comment  * @param me ", "Interface"));
    }

    @Test
    public void includeCommentsAfterTest() {
        doTest("package a.pack; import do.not.return.this;" +
                        "interface Interface{} //Some comment after\n//And another",
                Arrays.asList("Some comment after", "Interface", "And another"));
    }

    @Test
    public void includeFullyQualifiedNames() {
        doTest("package a; class A {int name1; void name2();" +
                        "String name3; fully.qualified.Name4 name5;}",
                Arrays.asList("A", "name1", "name2", "String", "name3",
                        "fully", "qualified", "Name4", "name5"));
    }

    @Test
    public void literalStringsTest() {
        doTest("package a; class A {" +
                        "public static final String myString = \"\\\"Hello there\\\"\";}",
                Arrays.asList("A", "String", "myString", "\"Hello there\""));
    }

    @Test
    public void multipleTypesTest() {
        doTest("package a; class A {//Some comment\n} //Some other comment\n" +
                "interface B {/*Comment some more*/}",
                Arrays.asList("A", "Some comment", "Some other comment", "B", "Comment some more"));
    }

    private void doTest(String sourceString, List<String> expectedAnswer) {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(sourceString.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        JavaSourceTextExtractor extractor = new JavaSourceTextExtractor(sourceString);
        compilationUnit.accept(extractor);
        List<String> answer = extractor.getExtractedTexts();

        assertEquals("Size of answer is different from expected",
                expectedAnswer.size(), answer.size());

        expectedAnswer.stream().forEach(t -> assertTrue("Extracted text not found in answer",
                answer.contains(t)));
    }
}
