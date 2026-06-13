@echo off
echo Installing shared libraries...
cd shared-libs\rtrs-common && mvn clean install && cd ..\..
cd shared-libs\rtrs-events && mvn clean install && cd ..\..
cd shared-libs\rtrs-security && mvn clean install && cd ..\..
echo Done.