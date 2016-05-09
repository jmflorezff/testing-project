BugLocatorII
===


Requirements
---

Java Runtime Environment compatible with Java 8


Execution instructions
---

1. Open a command line interface and change working directory to the path where "BugLocatorII.zip" was extracted.
2. Make sure the folder "data" and the subfolders "data/processed-bug-reports" and "data/processed-source-code" are present.
3. Run the program using the following instruction:

    java -jar BugLocatorII.jar -f
    
    
Notes
---

The full analysis will take around 10 minutes. The short analysis omits the Eclipse system and is significantly faster (about 1 minute). To run a short analysis simply run the program without the -f option:

    java -jar BugLocatorII.jar

All the available options for the program can be viewed by using the -h option:

    java -jar BugLocatorII.jar -h
