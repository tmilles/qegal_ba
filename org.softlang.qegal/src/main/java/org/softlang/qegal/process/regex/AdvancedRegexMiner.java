package org.softlang.qegal.process.regex;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.softlang.qegal.io.IOLayer;

public class AdvancedRegexMiner {

	private IOLayer iolayer;
	private List<String> pomPaths;

	private Pattern rgxProject = Pattern.compile("<project.*?>(?<projectBlock>.*)</project>", Pattern.DOTALL);
	private Pattern rgxRemoveMultiElements = Pattern.compile("<[^/>]+>([\\t\\n\\r\\s]+.+[\\t\\n\\r\\s]+)+</[^>]+>");
	private Pattern rgxBuild = Pattern.compile("<build>(?<buildBlock>.*)</build>", Pattern.DOTALL);
	private Pattern rgxParent = Pattern.compile("<parent>(?<parentBlock>.*)</parent>", Pattern.DOTALL);
	private Pattern rgxDependencies = Pattern.compile("<dependencies>(?<dependenciesBlock>.*?)</dependencies>",
			Pattern.DOTALL);
	private Pattern rgxDependency = Pattern.compile("<dependency>(?<dependencyBlock>.*?)</dependency>", Pattern.DOTALL);
	private Pattern rgxProperties = Pattern.compile("<properties>(?<properties>.*)</properties>", Pattern.DOTALL);
	private Pattern rgxProperty = Pattern.compile("<(?<name>[^>]+)>(?<value>[^<>]+)</[^>]+>");
	private Pattern rgxArtifactId = Pattern.compile("<artifactId>(?<artifact>.*)</artifactId>");
	private Pattern rgxGroupId = Pattern.compile("<groupId>(?<group>.*)</groupId>");
	private Pattern rgxVersion = Pattern.compile("<version>(?<version>.*)</version>");
	private Pattern rgxIsVariable = Pattern.compile("\\$\\{(.*)\\}");
	private Pattern rgxRelativePath = Pattern.compile("<relativePath>(?<path>.*)</relativePath>");

	public AdvancedRegexMiner(IOLayer iolayer) {
		this.iolayer = iolayer;
		pomPaths = new ArrayList<>();
	}

	public List<POM> mine() {
		String root = iolayer.root();
		// Get POM paths
		getPOMs(root);
		List<POM> tempPomObjects = new ArrayList<>();

		// Convert every path to a (temporary) object just in order to get it's content
		// and information
		for (String pomPath : pomPaths) {
			POM p = new POM();
			p.setLocation(pomPath);
			// Get content
			String content = readPOMContent(pomPath).replaceAll("(?s)<!--.*?-->", "");
			p.setContent(content);
			// Identify project block
			String projectBlock = getMatchResult(rgxProject.matcher(content), "projectBlock");
			// Remove parent block
			String parentBlock = getMatchResult(rgxParent.matcher(projectBlock), "parentBlock");
			List<String> dependenciesBlocks = getMatchResults(rgxDependencies.matcher(projectBlock),
					"dependenciesBlock");
			String buildBlock = getMatchResult(rgxBuild.matcher(projectBlock), "buildBlock");
			projectBlock = projectBlock.replace(parentBlock, "");
			projectBlock = projectBlock.replace(buildBlock, "");
			for (String dependenciesBlock : dependenciesBlocks) {
				projectBlock = projectBlock.replace(dependenciesBlock, "");
			}
			// Extract artifactId and groupId
			String artifactId = getMatchResult(rgxArtifactId.matcher(projectBlock), "artifact");
			String groupId = getMatchResult(rgxGroupId.matcher(projectBlock), "group");
			p.setArtifactId(artifactId);
			p.setGroupId(groupId);
			p.addProperty("project.artifactId", artifactId);
			p.addProperty("project.groupId", groupId);
			// Get properties
			String properties = getMatchResult(rgxProperties.matcher(content), "properties");
			Matcher matcherProperties = rgxProperty.matcher(properties);
			while (matcherProperties.find()) {
				p.addProperty(matcherProperties.group("name"), matcherProperties.group("value"));
			}
			String projectVersion = getMatchResult(rgxVersion.matcher(projectBlock), "version");
			if (!projectVersion.equals("")) {
				p.addProperty("project.version", projectVersion);
			}
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
			tempPomObjects.add(p);
		}

		// Let's identify parent relationships
		for (int i = 0; i < tempPomObjects.size(); i++) {
			String parentBlock = getMatchResult(rgxParent.matcher(tempPomObjects.get(i).getContent()), "parentBlock");
			String parentArtifactId = getMatchResult(rgxArtifactId.matcher(parentBlock), "artifact");
			String parentGroupId = getMatchResult(rgxGroupId.matcher(parentBlock), "group");
			String parentRelativePath = getMatchResult(rgxRelativePath.matcher(parentBlock), "path");
			if(!parentRelativePath.equals("")) {
				File a = new File(tempPomObjects.get(i).getLocation());
				File parentFolder = new File(a.getParent());
				File b = new File(parentFolder, parentRelativePath);
				try {
					String canonicalPath = b.getCanonicalPath();
					String[] parts = canonicalPath.split("repository:");
					String parentPath = "repository:" + parts[1];
					Optional<POM> optionalParent = tempPomObjects.stream().filter(p -> p.getLocation().equals(parentPath)).findFirst();
					if(optionalParent.isPresent()) {
						POM parent = optionalParent.get();
						parent.addChildren(tempPomObjects.get(i));
						tempPomObjects.get(i).setParent(parent);
						
					} 
					continue;
				} catch (Exception e) {
					continue;
				}
			}
			for (int j = 0; j < tempPomObjects.size(); j++) {
				if (j == i)
					continue;
				String artifactId2 = tempPomObjects.get(j).getArtifactId();
				String groupId2 = tempPomObjects.get(j).getGroupId();
				if (!"".equals(parentArtifactId) && parentArtifactId.equals(artifactId2) && (parentGroupId.equals("") || parentGroupId.equals(groupId2))) {
					// Found a relationship
					tempPomObjects.get(j).addChildren(tempPomObjects.get(i));
					tempPomObjects.get(i).setParent(tempPomObjects.get(j));
				}
			}
		}

		// Let's search for poms, that don't have any children
		List<POM> pomsToStartWith = new ArrayList<>();
		for (POM p : tempPomObjects) {
			if (p.getChildren().size() == 0) {
				pomsToStartWith.add(p);
			}
		}
		List<POM> pomsDone = new ArrayList<>();
		pomsDone = resolve(pomsToStartWith);
		while (pomsDone.size() < tempPomObjects.size()) {
			List<POM> next = new ArrayList<>();
			for (int i = 0; i < pomsDone.size(); i++) {
				if (pomsDone.get(i).getParent() != null && !pomsDone.contains(pomsDone.get(i).getParent())
						&& !next.contains(pomsDone.get(i).getParent())) {
					next.add(pomsDone.get(i).getParent());
				}
			}
			if (next.size() == 0)
				break;
			List<POM> newResolved = resolve(next);
			pomsDone.addAll(newResolved);
		}

		return pomsDone;

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

	private List<String> getMatchResults(Matcher m, String groupName) {
		List<String> results = new ArrayList<>();
		while (m.find()) {
			results.add(m.group(groupName));
		}
		return results;
	}

	private List<POM> resolve(List<POM> pomsToStartWith) {
		for (int i = 0; i < pomsToStartWith.size(); i++) {
//			System.out.println(pomsToStartWith.get(i).getLocation());
//			System.out.println(pomsToStartWith.get(i).getProperties().get("project.version"));
			for (int j = 0; j < pomsToStartWith.get(i).getDependencies().size(); j++) {
				if (rgxIsVariable.matcher(pomsToStartWith.get(i).getDependencies().get(j).getVersion()).matches()) {
					String variableToSearchFor = pomsToStartWith.get(i).getDependencies().get(j).getVersion()
							.replace("${", "").replace("}", "");
					boolean isResolved = false;
					POM parent = pomsToStartWith.get(i);
					while (parent != null && !isResolved) {
						if (parent.getProperties().get(variableToSearchFor) != null) {
							String resolvedVariable = parent.getProperties().get(variableToSearchFor);
							pomsToStartWith.get(i).getDependencies().get(j).setVersion(resolvedVariable);
							if (!resolvedVariable.contains("${")) {
								isResolved = true;
								break;
							} else {
								if(resolvedVariable.equals("${" + variableToSearchFor + "}")) {
									//Something is messed up here, abort
									break;
								}
								variableToSearchFor = resolvedVariable.replace("${", "").replace("}", "");
								continue;
							}
						}
						String thisArtifact = parent.getArtifactId();
						String thisGroup = parent.getGroupId();
						String parentArtifact = parent.getParent() != null ? parent.getParent().getArtifactId() : "";
						String parentGroup = parent.getParent() != null ? parent.getParent().getGroupId() : "";
						if (parentArtifact != null && parentGroup != null && parentArtifact.equals(thisArtifact)
								&& parentGroup.equals(thisGroup)) {
							isResolved = true;
						}
//						System.out.println("Current: " + parent.getLocation());
//						if(parent.getParent() != null) {
//							System.out.println("Parent: " + parent.getParent().getLocation());
//						}
						parent = parent.getParent();
					}
				}
			}
		}
		return pomsToStartWith;
	}
}
