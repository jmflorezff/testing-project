# testing-project
For testing class

CS6367 - Software Testing, Valitation, and Verification

OS: Multiplatform

Compiler: javac 1.8

IDE: Intelli J IDEA CE

Special instructions: 

1. Download Intelli J IDEA CE

2. New-> Project -> From existing souce -> Bug-location (Folder containing the souce code)

3. Import project from external model -> Next -> Next -> Next -> Select location for java SDK 1.8 ->Finish

4. Import needed libraries: File -> Project structure -> Libraries -> "+" (Add libraries) -> select (/bug-location/bin) -> Apply -> ok

5. Create the bug reports index:(/bug-location/src/java/buglocator/indexing/bug.reports/BuildBugReportIndexMain.java)-> Run 

6. Create souce code index:(/bug-location/src/java/buglocator/indexing/source.code/BuildSourceCodeIndexMain.java)-> Run

7. Run the IR system: (/bug-location/src/java/evaluation/EvaluationMain.java)

8. Result will be outputted to the console
