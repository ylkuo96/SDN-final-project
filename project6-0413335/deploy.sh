#!/bin/bash

# build
# -e with full error message
mvn clean install -DskipTests

# uninstall
~/onos/tools/package/runtime/bin/onos-app localhost uninstall nctu.winlab.project6_0413335

# install
~/onos/tools/package/runtime/bin/onos-app localhost install! ./target/project6-0413335-1.0-SNAPSHOT.oar
