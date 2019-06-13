#!/bin/bash

curl -X POST -H "Content-type: application/json" http://localhost:8181/onos/v1/network/configuration -d @SimpleConfig.json --user onos:rocks
