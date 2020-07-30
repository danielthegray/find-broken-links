# Broken link finder

This is a simple bot to find broken links on a site,
allowing depth-limited crawls, as well as domain-limited crawls.

It works well even on sites that have dynamically (JS-generated) content,
since a browser is used to collect the links on the page.

The HTTP status checking itself is done with the built-in Java HTTP client to
speed some things up a bit. The Selenium page load is done only for getting links
on the page.

The result is a fat-JAR file, containing all that is needed to execute it (it 
even auto-downloads the Selenium web driver for Firefox).

You can simply run
```
./gradlew build
```
to build your own version of the JAR, which will output a
`find-broken-links-all.jar` inside the `build/libs/` folder. You can invoke this
from the command line with a simple
```
java -jar find-broken-links-all.jar
```

and all the other arguments. Calling it without arguments will show the help screen.

Interrupting the crawl will save a crawl file which allows the crawl to be resumed at a later date
using the `-f` CLI argument.
