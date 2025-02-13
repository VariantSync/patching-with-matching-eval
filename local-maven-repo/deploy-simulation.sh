mvn install:install-file \
-DlocalRepositoryPath=../local-maven-repo \
-Dfile=../src/main/resources/lib/simulation-2.0.0-jar-with-dependencies.jar \
-DgroupId=org.XXXX-1.vevos \
-DartifactId=simulation \
-Dversion=2.0.0 \
-Dpackaging=jar \
-DgeneratePom=true
rm -rf ~/.m2/repository/org/variantsync/
