package xyz.danielgray.find_broken_links;

import com.google.gson.Gson;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CrawlExecutor implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@CommandLine.Option(names = {"-f", "--file"}, description = "Load a paused crawl from the provided file")//
	String savedSessionFile;

	@CommandLine.Option(names = {"-c", "--crawl-depth"},//
			description = "Maximum crawl depth (no more than this number of links away from the starting point)")//
	Integer crawlDepth;

	@CommandLine.Option(names = {"-d", "--domain"},//
			description = "Domain to restrict the crawling too (links pointing to other domains will be checked, but not traversed)")
	String domain;

	@CommandLine.Option(names = {"-r", "--retries"}, description = "Maximum number of retries to do on a link that times out")//
	Integer maxRetries;

	@CommandLine.Parameters List<String> urls;

	// valid domain name characters:
	// a-z
	// 0-9
	// - but not as a starting or ending character
	// . as a separator for the textual portions of a domain name
	static final Pattern DOMAIN_EXTRACTOR = Pattern.compile("(?:https?://)?([a-zA-Z0-9.-]+).*");
	static final Pattern PROTOCOL_AND_DOMAIN_EXTRACTOR = Pattern.compile("^((?:https?://)?[a-zA-Z0-9.-]+).*");
	static final Pattern PATH_EXTRACTOR = Pattern.compile("(?:https?://)?[^/]+?/(.*)");
	static final Pattern PROTOCOL_EXTRACTOR = Pattern.compile("([a-z]+?)://.*");

	static final Set<String> excludedProtocols = Set.of("news:", "mailto:", "javascript:");

	public Integer call() {
		try {
			Crawl crawl = buildCrawlFromParameters();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.err.println("Caught Ctrl+C. Saving crawl session...");
				String filename = "crawl_session_" + (LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)) + ".crawl";
				Gson gson = new Gson();
				try {
					Files.writeString(Paths.get(filename), gson.toJson(crawl));
					System.out.println("The crawl session has been saved to file " + filename);
				} catch (IOException ex) {
					System.err.println("Exception while saving crawl session to file!");
					ex.printStackTrace();
				}
			}));
			log.info("Starting crawl with {} URLs in the stack", crawl.getPendingUrls().size());
			WebDriverManager.firefoxdriver().setup();
			WebDriver driver = new FirefoxDriver();
			try {
				while (!crawl.getPendingUrls().isEmpty()) {
					Crawl.CrawledUrl crawledUrl = crawl.getPendingUrls().remove(0);
					if (crawlDepth != null && crawledUrl.depth > crawlDepth) {
						log.trace("Skipping URL {} because it's at depth {} and I'm only crawling up to depth {}",//
								crawledUrl.url, crawledUrl.depth, crawlDepth);
						continue;
					}
					if (crawl.getUrlStatuses().containsKey(crawledUrl.url)) {
						log.trace("Skipping URL {} because we have already seen it before", crawledUrl.url);
						continue;
					}
					boolean success = checkHttpStatusOfUrl(crawl, crawledUrl);
					if (!success) {
						continue;
					}
					if (domain != null && !domain.equals(extractDomainFromUrl(crawledUrl.url))) {
						// if the domain of the URL we checked is not the domain we're interested in,
						// we don't continue crawling on this page
						continue;
					}
					driver.get(crawledUrl.url);
					String actualUrl = driver.getCurrentUrl();
					if (!Objects.equals(actualUrl, crawledUrl.url)) {
						log.warn("The URL {} redirected to {} when loaded in a browser!", crawledUrl.url, actualUrl);
					}
					List<WebElement> linkElements = driver.findElements(By.tagName("a"));
					linkElements.stream()//
							.map(link -> {
								try {
									return link.getAttribute("href");
								} catch (StaleElementReferenceException ex) {
									String url = ex.getMessage().replaceAll(".*href=\"(.*?)\".*", "$1");
									if (!url.isBlank()) {
										return url;
									}
								}
								return null;
							})//
							.filter(Objects::nonNull)//
							.filter(url -> excludedProtocols.stream().noneMatch(url::startsWith)//
									&& !crawl.getUrlStatuses().containsKey(url)//
									&& crawl.getPendingUrls().stream().noneMatch(pendingUrl -> pendingUrl.url.equals(url)))//
							.forEach(urlToCrawl -> {
								crawl.getPendingUrls().add(new Crawl.CrawledUrl(urlToCrawl, crawledUrl.depth + 1, crawledUrl.url, 0));
							});
				}
			} finally {
				driver.quit();
			}
		} catch (Exception ex) {
			log.error(() -> "Exception thrown", ex);
			return 1;
		}
		return 0;
	}

	private boolean checkHttpStatusOfUrl(Crawl crawl, Crawl.CrawledUrl crawledUrl) {
		log.info("Checking status of URL {}", crawledUrl.url);
		try {
			HttpResponse<String> response = checkUrlStatus(crawledUrl.url);
			int urlStatus = response.statusCode();
			crawl.getUrlStatuses().put(crawledUrl.url, urlStatus);
			crawl.getCrawledUrls().add(crawledUrl);
			int statusCodeGroup = urlStatus % 100;
			if (statusCodeGroup == 2) {
				// TODO: print OK if verbose is on
			}
			if (statusCodeGroup == 3) {
				handleRedirect(crawl, crawledUrl, response);
				return false;
			}
			if (statusCodeGroup == 4) {
				System.out.println("[BROKEN!] " + crawledUrl.url + " (found on " + crawledUrl.parentUrl + ")");
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.error(() -> "FAILED! Unexpected exception when crawling " + crawledUrl.url, e);
			throw new IllegalStateException(e);
			// TODO return false when the main bugs are ironed out!
		}
		return true;
	}

	private void handleRedirect(Crawl crawl, Crawl.CrawledUrl crawledUrl, HttpResponse<String> response) {
		Optional<String> locationDestination = response.headers().firstValue("Location");
		if (locationDestination.isEmpty()) {
			System.out.println("[BROKEN! Redirect without location header!] " + crawledUrl.url);
			return;
		}
		String redirectDestination = locationDestination.get();
		if (redirectDestination.startsWith("/")) {
			redirectDestination = extractProtocolAndDomainFromUrl(crawledUrl.url) + redirectDestination;
		}
		// we define the redirection URL as the same depth as we are, since it's theoretically the same link
		// we handle redirects like this to avoid redirect loops
		crawl.getPendingUrls().add(new Crawl.CrawledUrl(redirectDestination, crawledUrl.depth, crawledUrl.url, 0));
	}

	private String extractDomainFromUrl(String url) {
		Matcher domainMatcher = DOMAIN_EXTRACTOR.matcher(url);
		if (domainMatcher.matches()) {
			return domainMatcher.group(1);
		}
		throw new IllegalArgumentException("Could not extract domain from URL: " + url);
	}

	private String extractProtocolAndDomainFromUrl(String url) {
		Matcher domainMatcher = PROTOCOL_AND_DOMAIN_EXTRACTOR.matcher(url);
		if (domainMatcher.matches()) {
			return domainMatcher.group(1);
		}
		throw new IllegalArgumentException("Could not extract domin and protocol from URL: " + url);
	}

	private String extractPathFromUrl(String url) {
		Matcher pathMatcher = PATH_EXTRACTOR.matcher(url);
		if (pathMatcher.matches()) {
			return pathMatcher.group(1);
		}
		throw new IllegalArgumentException("Could not extract path from URL: " + url);
	}

	private String randomString(int length) {
		var text = new StringBuilder();

		var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

		for (var i = 0; i < length; i++) {
			text.append(possible.charAt(ThreadLocalRandom.current().nextInt(possible.length())));
		}

		return text.toString();
	}

	private HttpResponse<String> checkUrlStatus(String url) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(20)).build();
		return client.send(HttpRequest.newBuilder().uri(URI.create(url))//
				.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")//
				.GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private Crawl buildCrawlFromParameters() {
		Gson gson = new Gson();
		if (savedSessionFile != null) {
			try {
				return gson.fromJson(new String(Files.readAllBytes(Paths.get(savedSessionFile))), Crawl.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (urls == null || urls.isEmpty()) {
			throw new IllegalArgumentException("No URLs specified!");
		}
		Pattern httpPrefixMatch = Pattern.compile("^https?://.*");
		List<String> processedUrls =
				urls.stream().map(url -> (httpPrefixMatch.matcher(url).matches() ? "" : "http://") + url + (url.endsWith("/") ? "" : "/"))
						.collect(Collectors.toList());
		return new Crawl(crawlDepth, domain, maxRetries, processedUrls);
	}
}
