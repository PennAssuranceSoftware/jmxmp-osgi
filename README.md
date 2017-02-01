# osgi-deployer
JMXMP Server For OSGi

## Releases
Until I get the full release automated you can create releases locally with the following command:
````
./gradlew release
````

## Test Connection
You can test the connection using the `visualvm` application. The following is an example of the command:
````
wget -P /tmp http://central.maven.org/maven2/org/glassfish/main/external/jmxremote_optional-repackaged/4.1.1/jmxremote_optional-repackaged-4.1.1.jar 
visualvm -cp:a /tmp/jmxremote_optional-repackaged-4.1.1.jar
````

## Notes
[JMXWS Connector](http://stackoverflow.com/questions/1339773/how-do-i-start-visualvm-with-the-jmxws-ws-connector-jsr-262)


