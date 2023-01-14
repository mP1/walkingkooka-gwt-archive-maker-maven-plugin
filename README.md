# walkingkooka-gwt-archive-maker-maven-plugin
A maven plugin and command line tool that creates a maven jar intended for gwt from my other repos.

## Purpose

It is used to make a gwt compatible jar file from a J2CL compatible jar file. 

This involves the following operations:

- Remove any files matched by the patterns within `.walkingkooka-j2cl-maven-plugin-ignored-files.txt`. Files marked with `@GwtIncompatible` will be copied.
- Shade any packages matched by `.walkingkooka-j2cl-maven-plugin-shade.txt` into the `super-source` directory declared in the GWT module.xml
- Move any public files matched by the patterns within `.walkingkooka-j2cl-maven-plugin-public-files.txt` to the `<public>` declaration in the GWT module XML.
- Replace the original `POM.xml` with the provided replacement.`



## Maven Plugin

A maven-plugin is provided which takes the *.jar file for the project and produces the GWT version performing the steps mentioned above.



### Build default jar

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <id>create-walkingkooka-jar</id>
            <phase>prepare-package</phase>
            <goals>
                <goal>jar</goal>
            </goals>
            <configuration>
                <classifier>temp</classifier>
            </configuration>
        </execution>
    </executions>
</plugin>
```



### Build GWT jar

The plugin below assumes that `walkingkooka:walkingkooka` JAR file has been created by a previous task and writes a new GWT jar file.

```xml
<plugin>
    <groupId>walkingkooka</groupId>
    <artifactId>walkingkooka-gwt-archive-maker-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>make walkingkooka-gwt</id>
            <phase>integration-test</phase>
            <goals>
                <goal>build</goal>
            </goals>
            <configuration>
                <input>target/walkingkooka-1.0-SNAPSHOT-temp.jar</input>
                <output>target/walkingkooka-gwt-1.0-SNAPSHOT.jar</output>
                <pom-file>walkingkooka-gwt-pom.xml</pom-file>
            </configuration>
        </execution>
    </executions>
</plugin>
```



## Deployment

This task will deploy the new GWT xml to the distribution repo.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-deploy-plugin</artifactId>
    <version>3.0.0</version>
    <executions>
        <execution>
            <id>deploy walkingkooka:walkingkooka-gwt</id>
            <phase>deploy</phase>
            <goals>
                <goal>deploy-file</goal>
            </goals>
            <configuration>
                <groupId>walkingkooka</groupId>
                <artifactId>walkingkooka-gwt</artifactId>
                <version>1.0-SNAPSHOT</version>
                <packaging>jar</packaging>
                <file>target/walkingkooka-gwt-1.0-SNAPSHOT.jar</file>
                <url>https://maven-repo-254709.appspot.com</url>
                <repositoryId>github-mp1-appengine-repo</repositoryId>
            </configuration>
        </execution>
    </executions>
</plugin>
```



# Integration test

The following [POM](https://github.com/mP1/walkingkooka/blob/master/pom.xml) contains additional steps to execute a basic unit test which verifies the GWT JAR contents are available and transpile.