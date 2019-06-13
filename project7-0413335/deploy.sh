#!/bin/bash

# build
# -e with full error message
mvn clean install -DskipTests

# uninstall
~/onos/tools/package/runtime/bin/onos-app localhost uninstall nctu.winlab.project7_0413335

# install
~/onos/tools/package/runtime/bin/onos-app localhost install! ./target/project7-0413335-1.0-SNAPSHOT.oar
