@echo off
echo Installing shared libraries...
cd shared-libs && mvn clean install && cd ..
echo Done.