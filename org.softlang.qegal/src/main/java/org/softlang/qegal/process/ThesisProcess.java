package org.softlang.qegal.process;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.maven.model.building.ModelBuildingException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.softlang.qegal.IMinedRepository;
import org.softlang.qegal.QegalLogging;
import org.softlang.qegal.QegalProcess2;
import org.softlang.qegal.io.IOFilesystem;
import org.softlang.qegal.io.IOGitBare;
import org.softlang.qegal.io.IOLayer;
import org.softlang.qegal.jutils.Gits;
import org.softlang.qegal.process.parts.AdvancedRegexProcess;
import org.softlang.qegal.process.parts.GoldStandardProcess;
import org.softlang.qegal.process.parts.RuleBasedProcess;
import org.eclipse.jgit.lib.Repository;
import org.softlang.qegal.process.parts.SimpleRegexProcess;
import org.softlang.qegal.process.regex.Dependency;

import com.google.gson.Gson;

public class ThesisProcess {

	private final static Node elementOf = NodeFactory.createURI("http://org.softlang.com/elementOf");
	private final static Node mavenDependency = NodeFactory.createURI("http://org.softlang.com/MavenDependency");
	private final static Node versionOf = NodeFactory.createURI("http://org.softlang.com/versionOf");
	private final static Node groupIdOf = NodeFactory.createURI("http://org.softlang.com/groupIdOf");
	private final static Node artifactIdOf = NodeFactory.createURI("http://org.softlang.com/artifactIdOf");
	private final static Node hasValue = NodeFactory.createURI("http://org.softlang.com/hasValue");
	
	//private final static String startWith = "Lawrence-zxc/summer";
	//Neu bis spring-projects/spring-bus
	//private final static String startWith = "tesla/m2e-core-tests";
	//keets2012/Spring-Boot-Samples mittwoch abend
	//wxm146case/running-location-system wiederholen
	//OpenNMS/opennms
	//jclouds/legacy-jclouds
	//apache/maven und juneau
	//private final static String startWith = "vladmihalcea/high-performance-java-persistence";
	//JamesonHuang/Graduation-Project
	private final static String startWith = "JamesonHuang/Graduation-Project";
	private final static String endWith = "KEINWERTHIER";

	public static void main(String[] args) throws MissingObjectException, IncorrectObjectTypeException, IOException {

		CSVParser records = CSVFormat.DEFAULT.withHeader().parse(new FileReader("data/maven_with_hashes.csv"));
		boolean startReached = false;
		for (CSVRecord record : records) {
			String repo = record.get("repo");
			if(repo.equals(startWith) || "".equals(startWith)) {
				startReached = true;
			}
			if(!startReached) {
				continue;
			}
			if(repo.equals(endWith)) {
				break;
			}
			String hash = record.get("hash");
			String name = repo.replace("/", "_");
			System.out.println("Doing " + repo);
			// Source downloaden und entpacken
			Process p = new ProcessBuilder("/bin/sh", "-c",
					"cd /home/torsten/temp_process/ && curl -o temp.zip https://codeload.github.com/" + repo + "/zip/" + hash + " && unzip temp.zip \"*/pom.xml\" -o -d input")
							.start();
			try {
				p.waitFor();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			IOLayer iolayer = new IOFilesystem(new File("/home/torsten/temp_process/input"));
//			Gits.collect(gitAddress, bare)
//			 Git git = Gits.collect(repo, true); 
//			 Repository repository = git.getRepository(); 
//			 IOLayer iolayer = new IOGitBare(repository, hash);
//			try {
//				System.out.println("Starting simple");
//				SimpleRegexProcess.mine(iolayer, "simple/" + name);
//			} catch (Exception e) {
//				System.out.println("Error while processing");
//			}
//			try {
//				System.out.println("Starting advanced");
//				AdvancedRegexProcess.mine(iolayer, "advanced/" + name);
//			} catch (Exception e) {
//				System.out.println("Error while processing: ");
//				e.printStackTrace();
//			}
			try {
				System.out.println("Starting rule-based");
				RuleBasedProcess.mine(iolayer, "rule/" + name);
			} catch(Throwable e) {
				System.out.println("Error while processing");
			}
//			try {
//				System.out.println("Starting goldstandard");
//				GoldStandardProcess.mine(iolayer, "gold/" + name);
//			} catch (Throwable e) {
//				System.out.println("Error while processing");
//			}
			System.out.println("Deleting");
			Process delete = new ProcessBuilder("/bin/sh", "-c", "cd /home/torsten/temp_process/ && rm -rf *").start();
			try {
				delete.waitFor();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
