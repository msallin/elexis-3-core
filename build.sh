#! /bin/bash
echo builddate=`date +%Y-%m-%d` >bundles/ch.elexis.core/builddate.properties
mvn -V clean verify -Dtycho.localArtifacts=ignore -Dmaven.test.skip=true -P all-archs
