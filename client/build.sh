#!/bin/bash

./gradlew assembleRelease -PGIT_COMMIT_ID=$(git rev-parse --short HEAD)