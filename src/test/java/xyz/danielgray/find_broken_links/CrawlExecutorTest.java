package xyz.danielgray.find_broken_links;

import org.junit.Test;

import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CrawlExecutorTest {
    @Test
    public void urlRegexTests() {
        Matcher domainMatcher = CrawlExecutor.DOMAIN_EXTRACTOR.matcher("https://epfl.ch/blabla");
        Matcher procotolAndDomainMatcher = CrawlExecutor.PROTOCOL_AND_DOMAIN_EXTRACTOR.matcher("https://epfl.ch/blabla");
        assertTrue(domainMatcher.matches());
        assertTrue(procotolAndDomainMatcher .matches());
        assertEquals("epfl.ch", domainMatcher.group(1));
        assertEquals("https://epfl.ch", procotolAndDomainMatcher.group(1));
        domainMatcher = CrawlExecutor.DOMAIN_EXTRACTOR.matcher("https://golang.org/pkg/os/exec/");
        assertTrue(domainMatcher.matches());
        assertEquals("golang.org", domainMatcher.group(1));

        Matcher pathMatcher = CrawlExecutor.PATH_EXTRACTOR.matcher("https://golang.org/pkg/os/exec/");
        assertTrue(pathMatcher.matches());
        assertEquals("pkg/os/exec/", pathMatcher.group(1));

        Matcher protocolMatcher = CrawlExecutor.PROTOCOL_EXTRACTOR.matcher("https://golang.org/pkg/os/exec/");
        assertTrue(protocolMatcher.matches());
        assertEquals("https", protocolMatcher.group(1));
        protocolMatcher = CrawlExecutor.PROTOCOL_EXTRACTOR.matcher("https://golang.org/pkg/os/exec/");
        assertTrue(protocolMatcher.matches());
        assertEquals("https", protocolMatcher.group(1));
    }
}
