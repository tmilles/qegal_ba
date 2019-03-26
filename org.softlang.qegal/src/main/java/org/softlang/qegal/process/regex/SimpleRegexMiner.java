package org.softlang.qegal.process.regex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.softlang.qegal.io.IOLayer;

public class SimpleRegexMiner {

	private IOLayer iolayer;
	private List<String> pomPaths;

	private Pattern rgxDependency = Pattern.compile("<dependency>(?<dependencyBlock>.*?)</dependency>", Pattern.DOTALL);
	private Pattern rgxArtifactId = Pattern.compile("<artifactId>(?<artifact>.*)</artifactId>");
	private Pattern rgxGroupId = Pattern.compile("<groupId>(?<group>.*)</groupId>");
	private Pattern rgxVersion = Pattern.compile("<version>(?<version>.*)</version>");
	
	public SimpleRegexMiner(IOLayer iolayer) {
		this.iolayer = iolayer;
		pomPaths = new ArrayList<>();
	}

	public List<POM> mine() {
		String root = iolayer.root();
		// Get POM paths
		getPOMs(root);
		List<POM> poms = new ArrayList<>();

		// Convert every path to a (temporary) object just in order to get it's content
		// and information
		for (String pomPath : pomPaths) {
			POM p = new POM();
			p.setLocation(pomPath);
			// Get content
			String content = readPOMContent(pomPath).replaceAll( "(?s)<!--.*?-->", "" );
			p.setContent(content);

			// Get dependencies
			Matcher matcherDependencies = rgxDependency.matcher(content);
			while (matcherDependencies.find()) {
				String dependencyBlock = matcherDependencies.group("dependencyBlock");
				Dependency d = new Dependency();
				String dependencyArtifactId = getMatchResult(rgxArtifactId.matcher(dependencyBlock), "artifact");
				String dependencyGroupId = getMatchResult(rgxGroupId.matcher(dependencyBlock), "group");
				String dependencyVersion = getMatchResult(rgxVersion.matcher(dependencyBlock), "version");
				d.setArtifactId(dependencyArtifactId);
				d.setGroupId(dependencyGroupId);
				d.setVersion(dependencyVersion);
				p.addDependency(d);
			}
			poms.add(p);
		}

		return poms;
	}

	private void getPOMs(String root) {
		List<String> children = iolayer.children(root);
		for (String child : children) {
			if (iolayer.isDirectory(child)) {
				getPOMs(child);
			} else {
				if (child.endsWith("pom.xml")) {
					pomPaths.add(child);
				}
			}
		}
	}

	private String readPOMContent(String path) {
		try {
			return IOUtils.toString(iolayer.access(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	private String getMatchResult(Matcher m, String groupName) {
		if (m.find()) {
			return m.group(groupName);
		}
		return "";
	}
}
