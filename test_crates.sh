#!/bin/sh

./gradlew build
if [ $? != 0 ] ; then
	exit
fi
echo "Moving JAR file"
mv $(find . -name find-broken-links-all.jar) ./find-broken-links.jar
echo "Executing crawl"
java -jar find-broken-links.jar -d crates.io -r 1 https://crates.io/
