FROM maven:3-eclipse-temurin-11 AS MAVEN_TOOL_CHAIN
ADD . /tmp
WORKDIR /tmp/
RUN mvn package

FROM eclipse-temurin:11-jdk
COPY --from=MAVEN_TOOL_CHAIN /tmp/cli/target/*-executable.jar yang-to-swagger.jar
ENTRYPOINT ["java","-Dfile.encoding=utf-8", "-XX:+ExitOnOutOfMemoryError","-Xms256m","-Xmx256m", "-XshowSettings:vm","-jar","yang-to-swagger.jar"]