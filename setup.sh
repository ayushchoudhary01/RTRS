#!/bin/bash
echo "Installing shared libraries to local Maven repository..."
cd shared-libs && mvn clean install && cd ..
echo "Done. You can now run any service."