package org.softlang.qegal.process.parts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.softlang.qegal.IMinedRepository;
import org.softlang.qegal.QegalLogging;
import org.softlang.qegal.QegalProcess2;
import org.softlang.qegal.io.IOFilesystem;
import org.softlang.qegal.io.IOLayer;
import org.softlang.qegal.process.regex.Dependency;

import com.google.gson.Gson;

public class RuleBasedProcess {

	private final static Node elementOf = NodeFactory.createURI("http://org.softlang.com/elementOf");
	private final static Node mavenDependency = NodeFactory.createURI("http://org.softlang.com/MavenDependency");
	private final static Node versionOf = NodeFactory.createURI("http://org.softlang.com/versionOf");
	private final static Node groupIdOf = NodeFactory.createURI("http://org.softlang.com/groupIdOf");
	private final static Node artifactIdOf = NodeFactory.createURI("http://org.softlang.com/artifactIdOf");
	private final static Node hasValue = NodeFactory.createURI("http://org.softlang.com/hasValue");

	public static void mine(IOLayer iolayer, String output) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		
		String modelOutput = "temp/output/rule/model";
		Map<String, String> properties = new HashMap<>();
		// Run mining.
		System.out.println(iolayer.root());
		IMinedRepository mined = QegalProcess2.execute(iolayer, new File(modelOutput),
				Collections.singleton(new File("src/main/java/org/softlang/qegal/modules/testmaven")), 1000 * 60 * 10,
				QegalLogging.ALL, true);

		properties.putAll(mined.properties());
		Graph g = mined.model().getGraph();
		ExtendedIterator<Triple> iter = g.find(Triple.create(Node.ANY, elementOf, mavenDependency));
		List<Dependency> deps = new ArrayList<>();
		while (iter.hasNext()) {
			// Get Metadata
			try {
				Node dependency = iter.next().getSubject();
				ExtendedIterator<Triple> artifactIter = g.find(Node.ANY, artifactIdOf, dependency);
				ExtendedIterator<Triple> groupIter = g.find(Node.ANY, groupIdOf, dependency);
				ExtendedIterator<Triple> versionIter = g.find(Node.ANY, versionOf, dependency);
				Node artifactIdNode = artifactIter.hasNext() ? artifactIter.next().getSubject() : null;
				Node groupIdNode = groupIter.hasNext() ? groupIter.next().getSubject() : null;
				Node versionNode = versionIter.hasNext() ? versionIter.next().getSubject() : null;
				List<String> versions = new ArrayList<String>();
				if (versionNode != null) {
					ExtendedIterator<Triple> versionValueIter = g.find(versionNode, hasValue, Node.ANY);
					List<Triple> versionList = versionValueIter.toList();
					versions = versionList.stream().map(t -> t.getObject().getLiteralValue().toString())
							.collect(Collectors.toList());
					versions.removeIf(v -> v.matches("\\$\\{.*\\}"));
				}
				ExtendedIterator<Triple> artifactValueIter = g.find(artifactIdNode, hasValue, Node.ANY);
				ExtendedIterator<Triple> groupValueIter = g.find(groupIdNode, hasValue, Node.ANY);
				String artifactId = artifactValueIter.hasNext()
						? artifactValueIter.next().getObject().getLiteralValue().toString()
						: null;
				String groupId = groupValueIter.hasNext()
						? groupValueIter.next().getObject().getLiteralValue().toString()
						: null;
				Dependency d = new Dependency();
				d.setArtifactId(artifactId);
				d.setGroupId(groupId);
				if (versions.size() > 0) {
					d.setVersion(versions.get(0));
					deps.add(d);
				}
			} catch (Exception e) {
				System.out.println("Error while extraction");
			}
		}
		//Filter duplicates
		List<Dependency> duplicates = new ArrayList<>();
		for(Dependency d1 : deps) {
			for(Dependency d2 : deps) {
				if(d1 != d2 && d1.getArtifactId().equals(d2.getArtifactId()) && d1.getGroupId().equals(d2.getGroupId())) {
					boolean alreadyInList = duplicates.stream().filter(d -> d.getArtifactId().equals(d1.getArtifactId()) && d.getGroupId().equals(d1.getGroupId())).toArray().length > 0;
					if(!alreadyInList) {
						duplicates.add(d1);
					}
				}
			}
		}
		//deps.removeAll(duplicates);
		Map<String, String> map = mined.properties();
		map.put("Dependencies", Integer.toString(deps.size()));
		Gson gson = new Gson();
		String json = gson.toJson(map);
		byte[] strToBytes = json.getBytes();
		FileOutputStream outputStream = new FileOutputStream("temp/output/" + output + "_properties.json");
		outputStream.write(strToBytes);
		outputStream.close();
		System.out.println(json);
		mined = null;
	}
}
