Requirements
=========

- Java 1.8
- maven


Usage
======

This creates the microservice server stubs from the BPMN2 choreography file.

```bash
mvn clean package
java -jar ./target/bpmn-adapter-1.0-SNAPSHOT-jar-with-dependencies.jar -i ./src/main/resources/choreography.xml -o output -w
```

- -i is the BPMN2 choreography diagram file, where all linked type should be available
- -o is the folder where the servers stub will be generated
- -w is a flag to tell the program to write openapi specs on disk



