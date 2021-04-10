#!/bin/bash
set -e 
DIR="$( cd "$(dirname "$0")" ; pwd -P )"
cd $DIR
TAG=${TAG:-1.13.2}
docker build --rm . -t pontusvisiongdpr/pontus-nifi-processors-salesforce-lib:${TAG}

docker push pontusvisiongdpr/pontus-nifi-processors-salesforce-lib:${TAG}

