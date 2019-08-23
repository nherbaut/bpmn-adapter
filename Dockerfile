from java
COPY web/target/web-ui-1.0-SNAPSHOT.jar /root/
CMD ["java", "-jar", "/root/web-ui-1.0-SNAPSHOT.jar"]
