#!/bin/bash
echo "Installing shared libraries to local Maven repository..."
cd shared-libs/rtrs-common && mvn clean install -q && cd ../..
cd shared-libs/rtrs-events && mvn clean install -q && cd ../..
cd shared-libs/rtrs-security && mvn clean install -q && cd ../..
echo "Done. You can now run any service."