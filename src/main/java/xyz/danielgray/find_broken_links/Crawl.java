package xyz.danielgray.find_broken_links;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Crawl {
    private final Integer depth;
    private final String domain;
    private final Integer maxRetries;

    private final Map<String, Integer> urlStatuses;
    private final LinkedList<CrawledUrl> pendingUrls;
    private final LinkedList<CrawledUrl> crawledUrls;

    public static class CrawledUrl {
        String url;
        int depth;
        String parentUrl;
        int retryAttempt;

        public CrawledUrl(String url, int depth, String parentUrl, int retryAttempt) {
            this.url = url;
            this.depth = depth;
            this.parentUrl = parentUrl;

            this.retryAttempt = retryAttempt;
        }
    }

    public Crawl(Integer depth, String domain, Integer maxRetries, List<String> pendingUrls) {
        this.depth = depth;
        this.domain = domain;
        this.maxRetries = maxRetries;
        this.pendingUrls = pendingUrls.stream().map(url -> new CrawledUrl(url, 0, null, 0)).collect(Collectors.toCollection(LinkedList::new));

        this.crawledUrls = new LinkedList<>();
        this.urlStatuses = new HashMap<>();
    }

    public Integer getDepth() {
        return depth;
    }

    public String getDomain() {
        return domain;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public List<CrawledUrl> getPendingUrls() {
        return pendingUrls;
    }

    public LinkedList<CrawledUrl> getCrawledUrls() {
        return crawledUrls;
    }

    public Map<String, Integer> getUrlStatuses() {
        return urlStatuses;
    }
}
