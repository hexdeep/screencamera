#!/bin/bash

./gradlew clean

./gradlew assembleRelease -PGIT_COMMIT_ID=$(git rev-parse --short HEAD)