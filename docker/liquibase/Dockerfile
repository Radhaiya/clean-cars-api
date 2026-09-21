# Liquibase 5 docker image no longer bundles JDBC drivers — add the MySQL
# connector (same version the app's Spring Boot BOM manages) to the image's
# classpath directory (/liquibase/lib).
FROM liquibase/liquibase:5.0.3
USER root
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar /liquibase/lib/mysql-connector-j-9.7.0.jar
